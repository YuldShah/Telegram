package org.telegram.messenger.ai;

import android.os.Handler;
import android.os.Looper;

import java.util.List;

/**
 * Central facade for all AI operations.
 *
 * Responsibilities:
 *  1. Routes requests to the correct {@link AIProvider} (on-device vs cloud).
 *  2. Enforces privacy and subscription gates before every operation.
 *  3. Provides a stable API that the rest of the app uses — provider
 *     implementations can change without affecting callers.
 *
 * Access pattern: AIManager.getInstance(accountId)
 * (mirrors Telegram's per-account controller pattern)
 */
public class AIManager {

    private static final int MAX_ACCOUNTS = 3;
    private static final AIManager[] instances = new AIManager[MAX_ACCOUNTS];

    private final int currentAccount;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final OnDeviceAIProvider onDeviceProvider;
    private final CloudAIProvider cloudProvider;
    private final AIPrivacySettings privacy;
    private final AISubscriptionManager subscription;

    // ── Singleton per account ────────────────────────────────────────────────

    public static AIManager getInstance(int account) {
        if (instances[account] == null) {
            synchronized (AIManager.class) {
                if (instances[account] == null) {
                    instances[account] = new AIManager(account);
                }
            }
        }
        return instances[account];
    }

    private AIManager(int account) {
        this.currentAccount = account;
        this.onDeviceProvider = new OnDeviceAIProvider();
        this.cloudProvider = new CloudAIProvider();
        this.privacy = AIPrivacySettings.getInstance();
        this.subscription = AISubscriptionManager.getInstance();
    }

    // ── Provider selection ───────────────────────────────────────────────────

