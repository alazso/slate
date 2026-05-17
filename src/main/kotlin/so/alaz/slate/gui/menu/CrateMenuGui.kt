package so.alaz.slate.gui.menu

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CrateVisibility
import so.alaz.slate.gui.GuiClickContext
import so.alaz.slate.gui.SlateGui
import so.alaz.slate.gui.StaticSlot
import so.alaz.slate.gui.preview.PreviewGui
import so.alaz.slate.util.ComponentUtil

class CrateMenuGui(
    private val player: Player,
) : SlateGui(
    title = "Crates",
    rows = SlateAPI.configManager()?.current?.gui?.menu?.rows ?: 4,
) {
    override suspend fun onBeforeOpen(player: Player) {
        val cfg = SlateAPI.configManager()?.current?.gui?.menu
        val borderMaterial = cfg?.borderMaterial?.let(Material::matchMaterial) ?: Material.GRAY_STAINED_GLASS_PANE
        fillBorder(borderMaterial)
        val crateManager = SlateAPI.crateManager() ?: return
        val eligibility = SlateAPI.crateEligibilityService() ?: return
        val keyService = SlateAPI.keyService()
        val slots = cfg?.crateSlots ?: emptyList()
        val crates = crateManager.all()
            .filter { eligibility.visibilityFor(player, it) == CrateVisibility.VISIBLE }
            .sortedBy { it.id }
        crates.take(slots.size).forEachIndexed { index, crate ->
            val slotIndex = slots[index]
            slot(slotIndex, buildCrateSlot(crate, keyService?.getVirtualKeys(player.uniqueId, crate.id) ?: 0))
        }
    }

    private fun fillBorder(material: Material) {
        val size = rows * 9
        (0 until 9).forEach { slot(it, StaticSlot(ItemStack(material))) }
        ((size - 9) until size).forEach { slot(it, StaticSlot(ItemStack(material))) }
        for (y in 1 until rows - 1) {
            slot(y * 9, StaticSlot(ItemStack(material)))
            slot((y * 9) + 8, StaticSlot(ItemStack(material)))
        }
    }

    private fun buildCrateSlot(crate: CrateDefinition, keys: Int): StaticSlot {
        val stack = ItemStack(crate.keyDefinition.material)
        stack.editMeta { meta ->
            meta.displayName(ComponentUtil.parse(crate.name))
            meta.lore(
                listOf(
                    "<gray>ID: ${crate.id}</gray>",
                    "<gray>Keys: <yellow>$keys</yellow></gray>",
                    "<gray>Left click: preview</gray>",
                    "<gray>Right click: open</gray>",
                ).map(ComponentUtil::parse),
            )
        }
        return StaticSlot(stack) { ctx -> handleCrateClick(ctx, crate) }
    }

    private suspend fun handleCrateClick(context: GuiClickContext, crate: CrateDefinition) {
        val guiService = SlateAPI.guiService() ?: return
        when (context.clickType) {
            org.bukkit.event.inventory.ClickType.LEFT -> {
                guiService.openGui(context.player, PreviewGui(crate))
            }
            org.bukkit.event.inventory.ClickType.RIGHT -> {
                if (!context.player.hasPermission(SlatePermissions.PLAYER_OPEN)) return
                SlateAPI.openService()?.openById(context.player, crate.id, 1)
            }
            else -> Unit
        }
    }
}

