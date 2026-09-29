package com.example.autodial.util

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import android.util.Log

object ContactsHelper {

    fun saveContact(context: Context, name: String, phone: String, prefix: String = "") {
        if (name.isBlank() || name == "UNKNOWN" || phone.isBlank()) return

        try {
            val ops = ArrayList<ContentProviderOperation>()

            // 1. Check if contact already exists
            val contactExists = checkContactExists(context, phone)
            if (contactExists) {
                Log.d("ContactsHelper", "Contact with phone $phone already exists. Skipping sync.")
                return
            }

            // 2. Insert new RawContact
            ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build())

            // 3. Insert Name
            val displayName = if (prefix.isNotBlank()) "$name $prefix" else name
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
                .build())

            // 4. Insert Phone Number
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build())

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            Log.d("ContactsHelper", "Successfully saved contact: $name ($phone)")
        } catch (e: Exception) {
            Log.e("ContactsHelper", "Failed to save contact: ${e.message}", e)
        }
    }

    private fun checkContactExists(context: Context, phoneNumber: String): Boolean {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?"
        val selectionArgs = arrayOf(phoneNumber)

        val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
        val exists = cursor?.use { it.count > 0 } ?: false
        return exists
    }

    fun readDeviceContacts(context: Context): List<Pair<String, String>> {
        val contacts = mutableListOf<Pair<String, String>>()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val cursor = context.contentResolver.query(uri, projection, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")
            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = if (nameIndex != -1) it.getString(nameIndex) ?: "UNKNOWN" else "UNKNOWN"
                    val number = if (numberIndex != -1) it.getString(numberIndex) ?: "" else ""
                    if (number.isNotBlank()) {
                        contacts.add(name to number)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ContactsHelper", "Failed to read device contacts: ${e.message}", e)
        }
        return contacts
    }
}
