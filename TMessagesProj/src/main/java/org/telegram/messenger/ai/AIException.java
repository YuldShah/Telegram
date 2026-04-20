package org.telegram.messenger.ai;

/**
 * Structured exception type for AI subsystem errors.
 * Avoids leaking provider-specific exception types into callers.
 */
public class AIException extends Exception {

    public enum Code {
        /** The provider (model / API) is not set up yet. */
        NOT_AVAILABLE,
        /** The requested feature requires a higher subscription tier. */
        SUBSCRIPTION_REQUIRED,
        /** The user has not granted cloud-inference consent for this feature. */
        CLOUD_CONSENT_REQUIRED,
        /** The AI master switch or this feature's toggle is off. */
        FEATURE_DISABLED,
        /** Network request failed. */
        NETWORK_ERROR,
        /** The model or API returned an unexpected response. */
        INFERENCE_ERROR,
        /** Input exceeds the maximum token/character limit. */
        INPUT_TOO_LONG,
        /** Generic / unclassified error. */
        UNKNOWN
    }

    private final Code code;

    public AIException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public AIException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
