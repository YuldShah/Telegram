package org.telegram.messenger.ai;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.telegram.messenger.ApplicationLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Local SQLite database for the AI subsystem.
 *
 * This is separate from Telegram's main messages database to isolate
 * AI data and make it easy to wipe on user request.
 *
 * Tables:
 *  - chat_interactions   — tracks when/how often the user interacts with each dialog
 *  - ai_notes            — user-created notes (AI Hub Notes feature)
 *  - ai_calendar_events  — user-created calendar events (AI Calendar feature)
 *
 * Privacy: this database never leaves the device.
 *          Call {@link #clearAllData()} to wipe everything.
 */
public class AIDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME = "ai_data.db";
    private static final int DB_VERSION = 1;

    // ── Table: chat_interactions ─────────────────────────────────────────────
    public static final String TABLE_INTERACTIONS = "chat_interactions";
    public static final String COL_DIALOG_ID = "dialog_id";
    public static final String COL_SENT_COUNT = "sent_count";
    public static final String COL_RECEIVED_COUNT = "received_count";
    public static final String COL_LAST_SENT_MS = "last_sent_ms";
    public static final String COL_LAST_RECEIVED_MS = "last_received_ms";
    public static final String COL_FIRST_TRACKED_MS = "first_tracked_ms";

    private static final String CREATE_INTERACTIONS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_INTERACTIONS + " ("
                    + COL_DIALOG_ID + " INTEGER PRIMARY KEY, "
                    + COL_SENT_COUNT + " INTEGER NOT NULL DEFAULT 0, "
                    + COL_RECEIVED_COUNT + " INTEGER NOT NULL DEFAULT 0, "
                    + COL_LAST_SENT_MS + " INTEGER NOT NULL DEFAULT 0, "
                    + COL_LAST_RECEIVED_MS + " INTEGER NOT NULL DEFAULT 0, "
                    + COL_FIRST_TRACKED_MS + " INTEGER NOT NULL DEFAULT 0"
                    + ");";

    // ── Table: ai_notes ──────────────────────────────────────────────────────
    public static final String TABLE_NOTES = "ai_notes";
    public static final String COL_NOTE_ID = "note_id";
    public static final String COL_TITLE = "title";
    public static final String COL_BODY = "body";
    public static final String COL_CREATED_MS = "created_ms";
    public static final String COL_UPDATED_MS = "updated_ms";
    public static final String COL_TAGS = "tags";          // JSON array string
    public static final String COL_IS_PINNED = "is_pinned";

    private static final String CREATE_NOTES =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NOTES + " ("
                    + COL_NOTE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COL_TITLE + " TEXT, "
                    + COL_BODY + " TEXT NOT NULL DEFAULT '', "
                    + COL_CREATED_MS + " INTEGER NOT NULL, "
                    + COL_UPDATED_MS + " INTEGER NOT NULL, "
                    + COL_TAGS + " TEXT, "
                    + COL_IS_PINNED + " INTEGER NOT NULL DEFAULT 0"
                    + ");";

    // ── Table: ai_calendar_events ────────────────────────────────────────────
    public static final String TABLE_CALENDAR = "ai_calendar_events";
    public static final String COL_EVENT_ID = "event_id";
    public static final String COL_EVENT_TITLE = "event_title";
    public static final String COL_EVENT_DESC = "event_desc";
    public static final String COL_START_MS = "start_ms";
    public static final String COL_END_MS = "end_ms";
    public static final String COL_ALL_DAY = "all_day";
    public static final String COL_RECURRENCE = "recurrence";   // null or iCalendar RRULE string
    public static final String COL_REMINDER_MS = "reminder_ms"; // ms before event, -1 = none

    private static final String CREATE_CALENDAR =
            "CREATE TABLE IF NOT EXISTS " + TABLE_CALENDAR + " ("
                    + COL_EVENT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COL_EVENT_TITLE + " TEXT NOT NULL, "
                    + COL_EVENT_DESC + " TEXT, "
                    + COL_START_MS + " INTEGER NOT NULL, "
                    + COL_END_MS + " INTEGER NOT NULL, "
                    + COL_ALL_DAY + " INTEGER NOT NULL DEFAULT 0, "
                    + COL_RECURRENCE + " TEXT, "
                    + COL_REMINDER_MS + " INTEGER NOT NULL DEFAULT -1"
                    + ");";

    // ── Singleton ────────────────────────────────────────────────────────────

    private static volatile AIDatabase instance;

    public static AIDatabase getInstance() {
        if (instance == null) {
            synchronized (AIDatabase.class) {
                if (instance == null) {
                    instance = new AIDatabase(ApplicationLoader.applicationContext);
                }
            }
        }
        return instance;
    }

    private AIDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    // ── SQLiteOpenHelper ─────────────────────────────────────────────────────

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_INTERACTIONS);
        db.execSQL(CREATE_NOTES);
        db.execSQL(CREATE_CALENDAR);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Migration logic goes here for future DB_VERSION increments.
        // Use ALTER TABLE — never DROP TABLE (user data must be preserved).
    }

    // ── Interaction tracking ─────────────────────────────────────────────────

    /** Records that the user sent a message in the given dialog. */
    public void recordMessageSent(long dialogId) {
        recordInteraction(dialogId, true);
    }

    /** Records that the user received a message in the given dialog. */
    public void recordMessageReceived(long dialogId) {
        recordInteraction(dialogId, false);
    }

    private void recordInteraction(long dialogId, boolean sent) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();

        ContentValues values = new ContentValues();
        values.put(COL_DIALOG_ID, dialogId);
        values.put(sent ? COL_SENT_COUNT : COL_RECEIVED_COUNT, 1);
        values.put(sent ? COL_LAST_SENT_MS : COL_LAST_RECEIVED_MS, now);
        values.put(COL_FIRST_TRACKED_MS, now);

        // UPSERT: insert or increment existing row.
        db.execSQL(
                "INSERT INTO " + TABLE_INTERACTIONS
                        + " (" + COL_DIALOG_ID + ", "
                        + COL_SENT_COUNT + ", "
                        + COL_RECEIVED_COUNT + ", "
                        + COL_LAST_SENT_MS + ", "
                        + COL_LAST_RECEIVED_MS + ", "
                        + COL_FIRST_TRACKED_MS + ") "
                        + "VALUES (?, 0, 0, 0, 0, ?) "
                        + "ON CONFLICT(" + COL_DIALOG_ID + ") DO NOTHING;",
                new Object[]{dialogId, now}
        );
        if (sent) {
            db.execSQL(
                    "UPDATE " + TABLE_INTERACTIONS
                            + " SET " + COL_SENT_COUNT + " = " + COL_SENT_COUNT + " + 1, "
                            + COL_LAST_SENT_MS + " = ? "
                            + "WHERE " + COL_DIALOG_ID + " = ?;",
                    new Object[]{now, dialogId}
            );
        } else {
            db.execSQL(
                    "UPDATE " + TABLE_INTERACTIONS
                            + " SET " + COL_RECEIVED_COUNT + " = " + COL_RECEIVED_COUNT + " + 1, "
                            + COL_LAST_RECEIVED_MS + " = ? "
                            + "WHERE " + COL_DIALOG_ID + " = ?;",
                    new Object[]{now, dialogId}
            );
        }
    }

    /**
     * Returns dialogs with zero sent messages AND last received message older
     * than {@code inactiveSinceMs} epoch milliseconds — candidates for leaving.
     */
    public List<Long> getInactiveDialogIds(long inactiveSinceMs) {
        List<Long> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor c = db.rawQuery(
                "SELECT " + COL_DIALOG_ID + " FROM " + TABLE_INTERACTIONS
                        + " WHERE " + COL_SENT_COUNT + " = 0"
                        + "   AND " + COL_LAST_RECEIVED_MS + " < ?"
                        + " ORDER BY " + COL_LAST_RECEIVED_MS + " ASC",
                new String[]{String.valueOf(inactiveSinceMs)}
        )) {
            while (c.moveToNext()) {
                result.add(c.getLong(0));
            }
        }
        return result;
    }

    /**
     * Returns interaction stats for a single dialog.
     * Returns {@code null} if no data has been tracked for this dialog yet.
     */
    public InteractionStats getStats(long dialogId) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT * FROM " + TABLE_INTERACTIONS
                        + " WHERE " + COL_DIALOG_ID + " = ?",
                new String[]{String.valueOf(dialogId)}
        )) {
            if (c.moveToFirst()) {
                return new InteractionStats(
                        c.getLong(c.getColumnIndexOrThrow(COL_DIALOG_ID)),
                        c.getInt(c.getColumnIndexOrThrow(COL_SENT_COUNT)),
                        c.getInt(c.getColumnIndexOrThrow(COL_RECEIVED_COUNT)),
                        c.getLong(c.getColumnIndexOrThrow(COL_LAST_SENT_MS)),
                        c.getLong(c.getColumnIndexOrThrow(COL_LAST_RECEIVED_MS)),
                        c.getLong(c.getColumnIndexOrThrow(COL_FIRST_TRACKED_MS))
                );
            }
        }
        return null;
    }

    // ── Data wipe ────────────────────────────────────────────────────────────

    /** Deletes all AI-related data. Called when the user resets AI settings. */
    public void clearAllData() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_INTERACTIONS, null, null);
        db.delete(TABLE_NOTES, null, null);
        db.delete(TABLE_CALENDAR, null, null);
    }

    // ── Value object ─────────────────────────────────────────────────────────

    public static final class InteractionStats {
        public final long dialogId;
        public final int sentCount;
        public final int receivedCount;
        public final long lastSentMs;
        public final long lastReceivedMs;
        public final long firstTrackedMs;

        public InteractionStats(long dialogId, int sentCount, int receivedCount,
                                long lastSentMs, long lastReceivedMs, long firstTrackedMs) {
            this.dialogId = dialogId;
            this.sentCount = sentCount;
            this.receivedCount = receivedCount;
            this.lastSentMs = lastSentMs;
            this.lastReceivedMs = lastReceivedMs;
            this.firstTrackedMs = firstTrackedMs;
        }

        /** Total messages (sent + received). */
        public int totalCount() {
            return sentCount + receivedCount;
        }

        /**
         * Days since the last interaction (sent or received), relative to now.
         */
        public long daysSinceLastInteraction() {
            long lastMs = Math.max(lastSentMs, lastReceivedMs);
            if (lastMs == 0) return Long.MAX_VALUE;
            return (System.currentTimeMillis() - lastMs) / 86_400_000L;
        }
    }
}
