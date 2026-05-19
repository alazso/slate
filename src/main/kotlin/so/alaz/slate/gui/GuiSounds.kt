package so.alaz.slate.gui

import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player

/**
 * Centralised sound feedback for GUI interactions.
 *
 * Volumes intentionally kept quiet (0.4) so menu spam doesn't drown out world audio.
 * Pitch differentiates intent (navigation vs success vs denied).
 */
object GuiSounds {
    private const val VOLUME = 0.4f

    fun click(player: Player) {
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, VOLUME, 1.0f)
    }

    fun navigate(player: Player) {
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, VOLUME, 1.4f)
    }

    fun pageTurn(player: Player) {
        player.playSound(player.location, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.MASTER, VOLUME, 1.0f)
    }

    fun success(player: Player) {
        player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, VOLUME, 1.2f)
    }

    fun denied(player: Player) {
        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, VOLUME, 1.0f)
    }
}
