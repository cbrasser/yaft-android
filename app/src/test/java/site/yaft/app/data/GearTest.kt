package site.yaft.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.yaft.app.track.Category

class GearTest {
    private fun recorded(name: String) = JSONObject(javaClass.getResource("/$name")!!.readText())

    // GET /api/app/gear for a throwaway rider on a local yaft: two boards, a retired one, a front wing, a stab, a wing and a parawing.
    private val gear = GearList.fromJson(recorded("gear-list.json"))
    private val kode = "3f944d6a-0dd8-4b12-b57b-336626a9c6e0"
    private val retired = "5224ab78-8e99-4dd9-8ebd-db9fffd89f69"

    @Test
    fun eachCategoryHasTheWebsitesSlots() {
        val foil = listOf(GearSlot("board_id", "board", "Board"), GearSlot("frontwing_id", "frontwing", "Front wing"), GearSlot("stab_id", "stab", "Stab"))
        assertEquals(foil, gear.slots(Category.DOCKSTART))
        assertEquals(foil + GearSlot("wing_id", "wing", "Wing"), gear.slots(Category.WINGFOIL))
        assertEquals(foil + GearSlot("wing_id", "parawing", "Parawing"), gear.slots(Category.PARAWING_DOWNWIND))
        Category.entries.forEach { assertTrue("${it.id} has slots", gear.slots(it).isNotEmpty()) }
    }

    @Test
    fun aSlotOffersGearOfItsKindThatIsntRetired() {
        val board = gear.slots(Category.WINGFOIL)[0]
        assertEquals(listOf("Flow 110", "Kode 85"), gear.options(board, null).map { it.name })
        // What the session has stays offered, retired or not, as on the website.
        assertEquals(listOf("Flow 110", "Kode 85", "Retired board"), gear.options(board, retired).map { it.name })
        assertEquals(listOf("Hydra 3"), gear.options(gear.slots(Category.PARAWING)[3], null).map { it.name })
    }

    @Test
    fun gearIsNamedById() {
        assertEquals("Kode 85", gear.name(kode))
        assertEquals("Retired board", gear.name(retired))
        assertNull(gear.name("not-mine"))
        assertNull(gear.name(null))
    }

    // The details of a session split into two parts, as GET /api/app/sessions/<id> has them.
    @Test
    fun aSplitSessionsGearIsOnItsParts() {
        val response = recorded("session-view-phone.json")
        val plain = SessionView.fromJson(response)
        assertFalse(plain.split)
        assertEquals(mapOf("board_id" to null, "frontwing_id" to null, "stab_id" to null, "wing_id" to null), plain.gear)

        response.getJSONObject("session").put(
            "details",
            JSONObject(
                """{"gear":{"board_id":null,"frontwing_id":null,"stab_id":null,"wing_id":null},"windBase":12,"windGust":18,"weather":null,
                "parts":[{"id":"p1","startsAt":1790937945000,"gear":{"board_id":"$kode","frontwing_id":null,"stab_id":null,"wing_id":null}},
                {"id":"p2","startsAt":1790940942000,"gear":{"board_id":"$retired","frontwing_id":null,"stab_id":null,"wing_id":null}}],"stravaId":null}""",
            ),
        )
        val split = SessionView.fromJson(response)
        assertTrue(split.split)
        assertEquals(listOf(1_790_937_945_000L, 1_790_940_942_000L), split.parts.map { it.startsAt })
        assertEquals(kode, split.parts[0].gear["board_id"])
        assertEquals(retired, split.parts[1].gear["board_id"])
        assertEquals(12, split.windBase)
    }
}
