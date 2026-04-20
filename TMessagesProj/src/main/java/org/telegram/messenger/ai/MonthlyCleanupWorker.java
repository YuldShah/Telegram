package org.telegram.messenger.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.telegram.messenger.NotificationCenter;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Background job that runs once per month to identify inactive chats
 * and surface a cleanup suggestion to the user.
 *
 * Scheduling: uses WorkManager with a 30-day interval. WorkManager
 * respects doze mode and battery optimizations, so the exact execution
 * time may vary by a few hours — this is intentional.
 *
 * Privacy: all processing is 100% local. No data is sent anywhere.
 */
public class MonthlyCleanupWorker extends Worker {

    /** Unique work name — prevents duplicate scheduled tasks. */
    public static final String WORK_NAME = "ai_monthly_cleanup";

    /** Inactivity threshold: dialogs with no sent message for this many days are flagged. */
    private static final int INACTIVITY_THRESHOLD_DAYS = 90;

    /** NotificationCenter event ID posted when new suggestions are ready. */
    public static final int EVENT_CLEANUP_SUGGESTIONS_READY =
            NotificationCenter.ai_cleanupSuggestionsReady;

    public MonthlyCleanupWorker(@NonNull Context context,
                                 @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        AIPrivacySettings privacy = AIPrivacySettings.getInstance();

        // Respect opt-out.
        if (!privacy.isFeatureEnabled(AIFeature.MONTHLY_CLEANUP)) {
            return Result.success();
        }

        try {
            long thresholdMs = System.currentTimeMillis()
                    - (long) INACTIVITY_THRESHOLD_DAYS * 24 * 60 * 60 * 1_000L;
            List<Long> inactiveIds = AIDatabase.getInstance()
                    .getInactiveDialogIds(thresholdMs);

            if (!inactiveIds.isEmpty()) {
                // Notify the UI layer — DialogsActivity observes this and shows
                // the cleanup suggestion banner.
                android.os.Handler mainHandler =
                        new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() ->
                        NotificationCenter.getGlobalInstance()
                                .postNotificationName(EVENT_CLEANUP_SUGGESTIONS_READY,
                                        inactiveIds));
            }
            return Result.success();
        } catch (Exception e) {
            // Retry later — don't crash WorkManager.
            return Result.retry();
        }
    }

    // ── Scheduling helpers ───────────────────────────────────────────────────

    /**
     * Schedules the monthly job. Safe to call multiple times — duplicate
     * requests are deduplicated by {@link ExistingPeriodicWorkPolicy#KEEP}.
     *
     * Call this from {@link org.telegram.messenger.ApplicationLoader} after
     * the user enables the cleanup feature.
     */
    public static void schedule(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                MonthlyCleanupWorker.class, 30, TimeUnit.DAYS)
                .setInitialDelay(30, TimeUnit.DAYS)
                .build();

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        WORK_NAME,
                        ExistingPeriodicWorkPolicy.KEEP,
                        request);
    }

    /**
     * Cancels the scheduled job when the user disables the cleanup feature.
     */
    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }
}
