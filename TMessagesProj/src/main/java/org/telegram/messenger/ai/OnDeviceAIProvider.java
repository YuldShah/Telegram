package org.telegram.messenger.ai;

import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * On-device AI provider using MediaPipe LLM Inference API.
 *
 * Model support (runtime-selected based on device RAM / GPU):
 *  - Gemma 3 1B   — low-end devices (≥2 GB RAM)
 *  - Gemma 3 4B   — mid-range devices (≥4 GB RAM)
 *  - Gemma 3 12B  — high-end devices (≥8 GB RAM) — optional download
 *
 * All inference runs on the device; no data is ever transmitted when
 * this provider is in use.
 *
 * TODO (implementation steps):
 *  1. Add MediaPipe dependency to TMessagesProj/build.gradle:
 *       implementation 'com.google.mediapipe:tasks-genai:0.10.18'
 *  2. Download the model file at first run via {@link OnDeviceModelManager}.
 *  3. Instantiate {@code LlmInference} with the model path and GPU delegate.
 *  4. Replace the stub bodies below with real MediaPipe calls.
 */
public class OnDeviceAIProvider implements AIProvider {

    private static final String TAG = "OnDeviceAIProvider";

    /** Max characters fed into the model in a single request. */
    private static final int MAX_INPUT_CHARS = 8_000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService inferenceExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ai-on-device-inference");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    /** True once the model file is loaded and the inference engine is ready. */
    private volatile boolean modelReady = false;

    // TODO: replace Object with the real MediaPipe LlmInference type once the
    //       dependency is added.
    private volatile Object llmInference = null;

    public OnDeviceAIProvider() {
        // Model loading is deferred — call prepareModel() during AI Hub init
        // so startup is not blocked.
    }

    /**
     * Asynchronously loads the appropriate model from local storage.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    public void prepareModel(AICallback<Void> callback) {
        if (modelReady) {
            mainHandler.post(() -> callback.onSuccess(null));
            return;
        }
        inferenceExecutor.execute(() -> {
            try {
                // TODO: Replace with real model loading logic:
                //   String modelPath = OnDeviceModelManager.getInstance().getModelPath();
                //   LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                //       .setModelPath(modelPath)
                //       .setMaxTokens(1024)
                //       .setPreferredBackend(LlmInference.Backend.GPU)
                //       .build();
                //   llmInference = LlmInference.createFromOptions(context, options);
                modelReady = true;
                mainHandler.post(() -> callback.onSuccess(null));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(
                        new AIException(AIException.Code.NOT_AVAILABLE,
                                "Failed to load on-device model", e)));
            }
        });
    }

    // ── AIProvider ───────────────────────────────────────────────────────────

    @Override
    public String getDisplayName() {
        return "On-Device (Gemma)";
    }

    @Override
    public boolean isAvailable() {
        return modelReady;
    }

    @Override
    public void complete(String prompt, AICallback<String> callback) {
        if (!modelReady) {
            mainHandler.post(() -> callback.onError(
                    new AIException(AIException.Code.NOT_AVAILABLE,
                            "On-device model is not ready yet.")));
            return;
        }
        if (prompt.length() > MAX_INPUT_CHARS) {
            mainHandler.post(() -> callback.onError(
                    new AIException(AIException.Code.INPUT_TOO_LONG,
                            "Input exceeds the on-device model's context window.")));
            return;
        }

        inferenceExecutor.execute(() -> {
            try {
                // TODO: Replace with real inference call:
                //   String result = ((LlmInference) llmInference).generateResponse(prompt);
                String result = "[On-device inference not yet implemented]";
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(
                        new AIException(AIException.Code.INFERENCE_ERROR,
                                "Inference failed", e)));
            }
        });
    }

    @Override
    public void summarize(List<String> messages, AICallback<String> callback) {
        if (messages == null || messages.isEmpty()) {
            mainHandler.post(() -> callback.onSuccess(""));
            return;
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Summarize the following Telegram messages in 2–3 concise sentences. "
                + "Focus on the key topics discussed. "
                + "Do not include names or personal identifiers.\n\n");
        for (String msg : messages) {
            prompt.append("• ").append(msg).append("\n");
        }

        complete(prompt.toString(), callback);
    }

    @Override
    public void cancel() {
        // TODO: Cancel any in-flight MediaPipe inference sessions.
    }
}
