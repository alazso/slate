package so.alaz.slate.gui.editor

import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import so.alaz.slate.item.ItemFactory
import so.alaz.slate.util.ComponentUtil

internal object KeyEditorPreviewResolver {
    fun buildPreview(draft: KeyEditorDraft, player: Player): ItemStack {
        val base = ItemFactory.buildVisual(
            request = draft.toBuildRequest(includeTextOverlay = false),
            fallbackMaterial = draft.material.takeIf { it != Material.AIR } ?: Material.TRIPWIRE_HOOK,
        )
        base.editMeta { meta ->
            if (draft.name.isNotBlank()) {
                val resolved = resolvePapi(draft.name, player)
                meta.displayName(ComponentUtil.parse(resolved))
            }
            meta.lore(
                draft.lore
                    .map { resolvePapi(it, player) }
                    .map(ComponentUtil::parse),
            )
        }
        return base
    }

    private fun resolvePapi(input: String, player: OfflinePlayer?): String {
        val method = runCatching {
            val clazz = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
            clazz.getMethod("setPlaceholders", OfflinePlayer::class.java, String::class.java)
        }.getOrNull() ?: return input
        return runCatching { method.invoke(null, player, input) as? String ?: input }
            .getOrDefault(input)
    }
}
