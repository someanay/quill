package com.tihor.quill.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** One saved dictation (M9). [appLabel]/[packageName] are the field's app at record time. */
data class HistoryEntry(
    val id: Long,
    val text: String,
    val createdAt: Long,
    val appLabel: String?,
    val packageName: String?,
)

/**
 * Hand-rolled SQLite store for dictation history (M9). No Room/KSP — KSP was deferred at M0 as fragile
 * against AGP 9.2.1's built-in Kotlin, and this is a single table. Local-only, app-private storage;
 * the DB file is excluded from Auto Backup (res/xml backup rules), relying on Android FBE at rest.
 */
class HistoryStore(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TEXT TEXT NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_PACKAGE TEXT,
                $COL_APP_LABEL TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_${TABLE}_created ON $TABLE($COL_CREATED_AT DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 only; future migrations slot in here.
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    fun insert(text: String, createdAt: Long, packageName: String?, appLabel: String?): Long {
        val values = ContentValues().apply {
            put(COL_TEXT, text)
            put(COL_CREATED_AT, createdAt)
            put(COL_PACKAGE, packageName)
            put(COL_APP_LABEL, appLabel)
        }
        return writableDatabase.insert(TABLE, null, values)
    }

    /** Delete entries created strictly before [cutoff] (epoch ms). Returns the number deleted. */
    fun pruneOlderThan(cutoff: Long): Int =
        writableDatabase.delete(TABLE, "$COL_CREATED_AT < ?", arrayOf(cutoff.toString()))

    /** Prune to the newest [cap] rows. Returns the number deleted. */
    fun pruneTo(cap: Int): Int {
        // Keep the newest `cap` rows (by time, ties broken by id); delete the rest.
        return writableDatabase.delete(
            TABLE,
            "$COL_ID NOT IN (SELECT $COL_ID FROM $TABLE ORDER BY $COL_CREATED_AT DESC, $COL_ID DESC LIMIT ?)",
            arrayOf(cap.toString()),
        )
    }

    fun queryAll(): List<HistoryEntry> {
        val out = ArrayList<HistoryEntry>()
        readableDatabase.query(
            TABLE, null, null, null, null, null, "$COL_CREATED_AT DESC, $COL_ID DESC",
        ).use { c ->
            val iId = c.getColumnIndexOrThrow(COL_ID)
            val iText = c.getColumnIndexOrThrow(COL_TEXT)
            val iCreated = c.getColumnIndexOrThrow(COL_CREATED_AT)
            val iPkg = c.getColumnIndexOrThrow(COL_PACKAGE)
            val iLabel = c.getColumnIndexOrThrow(COL_APP_LABEL)
            while (c.moveToNext()) {
                out += HistoryEntry(
                    id = c.getLong(iId),
                    text = c.getString(iText),
                    createdAt = c.getLong(iCreated),
                    packageName = if (c.isNull(iPkg)) null else c.getString(iPkg),
                    appLabel = if (c.isNull(iLabel)) null else c.getString(iLabel),
                )
            }
        }
        return out
    }

    fun delete(id: Long): Int = writableDatabase.delete(TABLE, "$COL_ID = ?", arrayOf(id.toString()))

    fun clearAll(): Int = writableDatabase.delete(TABLE, null, null)

    fun count(): Long {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { c ->
            return if (c.moveToFirst()) c.getLong(0) else 0L
        }
    }

    companion object {
        const val DB_NAME = "quill_history.db"
        const val DB_VERSION = 1
        private const val TABLE = "history"
        private const val COL_ID = "id"
        private const val COL_TEXT = "text"
        private const val COL_CREATED_AT = "created_at"
        private const val COL_PACKAGE = "package"
        private const val COL_APP_LABEL = "app_label"
    }
}
