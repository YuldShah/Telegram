package org.telegram.messenger.ai;

import java.util.List;

/**
 * Contract that every AI backend must implement.
 *
 * Implementations:
 *  - {@link OnDeviceAIProvider}  — runs a local model (Gemini Nano / MediaPipe LLM)
 *  - {@link CloudAIProvider}     — calls a remote API (user's cloud subscription)
 */
public interface AIProvider {

    /** Human-readable display name shown in settings (e.g. "On-Device (Gemini Nano)"). */
    String getDisplayName();

    /** Whether this provider is ready to handle requests (model downloaded, API key set, etc.). */
    boolean isAvailable();

    /**
     * Generates a plain-text completion for the given prompt.
     *
     * @param prompt   The full prompt string.
     * @param callback Receives the result or an error on the calling thread's Looper.
     */
    void complete(String prompt, AICallback<String> callback);

    /**
     * Summarises a list of raw message texts into a single paragraph.
     *
     * @param messages Ordered (oldest first) list of message strings.
     * @param callback Receives the summary or an error.
     */
    void summarize(List<String> messages, AICallback<String> callback);

    /**
     * Cancels any in-flight requests and releases resources.
     * Safe to call multiple times.
     */
    void cancel();
}
