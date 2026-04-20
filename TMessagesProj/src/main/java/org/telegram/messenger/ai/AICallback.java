package org.telegram.messenger.ai;

/**
 * Generic async callback for AI operations.
 *
 * Implementations are always invoked on the main thread.
 */
public interface AICallback<T> {
    void onSuccess(T result);
    void onError(AIException error);
}
