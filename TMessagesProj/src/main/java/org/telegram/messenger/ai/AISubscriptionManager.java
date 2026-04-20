package org.telegram.messenger.ai;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * Manages the user's subscription tier and gates feature access.
 *
 * Tier storage: stored locally in SharedPreferences, validated via the
 * subscription receipt verification flow (Google Play Billing / your backend).
 *
 * In production, the tier should be confirmed server-side with every app
 * launch and stored encrypted locally for offline access.
 *
 * TODO (production steps):
 *  1. Integrate Google Play Billing (already present as
 *       'com.android.billingclient:billing' in build.gradle).
 *  2. Create a lightweight verification backend that validates purchase tokens
 *     and returns the active tier as a signed JWT.
 *  3. Decode and cache the JWT here; re-verify on each app launch.
 */
public class AISubscriptionManager {

    private static final String PREFS_NAME = "ai_subscription";
    private static final String KEY_TIER = "subscription_tier";
    private static final String KEY_EXPIRY_MS = "subscription_expiry_ms";

    private static volatile AISubscriptionManager instance;

    private final SharedPreferences prefs;

    private AISubscriptionManager() {
        prefs = ApplicationLoader.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static AISubscriptionManager getInstance() {
        if (instance == null) {
            synchronized (AISubscriptionManager.class) {
                if (instance == null) {
                    instance = new AISubscriptionManager();
                }
            }
        }
        return instance;
    }

    // ── Tier access ──────────────────────────────────────────────────────────

    /** Returns the user's currently active subscription tier. */
    public SubscriptionTier getActiveTier() {
        if (isPremiumExpired()) {
            return SubscriptionTier.FREE;
        }
        String name = prefs.getString(KEY_TIER, SubscriptionTier.FREE.name());
        try {
            return SubscriptionTier.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return SubscriptionTier.FREE;
        }
    }

    /** Returns true if the user can access the given {@link AIFeature}. */
    public boolean canAccessFeature(AIFeature feature) {
        return getActiveTier().satisfies(feature.minimumTier);
    }

    // ── Tier mutation (called after purchase verification) ───────────────────

    /**
     * Grants PREMIUM tier until the given epoch-millisecond expiry.
     * Must only be called after server-side purchase verification succeeds.
     */
    public void grantPremium(long expiryEpochMs) {
        prefs.edit()
                .putString(KEY_TIER, SubscriptionTier.PREMIUM.name())
                .putLong(KEY_EXPIRY_MS, expiryEpochMs)
                .apply();
    }

    /** Revokes premium and downgrades to FREE immediately. */
    public void revokePremium() {
        prefs.edit()
                .putString(KEY_TIER, SubscriptionTier.FREE.name())
                .remove(KEY_EXPIRY_MS)
                .apply();
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private boolean isPremiumExpired() {
        long expiry = prefs.getLong(KEY_EXPIRY_MS, 0L);
        return expiry > 0 && System.currentTimeMillis() > expiry;
    }
}
