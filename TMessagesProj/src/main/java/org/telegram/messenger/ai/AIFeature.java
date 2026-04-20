package org.telegram.messenger.ai;

/**
 * Enumeration of every AI feature in the app.
 *
 * Each feature has:
 *  - a stable preference key (used in AIPrivacySettings)
 *  - a minimum tier required to use it
 *  - whether cloud inference is allowed by default
 *  - whether on-device inference is supported
 */
public enum AIFeature {

    // ─── Messaging ──────────────────────────────────────────────────────────
    UNREAD_SUMMARY(
            "ai_unread_summary",
            SubscriptionTier.FREE,
            /*cloudAllowed=*/ true,
            /*onDeviceSupported=*/ true
    ),
    PRIORITY_INBOX(
            "ai_priority_inbox",
            SubscriptionTier.FREE,
            /*cloudAllowed=*/ false,   // purely local scoring — never needs cloud
            /*onDeviceSupported=*/ true
    ),

    // ─── Chat Hygiene ────────────────────────────────────────────────────────
    INACTIVE_CHAT_SUGGESTIONS(
            "ai_inactive_suggestions",
            SubscriptionTier.FREE,
            /*cloudAllowed=*/ false,
            /*onDeviceSupported=*/ true
    ),
    MONTHLY_CLEANUP(
            "ai_monthly_cleanup",
            SubscriptionTier.FREE,
            /*cloudAllowed=*/ false,
            /*onDeviceSupported=*/ true
    ),

    // ─── AI Hub / Agent ──────────────────────────────────────────────────────
    AI_AGENT_CHAT(
            "ai_agent_chat",
            SubscriptionTier.FREE,
            /*cloudAllowed=*/ true,
            /*onDeviceSupported=*/ true
    ),
    AI_SMART_REPLY(
            "ai_smart_reply",
            SubscriptionTier.PREMIUM,
            /*cloudAllowed=*/ true,
            /*onDeviceSupported=*/ true
    ),

    // ─── Notes & Calendar ───────────────────────────────────────────────────
    AI_NOTES(
            "ai_notes",
            SubscriptionTier.FREE,
            /*cloudAllowed=*/ false,
            /*onDeviceSupported=*/ true
    ),
    AI_CALENDAR(
            "ai_calendar",
            SubscriptionTier.FREE,
            /*cloudAllowed=*/ false,
            /*onDeviceSupported=*/ true
    ),
    AI_CALENDAR_SMART_SCHEDULING(
            "ai_calendar_smart_scheduling",
            SubscriptionTier.PREMIUM,
            /*cloudAllowed=*/ true,
            /*onDeviceSupported=*/ true
    ),

    // ─── Premium-only cloud features ────────────────────────────────────────
    MESSAGE_TRANSLATION(
            "ai_message_translation",
            SubscriptionTier.PREMIUM,
            /*cloudAllowed=*/ true,
            /*onDeviceSupported=*/ false
    ),
    VOICE_TRANSCRIPTION(
            "ai_voice_transcription",
            SubscriptionTier.PREMIUM,
            /*cloudAllowed=*/ true,
            /*onDeviceSupported=*/ true
    ),
    IMAGE_UNDERSTANDING(
            "ai_image_understanding",
            SubscriptionTier.PREMIUM,
            /*cloudAllowed=*/ true,
            /*onDeviceSupported=*/ false
    );

    // ────────────────────────────────────────────────────────────────────────

    /** SharedPreferences / AIPrivacySettings key for this feature's on/off toggle. */
    public final String prefKey;

    /** Minimum subscription tier required to access this feature. */
    public final SubscriptionTier minimumTier;

    /**
     * Whether this feature is permitted to call cloud AI APIs.
     * Even when true, the user must have opted in via AIPrivacySettings.
     */
    public final boolean cloudAllowed;

    /** Whether on-device inference is a viable alternative for this feature. */
    public final boolean onDeviceSupported;

    AIFeature(String prefKey, SubscriptionTier minimumTier,
              boolean cloudAllowed, boolean onDeviceSupported) {
        this.prefKey = prefKey;
        this.minimumTier = minimumTier;
        this.cloudAllowed = cloudAllowed;
        this.onDeviceSupported = onDeviceSupported;
    }
}
