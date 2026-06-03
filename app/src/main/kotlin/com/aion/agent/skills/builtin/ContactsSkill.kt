package com.aion.agent.skills.builtin

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import com.aion.agent.core.AgentCapability
import com.aion.agent.skills.AgentSkill
import com.aion.agent.skills.SkillDefinition
import com.aion.agent.skills.SkillParameter
import com.aion.agent.skills.SkillResult
import com.aion.agent.util.AionLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in skill for reading the device contacts. Follows the same pattern as [SmsSkill] and [CallSkill]:
 *  - Requires [AgentCapability.MINIMAL] (contacts are read-only; the runtime permission
 *    guard is handled inside [execute])
 *  - Verifies READ_CONTACTS permission before querying
 *  - Returns matching contacts with phone numbers when available
 */
@Singleton
class ContactsSkill @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AionLogger,
) : AgentSkill {

    override val definition: SkillDefinition = SkillDefinition(
        id = "contacts.find",
        name = "Find Contact",
        description = "Looks up a contact by name or lists contacts. " +
            "Use when the user asks to find, look up, or search for a contact.",
        keywords = listOf(
            "contact", "address book", "phonebook", "find contact",
            "look up", "search contact", "phone number", "contacts",
        ),
        parameters = listOf(
            SkillParameter(
                name = "query",
                description = "Name or partial name to search for",
                jsonType = "string",
                required = true,
            ),
            SkillParameter(
                name = "limit",
                description = "Maximum number of results to return",
                jsonType = "string",
                required = false,
            ),
        ),
        requiredPermissions = listOf("android.permission.READ_CONTACTS"),
        requiredCapability = AgentCapability.MINIMAL,
    )

    override fun canHandle(input: String): Float {
        return if ("contact" in input.lowercase()) 0.4f else 0f
    }

    override suspend fun execute(params: Map<String, String>): SkillResult = withContext(Dispatchers.IO) {
        val query = params["query"]?.trim().orEmpty()
        if (query.isBlank()) {
            return@withContext SkillResult.Failure(
                reason = "Missing 'query' parameter",
                summary = "Please provide a name to search for.",
            )
        }
        val limit = params["limit"]?.trim()?.toIntOrNull() ?: 5

        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext SkillResult.Failure(
                reason = "READ_CONTACTS permission not granted",
                summary = "I need contacts permission to look that up. Enable it in Settings.",
            )
        }

        try {
            val contacts = searchContacts(query, limit)
            if (contacts.isEmpty()) {
                SkillResult.Success(
                    output = "No contacts found matching \"$query\"",
                    summary = "No contacts found for \"$query\".",
                )
            } else {
                val lines = contacts.joinToString("\n")
                SkillResult.Success(
                    output = lines,
                    summary = "Found ${contacts.size} contact(s) matching \"$query\".",
                )
            }
        } catch (t: Throwable) {
            logger.e("ContactsSkill", t) { "Contact lookup failed" }
            SkillResult.Failure(
                reason = t.message ?: "Unknown error",
                summary = "Couldn't search contacts: ${t.message ?: "unknown error"}",
            )
        }
    }

    /**
     * Queries [ContactsContract.Contacts] for contacts whose display name
     * contains [query], up to [limit] results.
     */
    private fun searchContacts(query: String, limit: Int): List<String> {
        val resolver: ContentResolver = context.contentResolver
        val uri = ContactsContract.Contacts.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
        )
        val selection = "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")
        val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME} ASC"

        val results = mutableListOf<String>()

        resolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            while (cursor.moveToNext() && results.size < limit) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
                val hasPhone = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0

                val phoneNumbers = if (hasPhone) getPhoneNumbers(id) else emptyList()
                val phoneStr = if (phoneNumbers.isNotEmpty()) {
                    " — ${phoneNumbers.joinToString(", ")}"
                } else {
                    ""
                }
                results.add("$name$phoneStr")
            }
        }

        return results
    }

    /**
     * Queries [ContactsContract.CommonDataKinds.Phone] for all phone numbers
     * associated with the given [contactId].
     */
    private fun getPhoneNumbers(contactId: Long): List<String> {
        val resolver: ContentResolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        val selectionArgs = arrayOf(contactId.toString())

        val numbers = mutableListOf<String>()

        resolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                val type = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE))
                val typeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, type, null)
                numbers.add("$number ($typeLabel)")
            }
        }

        return numbers
    }
}
