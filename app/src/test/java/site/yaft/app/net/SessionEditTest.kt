package site.yaft.app.net

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import site.yaft.app.data.Wind
import site.yaft.app.track.Category

class SessionEditTest {
    private fun assertJson(expected: String, actual: JSONObject) = assertEquals(JSONObject(expected).toString(), actual.toString())

    @Test
    fun aCategoryEditSendsOnlyTheCategory() {
        assertJson("""{"category":"parawing"}""", SessionEdit.Ridden(Category.PARAWING).toJson())
    }

    @Test
    fun aWindEditSendsTheDirectionAndStrengthAsTyped() {
        assertJson("""{"windFrom":250,"windBase":14,"windGust":20}""", SessionEdit.Wind(" 250 ", "14", "20").toJson())
        // Empty fields clear: yaft estimates the direction again, and the strength goes.
        assertJson("""{"windFrom":null,"windBase":null,"windGust":null}""", SessionEdit.Wind("", " ", "").toJson())
        // What isn't a whole number goes as typed, so the website answers with its own message.
        assertJson("""{"windFrom":"north","windBase":"14.5","windGust":null}""", SessionEdit.Wind("north", "14.5", "").toJson())
    }

    @Test
    fun aWindEditSendsOnlyWhatChanged() {
        // A category without wind strength, or only the direction changed.
        assertJson("""{"windFrom":90}""", SessionEdit.Wind("90", null, null).toJson())
        // Only the strength changed: the direction isn't sent, so yaft keeps it and the analysis.
        assertJson("""{"windBase":12,"windGust":null}""", SessionEdit.Wind(null, "12", "").toJson())
    }

    @Test
    fun aNotesEditSendsTheNotesAndRating() {
        assertJson("""{"notes":"glassy, then gusty","rating":4}""", SessionEdit.Notes("glassy, then gusty", 4).toJson())
        assertJson("""{"notes":"","rating":null}""", SessionEdit.Notes("", null).toJson())
    }

    @Test
    fun aGearEditSendsEachSlotWithNoneAsNull() {
        assertJson(
            """{"gear":{"board_id":"3f944d6a-0dd8-4b12-b57b-336626a9c6e0","wing_id":null}}""",
            SessionEdit.Gear(mapOf("board_id" to "3f944d6a-0dd8-4b12-b57b-336626a9c6e0", "wing_id" to null)).toJson(),
        )
    }

    // Gear on a split session, or gear that's gone: the website refuses it with its own message.
    @Test
    fun refusedGearSaysWhy() {
        val split = "This session is split into parts, each with its own gear. Change the gear on yaft."
        assertEquals(EditResult.Invalid(split), editResult(409, """{"error":"$split"}"""))
    }

    private fun recorded(name: String) = javaClass.getResource("/$name")!!.readText()

    // PATCH /api/app/sessions/<id> on the phone recording of session-view-phone.json, uploaded to a local yaft:
    // { category: parawing, windFrom: 250, windBase: 14, windGust: 20, notes, rating: 4 }.
    @Test
    fun aSavedEditComesBackAsTheNewView() {
        val result = editResult(200, recorded("session-edit-phone.json"))
        assertTrue(result is EditResult.Saved)
        val v = (result as EditResult.Saved).view
        assertEquals(Category.PARAWING, v.category)
        assertEquals("77 s on foil over 321 m, 1 touchdown, with 0 jibes and 0 tacks.", v.summary)
        assertEquals(Wind(250.0, "user"), v.wind)
        assertEquals(250, v.savedWind)
        assertEquals(14, v.windBase)
        assertEquals(20, v.windGust)
        assertEquals("glassy, then gusty", v.notes)
        assertEquals(4, v.rating)
    }

    @Test
    fun aRefusedEditSaysWhy() {
        assertEquals(EditResult.Invalid("Gusts can’t be lighter than the base wind."), editResult(400, """{"error":"Gusts can’t be lighter than the base wind."}"""))
        assertEquals(EditResult.SignedOut, editResult(401, """{"error":"Log in again to change this session."}"""))
        assertEquals(EditResult.Gone, editResult(404, """{"error":"This session isn’t on yaft any more."}"""))
        assertEquals(EditResult.Failed("Couldn’t save that. Try again in a moment."), editResult(500, """{"error":"Couldn’t save that. Try again in a moment."}"""))
        assertEquals(EditResult.Failed(EditResult.TRY_AGAIN), editResult(502, "<html>Bad gateway</html>"))
        assertEquals(EditResult.Failed(EditResult.UNEXPECTED), editResult(200, "{}"))
    }

    // A server from before the edit endpoint (API version 2 without PATCH) answers 405.
    @Test
    fun aServerWithoutEditingSaysSo() {
        assertEquals(EditResult.Failed(EditResult.OLD_SERVER), editResult(405, ""))
    }
}