    /**
     * Selects the best available provider for the given feature.
     *
     * Priority:
     *  1. On-device (if supported, ready, and not explicitly overridden by user).
     *  2. Cloud (only if cloud consent is given and subscription allows it).
     *
     * Returns {@code null} if no suitable provider is available.
     */
    private AIProvider resolveProvider(AIFeature feature, AICallback<?> callback) {
        // Master / feature kill-switch
        if (!privacy.isFeatureEnabled(feature)) {
            mainHandler.post(() -> callback.onError(
                    new AIException(AIException.Code.FEATURE_DISABLED,
                            "Feature " + feature.name() + " is disabled in AI settings.")));
            return null;
        }

        // Subscription gate
        if (!subscription.canAccessFeature(feature)) {
            mainHandler.post(() -> callback.onError(
                    new AIException(AIException.Code.SUBSCRIPTION_REQUIRED,
                            "This feature requires a Premium subscription.")));
            return null;
        }

        // Prefer on-device
        if (feature.onDeviceSupported && onDeviceProvider.isAvailable()) {
            return onDeviceProvider;
        }

        // Fall back to cloud if consent given
        if (feature.cloudAllowed && privacy.isCloudConsentGiven(feature)
                && cloudProvider.isAvailable()) {
            return cloudProvider;
        }

        // No provider available
        mainHandler.post(() -> callback.onError(
                new AIException(AIException.Code.NOT_AVAILABLE,
                        "No AI provider is ready. "
                                + (feature.onDeviceSupported
                                ? "The on-device model may still be downloading."
                                : "Please configure your cloud API key in AI settings."))));
        return null;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Summarizes a list of unread messages for a chat.
     *
     * The caller must provide raw message texts — no user IDs or other
     * identifying metadata should be included.
     *
     * @param messages  Plain-text message bodies, oldest first.
     * @param callback  Receives the summary string on the main thread.
     */
    public void summarizeUnreadMessages(List<String> messages, AICallback<String> callback) {
        AIProvider provider = resolveProvider(AIFeature.UNREAD_SUMMARY, callback);
        if (provider == null) return;
        provider.summarize(messages, callback);
    }

    /**
     * Generates a free-form completion for the AI Agent page.
     *
     * @param prompt   The user's input prompt.
     * @param callback Receives the completion on the main thread.
     */
    public void agentComplete(String prompt, AICallback<String> callback) {
        AIProvider provider = resolveProvider(AIFeature.AI_AGENT_CHAT, callback);
        if (provider == null) return;
        provider.complete(prompt, callback);
    }

    /**
     * Returns a list of dialog IDs that are candidates for leaving/deleting,
     * based on the locally tracked interaction history.
     *
     * Criteria: zero sent messages AND last received message older than
     * {@code thresholdDays} days.
     *
     * This method never calls any AI provider — it's purely local computation.
     *
     * @param thresholdDays Inactivity threshold in days (default: 90).
     * @param callback      Receives the sorted list on the main thread.
     */
    public void getInactiveChatSuggestions(int thresholdDays, AICallback<List<Long>> callback) {
        if (!privacy.isFeatureEnabled(AIFeature.INACTIVE_CHAT_SUGGESTIONS)) {
            mainHandler.post(() -> callback.onError(
                    new AIException(AIException.Code.FEATURE_DISABLED,
                            "Inactive chat suggestions are disabled.")));
            return;
        }

        // Run DB query off the main thread.
        new Thread(() -> {
            long thresholdMs = System.currentTimeMillis()
                    - (long) thresholdDays * 24 * 60 * 60 * 1_000L;
            List<Long> ids = AIDatabase.getInstance().getInactiveDialogIds(thresholdMs);
            mainHandler.post(() -> callback.onSuccess(ids));
        }, "ai-inactive-query").start();
    }

    /**
     * Records a sent message interaction for interaction tracking.
     * Called by the message-sending pipeline.
     *
     * @param dialogId Telegram dialog ID.
     */
    public void trackMessageSent(long dialogId) {
        if (!privacy.isFeatureEnabled(AIFeature.INACTIVE_CHAT_SUGGESTIONS)
                && !privacy.isFeatureEnabled(AIFeature.PRIORITY_INBOX)
                && !privacy.isFeatureEnabled(AIFeature.MONTHLY_CLEANUP)) {
            return; // No tracking needed if all dependent features are off.
        }
        AIDatabase.getInstance().recordMessageSent(dialogId);
    }

    /**
     * Records a received message interaction for interaction tracking.
     * Called by the message-receiving pipeline.
     *
     * @param dialogId Telegram dialog ID.
     */
    public void trackMessageReceived(long dialogId) {
        if (!privacy.isFeatureEnabled(AIFeature.INACTIVE_CHAT_SUGGESTIONS)
                && !privacy.isFeatureEnabled(AIFeature.PRIORITY_INBOX)
                && !privacy.isFeatureEnabled(AIFeature.MONTHLY_CLEANUP)) {
            return;
        }
        AIDatabase.getInstance().recordMessageReceived(dialogId);
    }

    /**
     * Computes a priority score for a dialog using local interaction stats.
     * Higher score = higher priority.
     *
     * Scoring is fully on-device. No AI inference required.
     *
     * @param dialogId Telegram dialog ID.
     * @return         Score in range [0, 100]. Returns 0 if no data tracked.
     */
    public int computePriorityScore(long dialogId) {
        AIDatabase.InteractionStats stats = AIDatabase.getInstance().getStats(dialogId);
        if (stats == null) return 0;

        // Simple heuristic — can be replaced with a lightweight ML model later.
        int sentWeight = Math.min(stats.sentCount * 3, 60);
        int recencyWeight = (int) Math.max(0, 30 - stats.daysSinceLastInteraction());
        int responseRatio = stats.totalCount() > 0
                ? (int) ((stats.sentCount / (float) stats.totalCount()) * 10)
                : 0;
        return Math.min(100, sentWeight + recencyWeight + responseRatio);
    }

    /**
     * Cancels any in-flight AI requests for this account.
     */
    public void cancelAll() {
        onDeviceProvider.cancel();
        cloudProvider.cancel();
    }

    /**
     * Wipes all AI data (privacy settings, interaction history, notes, calendar).
     * Should be called from the AI Privacy Settings "Reset all AI data" action.
     */
    public void clearAllData() {
        privacy.clearAllAIData();
        AIDatabase.getInstance().clearAllData();
    }
}
