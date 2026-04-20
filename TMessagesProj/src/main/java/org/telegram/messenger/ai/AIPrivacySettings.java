package org.telegram.messenger.ai;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * Per-feature privacy settings.
 *
 * Privacy contract:
 *  - Every AI feature starts disabled by default.
 *  - Cloud inference is opt-in per feature and is blocked unless the user
 *    has explicitly consented AND the feature's {@link AIFeature#cloudAllowed} flag is true.
 *  - Interaction tracking (used by PRIORITY_INBOX, INACTIVE_CHAT_SUGGESTIONS,
 *    MONTHLY_CLEANUP) is stored 100% on-device and never transmitted.
 *  - The user can wipe all locally stored AI data via {@link #clearAllAIData()}.
 */
public class AIPrivacySettings {

    private static final String PREFS_NAME = "ai_privacy_settings";

    /**
     * Shared preference key suffix appended to {@link AIFeature#prefKey}
     * to record whether the feature is enabled.
     */
    private static final String SUFFIX_ENABLED = "_enabled";

    /**
     * Shared preference key suffix indicating the user has consented to
     * cloud inference for a specific feature.
     */
    private static final String SUFFIX_CLOUD_CONSENT = "_cloud_consent";

    /** Master kill-switch: when false, all AI features are disabled. */
    private static final String KEY_AI_MASTER_SWITCH = "ai_master_enabled";

    /** Whether the user has ever seen the AI on-boarding/privacy disclosure. */
    private static final String KEY_ONBOARDING_SHOWN = "ai_onboarding_shown";

    // ────────────────────────────────────────────────────────────────────────

    private static volatile AIPrivacySettings instance;

    private final SharedPreferences prefs;

    private AIPrivacySettings() {
        prefs = ApplicationLoader.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static AIPrivacySettings getInstance() {
        if (instance == null) {
            synchronized (AIPrivacySettings.class) {
                if (instance == null) {
                    instance = new AIPrivacySettings();
                }
            }
        }
        return instance;
    }

    // ── Master switch ────────────────────────────────────────────────────────

    public boolean isAIEnabled() {
        return prefs.getBoolean(KEY_AI_MASTER_SWITCH, false);
    }

    public void setAIEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AI_MASTER_SWITCH, enabled).apply();
    }

    // ── Per-feature toggle ───────────────────────────────────────────────────

    /**
     * Returns true if the feature is enabled.
     * Always returns false when the master switch is off.
     */
    public boolean isFeatureEnabled(AIFeature feature) {
        if (!isAIEnabled()) return false;
        return prefs.getBoolean(feature.prefKey + SUFFIX_ENABLED, false);
    }

    public void setFeatureEnabled(AIFeature feature, boolean enabled) {
        prefs.edit().putBoolean(feature.prefKey + SUFFIX_ENABLED, enabled).apply();
    }

    // ── Cloud consent ────────────────────────────────────────────────────────

    /**
     * Returns true only when:
     *  1. The feature's {@code cloudAllowed} flag is true.
     *  2. The user has explicitly given cloud consent for this feature.
     *  3. The master switch and feature toggle are both on.
     *
     * Use this before sending any data to an external API.
     */
    public boolean isCloudConsentGiven(AIFeature feature) {
        if (!feature.cloudAllowed) return false;
        if (!isFeatureEnabled(feature)) return false;
        return prefs.getBoolean(feature.prefKey + SUFFIX_CLOUD_CONSENT, false);
    }

    public void setCloudConsent(AIFeature feature, boolean consent) {
        if (!feature.cloudAllowed) return; // silently ignore — this feature never uses cloud
        prefs.edit().putBoolean(feature.prefKey + SUFFIX_CLOUD_CONSENT, consent).apply();
    }

    // ── On-boarding ──────────────────────────────────────────────────────────

    public boolean isOnboardingShown() {
        return prefs.getBoolean(KEY_ONBOARDING_SHOWN, false);
    }

    public void setOnboardingShown(boolean shown) {
        prefs.edit().putBoolean(KEY_ONBOARDING_SHOWN, shown).apply();
    }

    // ── Data wipe ────────────────────────────────────────────────────────────

    /**
     * Clears all AI-related SharedPreferences.
     * The caller should also invoke {@link AIDatabase#clearAllData()} to wipe
     * the local interaction history.
     */
    public void clearAllAIData() {
        prefs.edit().clear().apply();
    }
}
