package site.yaft.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import site.yaft.app.net.Account
import site.yaft.app.net.Http
import site.yaft.app.net.NetworkError
import site.yaft.app.track.Category

/** One item of the rider's gear on yaft. Retired gear stays on old sessions but isn't offered for new picks. */
data class GearItem(val id: String, val kind: String, val name: String, val retired: Boolean)

/** A gear slot a category shows: the session column it fills (board_id…), the kind of gear it takes and its label. */
data class GearSlot(val slot: String, val kind: String, val label: String)

/**
 * The rider's gear and each category's gear slots, from GET /api/app/gear (API
 * version 2). The slot rules come from the website, so the app doesn't copy them.
 */
class GearList(val items: List<GearItem>, private val slots: Map<String, List<GearSlot>>) {
    /** The slots a session of [category] shows, in the website's order. */
    fun slots(category: Category): List<GearSlot> = slots[category.id].orEmpty()

    /** What [slot] offers: the rider's gear of its kind that isn't retired, plus [current], as the website's selects do. */
    fun options(slot: GearSlot, current: String?): List<GearItem> = items.filter { (it.kind == slot.kind && !it.retired) || it.id == current }

    fun name(id: String?): String? = items.firstOrNull { it.id == id }?.name

    companion object {
        fun fromJson(j: JSONObject): GearList {
            val gear = j.getJSONArray("gear")
            val items = (0 until gear.length()).map { i ->
                gear.getJSONObject(i).let { GearItem(it.getString("id"), it.getString("kind"), it.getString("name"), it.optBoolean("retired")) }
            }
            val bySlot = j.getJSONObject("slots")
            val slots = bySlot.keys().asSequence().associateWith { category ->
                val list = bySlot.getJSONArray(category)
                (0 until list.length()).map { i -> list.getJSONObject(i).let { GearSlot(it.getString("slot"), it.getString("kind"), it.getString("label")) } }
            }
            return GearList(items, slots)
        }
    }
}

/** One part of a split session: when it starts (epoch ms) and its gear ids per slot. */
data class PartGear(val startsAt: Long, val gear: Map<String, String?>)

/** Gear ids per slot column, as a session's details have them; null where none is set. */
internal fun gearIds(j: JSONObject?): Map<String, String?> =
    j?.keys()?.asSequence()?.associateWith { if (j.isNull(it)) null else j.optString(it) }.orEmpty()

/** What loading the rider's gear came to. */
sealed interface GearLoad {
    data class Ready(val gear: GearList) : GearLoad
    data object SignedOut : GearLoad
    data class Failed(val message: String) : GearLoad
}

/** Loads the rider's gear and the slots from yaft. Needs a connection. */
suspend fun loadGear(account: Account): GearLoad = withContext(Dispatchers.IO) {
    try {
        val token = account.accessToken() ?: return@withContext GearLoad.SignedOut
        val res = Http.request("GET", "${account.siteUrl}/api/app/gear", headers = mapOf("Authorization" to "Bearer $token"))
        when {
            res.status == 401 -> GearLoad.SignedOut
            !res.ok -> GearLoad.Failed(res.json.optString("error").ifEmpty { "yaft couldn't load your gear. Try again later." })
            else -> GearLoad.Ready(GearList.fromJson(res.json))
        }
    } catch (_: NetworkError) {
        GearLoad.Failed(NetworkError.MESSAGE)
    } catch (_: Exception) {
        GearLoad.Failed("yaft sent something unexpected. Try again later.")
    }
}
