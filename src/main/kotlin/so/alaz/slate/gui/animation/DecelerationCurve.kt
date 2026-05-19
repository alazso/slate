package so.alaz.slate.gui.animation

/**
 * Builds render tick indices for a deceleration phase where gaps between frames
 * widen toward the start and narrow toward [endTick] (the last frame of the phase).
 *
 * Example: [slowSpin] with endTick=80 and cuts=9 → {80, 71, 63, 56, 50, 45, 41, 38, 36}
 */
fun slowSpin(endTick: Int, cuts: Int): Set<Int> {
    if (cuts <= 0 || endTick < 0) return emptySet()
    val result = linkedSetOf<Int>()
    var current = endTick
    var step = cuts
    repeat(cuts) {
        if (current >= 0) {
            result.add(current)
        }
        if (step <= 0) return@repeat
        current -= step
        step--
    }
    return result
}

fun renderIndexAt(tick: Int, shouldRender: (Int) -> Boolean): Int =
    (0 until tick).count(shouldRender)

fun fastPhaseRender(tick: Int, tickInterval: Int): Boolean =
    tick % tickInterval.coerceAtLeast(1) == 0
