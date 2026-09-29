package com.example.autodial.util

import android.content.ContentProviderOperation
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import com.example.autodial.data.Contact
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

object ContactSyncHelper {

    fun syncToPhone(context: Context, contacts: List<Contact>, globalPrefix: String, dao: com.example.autodial.data.ContactDao? = null) {
        contacts.forEach { contact ->
            saveIndividualContact(context, contact.name, contact.phone, contact.prefix.ifBlank { globalPrefix }, dao)
        }
    }

    fun saveIndividualContact(context: Context, name: String, phone: String, prefix: String, dao: com.example.autodial.data.ContactDao? = null) {
        // 1. Save to Internal Room Database
        dao?.let {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val existing = it.getAllContacts().first().find { c -> c.phone == phone }
                if (existing == null) {
                    Log.d("ContactSync", "Adding new contact to internal DB: $name")
                    it.insertContact(com.example.autodial.data.Contact(name = name, phone = phone, prefix = prefix))
                } else {
                    Log.d("ContactSync", "Updating existing contact in internal DB: $name")
                    it.updateContact(existing.copy(name = name, prefix = prefix))
                }
            }
        }

        // 2. Save to Phone System Contacts
        try {
            val resolver = context.contentResolver
            val displayName = "$name $prefix".trim()
            val rawContactId = findRawContactByPhone(context, phone)

            val ops = ArrayList<ContentProviderOperation>()

            if (rawContactId == -1L) {
                // Insert new contact
                ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build())

                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
                    .build())

                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build())
            } else {
                // Update existing contact name (keep the phone)
                ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                        arrayOf(rawContactId.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    )
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
                    .build())
            }

            if (ops.isNotEmpty()) {
                resolver.applyBatch(ContactsContract.AUTHORITY, ops)
                Log.d("ContactSync", "Successfully saved/updated contact: $displayName ($phone)")
            }
        } catch (e: Exception) {
            Log.e("ContactSync", "Failed to save contact: $name", e)
        }
    }

    private fun findRawContactByPhone(context: Context, phone: String): Long {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone))
        val projection = arrayOf(ContactsContract.PhoneLookup._ID)
        
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val contactId = cursor.getLong(0)
                // Now find a raw contact ID for this contact ID
                return findRawContactIdForContact(context, contactId)
            }
        }
        return -1L
    }

    private fun findRawContactIdForContact(context: Context, contactId: Long): Long {
        val uri = ContactsContract.RawContacts.CONTENT_URI
        val projection = arrayOf(ContactsContract.RawContacts._ID)
        val selection = "${ContactsContract.RawContacts.CONTACT_ID} = ?"
        val selectionArgs = arrayOf(contactId.toString())
        
        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        return -1L
    }
}
