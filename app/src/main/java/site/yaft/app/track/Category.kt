package site.yaft.app.track

/**
 * The seven foiling categories, in step with lib/categories.ts on the website.
 * The id is what the server stores; the threshold is the smoothed speed (km/h)
 * above which the rider counts as on foil. Categories that show maneuvers count
 * tacks and jibes; those that use the wind show where it came from.
 */
enum class Category(val id: String, val label: String, val foilThresholdKmh: Double, val segmentNoun: String, val showsManeuvers: Boolean, val usesWind: Boolean) {
    DOCKSTART("dockstart", "Dockstart", 11.0, "Run", false, false),
    WAKETHIEVE("wakethieve", "Dockstart wakethieve", 11.0, "Run", false, false),
    WINGFOIL("wingfoil", "Wingfoil", 12.0, "Flight", true, true),
    PARAWING("parawing", "Parawing", 12.0, "Flight", true, true),
    PARAWING_UPDOWN("parawing-updown", "Parawing up/downwind", 12.0, "Flight", true, true),
    PARAWING_DOWNWIND("parawing-downwind", "Parawing downwind", 12.0, "Flight", false, true),
    SUP_DOWNWIND("sup-downwind", "SUP downwind", 12.0, "Flight", false, false);

    companion object {
        fun fromId(id: String?): Category = entries.firstOrNull { it.id == id } ?: WINGFOIL
    }
}
