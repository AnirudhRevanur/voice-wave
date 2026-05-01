package com.voicewave.handlers

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

/**
 * Opens a WhatsApp chat with a contact by name.
 *
 * HOW IT WORKS:
 * WhatsApp registers a deep link handler for:
 *   https://wa.me/<phone_number>
 *
 * So we:
 * 1. Look up the contact's phone number from the contacts database
 * 2. Strip it down to digits only (remove +, spaces, dashes)
 * 3. Fire the wa.me deep link → WhatsApp opens the chat directly
 */
object WhatsAppHandler {

    fun handle(context: Context, contactName: String): Boolean {
        val number = findPhoneNumber(context.contentResolver, contactName)
            ?: return false

        // Strip everything except digits and leading +
        val cleaned = number.replace(Regex("[^\\d+]"), "")

        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/$cleaned")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun findPhoneNumber(resolver: ContentResolver, name: String): String? {
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
