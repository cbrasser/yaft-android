package site.yaft.app.track

/**
 * The seven foiling categories, in step with lib/categories.ts on the website.
 * The id is what the server stores; the threshold is the smoothed speed (km/h)
 * above which the rider counts as on foil.
 */
enum class Category(val id: String, val label: String, val foilThresholdKmh: Double, val segmentNoun: String) {
    DOCKSTART("dockstart", "Dockstart", 11.0, "Run"),
    WAKETHIEVE("wakethieve", "Dockstart wakethieve", 11.0, "Run"),
    WINGFOIL("wingfoil", "Wingfoil", 12.0, "Flight"),
    PARAWING("parawing", "Parawing", 12.0, "Flight"),
    PARAWING_UPDOWN("parawing-updown", "Parawing up/downwind", 12.0, "Flight"),
    PARAWING_DOWNWIND("parawing-downwind", "Parawing downwind", 12.0, "Flight"),
    SUP_DOWNWIND("sup-downwind", "SUP downwind", 12.0, "Flight");

    companion object {
        fun fromId(id: String?): Category = entries.firstOrNull { it.id == id } ?: WINGFOIL
    }
}
