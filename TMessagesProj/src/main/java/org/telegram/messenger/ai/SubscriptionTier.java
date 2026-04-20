package org.telegram.messenger.ai;

/**
 * Subscription tiers that gate AI feature access.
 *
 * FREE     — available to everyone, on-device only.
 * PREMIUM  — requires an active subscription; unlocks cloud inference
 *            and heavier features that need server-side resources.
 */
public enum SubscriptionTier {

    /** No subscription required. On-device AI only. */
    FREE(0),

    /** Paid subscription. Unlocks cloud AI and premium-only features. */
    PREMIUM(1);

    /** Numeric level — higher value means higher tier. */
    public final int level;

    SubscriptionTier(int level) {
        this.level = level;
    }

    /** Returns true if this tier satisfies the {@code required} tier. */
    public boolean satisfies(SubscriptionTier required) {
        return this.level >= required.level;
    }
}
