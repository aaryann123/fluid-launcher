package app.fluid.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.compose.runtime.Immutable

/** A person search can call or text, with one number: their default, else their mobile, else the first. */
@Immutable
data class ContactHit(
    override val key: String, override val label: String, val number: String, val kind: String, val text: Boolean,
) : Hit

/** Stands in for contacts on a "call …"/"text …" search until Fluid may read them. */
data class AskContacts(override val label: String) : Hit {
    override val key get() = "ask-contacts"
}

object Contacts {
    private val verbs = mapOf(
        "call" to false, "ring" to false, "dial" to false, "phone" to false,
        "text" to true, "message" to true, "msg" to true, "sms" to true,
    )

    /** "call mom" → (false, "mom"), "text sam" → (true, "sam"), "mom" → (null, "mom"). */
    fun parse(query: String): Pair<Boolean?, String> {
        val q = query.trim()
        val verb = verbs[q.substringBefore(' ').lowercase()]
        val rest = q.substringAfter(' ', "").trim()
        return if (verb != null && rest.isNotEmpty()) verb to rest else null to q
    }

    fun allowed(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** People whose name matches, via the contacts provider's own (indexed, nickname-aware) name filter. */
    fun search(ctx: Context, name: String, text: Boolean, max: Int = 5): List<ContactHit> {
        if (name.length < 2 || !allowed(ctx)) return emptyList()
        val cols = arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE, Phone.LABEL, Phone.IS_SUPER_PRIMARY)
        val best = LinkedHashMap<Long, Pair<Int, ContactHit>>()
        runCatching {
            ctx.contentResolver.query(Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(name)), cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val display = c.getString(1) ?: continue
                    val number = c.getString(2) ?: continue
                    val type = c.getInt(3)
                    val rank = when {
                        c.getInt(5) != 0 -> 0
                        type == Phone.TYPE_MOBILE -> 1
                        else -> 2
                    }
                    if ((best[id]?.first ?: 3) <= rank) continue
                    val kind = Phone.getTypeLabel(ctx.resources, type, c.getString(4)).toString().lowercase()
                    best[id] = rank to ContactHit("c|$id", display, number, kind, text)
                }
            }
        }
        return best.values.map { it.second }.take(max)
    }
}
