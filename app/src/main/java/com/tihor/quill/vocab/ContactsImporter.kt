package com.tihor.quill.vocab

import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads contact display names via `ContactsContract`, filters them through [ContactVocabulary], and
 * stores the result in [ContactsVocabularyStore] — **only** when the user explicitly taps "Import
 * from contacts" (Phase 1). Never runs automatically, never on a schedule, and makes **no network
 * call**: the data flows ContactsContract → filter → on-device DataStore, nothing else.
 *
 * The query runs on [Dispatchers.IO] (contacts providers can be slow) and fails closed: any
 * provider/security error yields [Result.failure], and the previously stored vocabulary is left
 * untouched (fail-soft — a failed re-import never wipes a good earlier import).
 */
class ContactsImporter(private val context: Context) {

    private val store = ContactsVocabularyStore(context)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Import now. Requires [android.Manifest.permission.READ_CONTACTS] — check [hasPermission] and
     * request it from the UI first. @return the number of stored terms, or a failure (store
     * unchanged).
     */
    suspend fun import(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val names = ArrayList<String>()
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.let { names.add(it) }
                }
            }
            val terms = ContactVocabulary.extract(names)
            store.replaceAll(terms)
            terms.size
        }
    }
}
