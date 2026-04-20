package org.telegram.messenger.ai;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cloud AI provider — calls an OpenAI-compatible chat completion endpoint.
 *
 * Data sent to the API:
 *  - Only the text of messages the user has explicitly asked to summarize.
 *  - No user IDs, dialog IDs, or any Telegram-specific metadata.
 *  - Requests are made over HTTPS only.
 *
 * Privacy gate: callers MUST check
 *   {@link AIPrivacySettings#isCloudConsentGiven(AIFeature)} before invoking
 *   any method in this class. The manager ({@link AIManager}) enforces this.
 *
 * TODO (implementation steps):
 *  1. Add OkHttp to TMessagesProj/build.gradle:
 *       implementation 'com.squareup.okhttp3:okhttp:4.12.0'
 *  2. Store the user's API key in Android Keystore (never in SharedPreferences
 *     in plain text) via {@link AIKeyStoreHelper}.
 *  3. Replace the stub network call below with real OkHttp logic.
 */
public class CloudAIProvider implements AIProvider {

    /** Default API base URL — can be overridden in settings for self-hosted deployments. */
    public static final String DEFAULT_API_BASE = "https://api.openai.com/v1";

    /** Model used for standard completion/summarization tasks. */
    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    /** Maximum characters in a single request to avoid runaway API costs. */
    private static final int MAX_INPUT_CHARS = 32_000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService networkExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ai-cloud-request");
        t.setDaemon(true);
        return t;
    });

    private volatile String apiKey = null;
    private volatile String apiBase = DEFAULT_API_BASE;
    private volatile String model = DEFAULT_MODEL;

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    // TODO: replace with real OkHttpClient once dependency is added.
    // private OkHttpClient httpClient;

    public CloudAIProvider() {
        loadApiKey();
    }

    // ── Configuration ────────────────────────────────────────────────────────

    private void loadApiKey() {
        // TODO: Retrieve API key from Android Keystore via AIKeyStoreHelper:
        //   apiKey = AIKeyStoreHelper.getApiKey();
    }

    public void setApiKey(String key) {
        // TODO: Store in Android Keystore, not SharedPreferences.
        //   AIKeyStoreHelper.storeApiKey(key);
        this.apiKey = key;
    }

    public void setApiBase(String base) {
        this.apiBase = base;
    }

    public void setModel(String model) {
        this.model = model;
    }

    // ── AIProvider ───────────────────────────────────────────────────────────

    @Override
    public String getDisplayName() {
        return "Cloud AI (OpenAI-compatible)";
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }

    @Override
    public void complete(String prompt, AICallback<String> callback) {
        if (apiKey == null || apiKey.isEmpty()) {
            mainHandler.post(() -> callback.onError(
                    new AIException(AIException.Code.NOT_AVAILABLE,
                            "No API key configured.")));
            return;
        }
        if (prompt.length() > MAX_INPUT_CHARS) {
            mainHandler.post(() -> callback.onError(
                    new AIException(AIException.Code.INPUT_TOO_LONG,
                            "Input exceeds the cloud API's supported context.")));
            return;
        }

        cancelled.set(false);
        networkExecutor.execute(() -> {
            if (cancelled.get()) return;

            try {
                // Build the request body (OpenAI chat completions format).
                JSONObject requestBody = buildChatRequest(prompt);

                // TODO: Replace with real OkHttp call:
                //   RequestBody body = RequestBody.create(
                //       requestBody.toString(), MediaType.get("application/json"));
                //   Request request = new Request.Builder()
                //       .url(apiBase + "/chat/completions")
                //       .header("Authorization", "Bearer " + apiKey)
                //       .header("Content-Type", "application/json")
                //       .post(body)
                //       .build();
                //   try (Response response = httpClient.newCall(request).execute()) {
                //       String json = response.body().string();
                //       String result = parseCompletionResponse(json);
                //       mainHandler.post(() -> callback.onSuccess(result));
                //   }

                // Stub response until OkHttp is added:
                String stubResult = "[Cloud inference not yet implemented — add OkHttp dependency]";
                if (!cancelled.get()) {
                    mainHandler.post(() -> callback.onSuccess(stubResult));
                }
            } catch (JSONException e) {
                mainHandler.post(() -> callback.onError(
                        new AIException(AIException.Code.INFERENCE_ERROR,
                                "Failed to build request", e)));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(
                        new AIException(AIException.Code.NETWORK_ERROR,
                                "Network request failed", e)));
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
                + "Focus on the key topics and action items. "
                + "Omit @mentions, names, and any personal identifiers.\n\n");
        for (String msg : messages) {
            prompt.append("• ").append(msg).append("\n");
        }

        complete(prompt.toString(), callback);
    }

    @Override
    public void cancel() {
        cancelled.set(true);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private JSONObject buildChatRequest(String userContent) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", userContent);

        JSONArray messages = new JSONArray();
        messages.put(message);

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", 512);
        body.put("temperature", 0.3);
        return body;
    }

    @SuppressWarnings("unused")
    private String parseCompletionResponse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        return root.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim();
    }
}
