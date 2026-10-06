package site.yaft.app.track

/**
 * Running numbers for a session as points arrive. These are the phone's quick
 * view; yaft.site redoes the full analysis (gap bridging, short segments, top
 * speed from the speed field) when the session is uploaded.
 */
class LiveStats(private val foilThresholdKmh: Double) {
    var points = 0; private set
    var startT = 0L; private set
    var lastT = 0L; private set
    var distanceM = 0.0; private set
    /** 3 s smoothed speed at the last point, km/h. */
    var speedKmh = 0.0; private set
    /** Best 3 s mean speed, km/h. */
    var top3Kmh = 0.0; private set
    var foilTimeS = 0.0; private set

    val durationS: Double get() = if (points < 2) 0.0 else (lastT - startT) / 1000.0
    val onFoil: Boolean get() = speedKmh >= foilThresholdKmh

    private var last: TrackPoint? = null
    private val window = ArrayDeque<Pair<Long, Double>>()

    fun add(p: TrackPoint) {
        val prev = last
        if (prev != null && p.t <= prev.t) return
        val step = if (prev == null) 0.0 else distanceM(prev, p)
        val dt = if (prev == null) 0.0 else (p.t - prev.t) / 1000.0
        val kmh = when {
            p.speedMs != null -> p.speedMs * 3.6
            dt > 0 -> step / dt * 3.6
            else -> 0.0
        }

        window.addLast(p.t to kmh)
        while (window.first().first <= p.t - SMOOTH_MS) window.removeFirst()
        speedKmh = window.sumOf { it.second } / window.size
        // A full window only: a single spike right after a gap isn't a top speed.
        if (window.last().first - window.first().first >= SMOOTH_MS - 1_500) top3Kmh = maxOf(top3Kmh, speedKmh)

        if (prev == null) startT = p.t
        else {
            distanceM += step
            // Gaps longer than a few seconds (lost fix) don't count as foiling time.
            if (onFoil && dt <= MAX_STEP_S) foilTimeS += dt
        }
        lastT = p.t
        last = p
        points++
    }

    companion object {
        private const val SMOOTH_MS = 3_000L
        private const val MAX_STEP_S = 5.0

        fun of(points: Iterable<TrackPoint>, category: Category) =
            LiveStats(category.foilThresholdKmh).apply { points.forEach(::add) }
    }
}
