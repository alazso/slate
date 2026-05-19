package so.alaz.slate.gui.animation

import so.alaz.slate.crate.Prize

interface AnimationTemplate {
    val id: String
    val totalTicks: Int
    val tickInterval: Int

    fun frame(tick: Int, prize: Prize, decoys: List<Prize>): AnimationFrame

    /**
     * Whether this tick should produce a visible frame update.
     * During deceleration, most ticks return false so the reel appears to slow down.
     * During fast spin, returns true every [tickInterval] ticks (or every tick when interval is 1).
     */
    fun shouldRenderOnTick(tick: Int): Boolean
}
