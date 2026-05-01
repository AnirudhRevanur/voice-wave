package com.voicewave.handlers

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

/**
 * Looks up a contact by name and fires the dialer.
 *
 * We use ACTION_DIAL (not ACTION_CALL) which opens the dialer with the
 * number pre-filled but doesn't auto-dial. This is safer — the user
 * taps the call button themselves. If you want auto-dial, swap to
 * ACTION_CALL (requires CALL_PHONE permission, already in manifest).
 */
object CallHandler {

    fun handle(context: Context, contactName: String): Boolean {
        val number = findPhoneNumber(context.contentResolver, contactName)
            ?: return false  // Contact not found

        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(dialIntent)
        return true
    }

    private fun findPhoneNumber(resolver: ContentResolver, name: String): String? {
        // Query contacts whose display name contains what the user said
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        ) ?: return null

        return cursor.use {
            if (it.moveToFirst()) {
                it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
            } else null
        }
    }
}
