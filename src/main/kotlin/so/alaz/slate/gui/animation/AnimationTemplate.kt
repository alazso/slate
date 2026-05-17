package so.alaz.slate.gui.animation

import so.alaz.slate.crate.Prize

interface AnimationTemplate {
    val id: String
    val totalTicks: Int
    val tickInterval: Int

    fun frame(tick: Int, prize: Prize, decoys: List<Prize>): AnimationFrame
}

