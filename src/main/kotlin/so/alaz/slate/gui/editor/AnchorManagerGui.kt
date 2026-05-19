package so.alaz.slate.gui.editor

import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.sub.CommandMessages
import so.alaz.slate.crate.anchor.CrateAnchor
import so.alaz.slate.gui.GuiClickContext
import so.alaz.slate.gui.GuiSession
import so.alaz.slate.gui.GuiSlot
import so.alaz.slate.gui.GuiSounds
import so.alaz.slate.gui.GuiText
import so.alaz.slate.gui.SlateGui
import so.alaz.slate.gui.StaticSlot
import so.alaz.slate.message.MessageKeys

/**
 * Admin Anchor Manager — GUI-REFACTOR.md §14.
 *
 *   - Slot 4: refresh tile (warms the anchor cache from storage)
 *   - Slots 10..34: anchor tiles
 *      - Left-click: teleport to the anchor (must be online + scheduler available)
 *      - Shift-Right-click: remove the anchor
 *   - Slots 48/49/50: prev/page/next
 *   - Slot 53: back
 */
class AnchorManagerGui(
    private val backFactory: ((Player) -> SlateGui)? = { AdminPanelGui() },
) : SlateGui(
    title = GuiText.raw(MessageKeys.GUI_ANCHOR_MANAGER_TITLE),
    rows = 6,
) {
    private var page: Int = 0
    private var anchors: List<CrateAnchor> = emptyList()

    override suspend fun onBeforeOpen(player: Player) {
        val border = StaticSlot(blank())
        fillBorder(border)

        val service = SlateAPI.anchorService()
        anchors = service?.allAnchors().orEmpty().sortedWith(compareBy({ it.crateId }, { it.worldKey }, { it.x }, { it.y }, { it.z }))
        val totalPages = ((anchors.size + GRID_SLOTS.size - 1) / GRID_SLOTS.size).coerceAtLeast(1)
        if (page >= totalPages) page = totalPages - 1
        if (page < 0) page = 0

        slot(4, refreshTile())
        if (anchors.isEmpty()) {
            slot(22, emptyTile())
        } else {
            val pageItems = anchors.drop(page * GRID_SLOTS.size).take(GRID_SLOTS.size)
            for ((index, anchor) in pageItems.withIndex()) {
                slot(GRID_SLOTS[index], anchorTile(anchor))
            }
        }

        slot(48, prevTile(totalPages))
        slot(49, pageIndicator(totalPages, anchors.size))
        slot(50, nextTile(totalPages))
        slot(53, backTile())
    }

    override suspend fun onClick(context: GuiClickContext, session: GuiSession, slot: GuiSlot?) {
        super.onClick(context, session, slot)
        if (session.closeRequested) return
        val scheduler = SlateAPI.scheduler() ?: return
        refresh(context.player, scheduler, session.inventory)
    }

    private fun fillBorder(border: StaticSlot) {
        val sz = rows * 9
        for (i in 0 until 9) slot(i, border)
        for (i in (sz - 9) until sz) slot(i, border)
        for (y in 1 until rows - 1) {
            slot(y * 9, border)
            slot((y * 9) + 8, border)
        }
    }

    private fun blank(): ItemStack {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        item.editMeta { meta -> meta.displayName(net.kyori.adventure.text.Component.empty()) }
        return item
    }

    private fun anchorTile(anchor: CrateAnchor): StaticSlot {
        val item = ItemStack(Material.LODESTONE)
        val placeholders = mapOf(
            "crate" to anchor.crateId,
            "world" to anchor.worldKey,
            "x" to anchor.x,
            "y" to anchor.y,
            "z" to anchor.z,
        )
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_ANCHOR_MANAGER_TILE_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_ANCHOR_MANAGER_TILE_LORE, placeholders))
        }
        return StaticSlot(item) { context ->
            handleAnchorClick(context, anchor)
        }
    }

    private suspend fun handleAnchorClick(context: GuiClickContext, anchor: CrateAnchor) {
        val service = SlateAPI.anchorService() ?: return
        val scheduler = SlateAPI.scheduler() ?: return
        val scope = SlateAPI.pluginScope() ?: return
        val world = Bukkit.getWorld(NamespacedKey.fromString(anchor.worldKey) ?: return)
        when (context.clickType) {
            ClickType.SHIFT_RIGHT -> {
                if (world == null) {
                    GuiSounds.denied(context.player)
                    return
                }
                val location = Location(world, anchor.x.toDouble(), anchor.y.toDouble(), anchor.z.toDouble())
                scope.launch {
                    val removed = runCatching { service.removeAnchor(location) }.getOrDefault(false)
                    CommandMessages.dispatchForSender(context.player) {
                        if (removed) {
                            GuiSounds.success(context.player)
                            SlateAPI.messageService()?.hotbar(
                                context.player,
                                MessageKeys.GUI_ANCHOR_MANAGER_REMOVE_HOTBAR,
                                mapOf("crate" to anchor.crateId),
                            )
                        } else {
                            GuiSounds.denied(context.player)
                        }
                    }
                }
            }
            else -> {
                if (world == null) {
                    GuiSounds.denied(context.player)
                    return
                }
                val location = Location(
                    world,
                    anchor.x.toDouble() + 0.5,
                    anchor.y.toDouble() + 1.0,
                    anchor.z.toDouble() + 0.5,
                )
                GuiSounds.navigate(context.player)
                scheduler.runForPlayerEntity(context.player) {
                    context.player.teleport(location)
                    SlateAPI.messageService()?.hotbar(
                        context.player,
                        MessageKeys.GUI_ANCHOR_MANAGER_TP_HOTBAR,
                        mapOf("crate" to anchor.crateId),
                    )
                }
                context.session.closeRequested = true
            }
        }
    }

    private fun refreshTile(): StaticSlot {
        val item = ItemStack(Material.ENDER_EYE)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_ANCHOR_MANAGER_REFRESH_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_ANCHOR_MANAGER_REFRESH_LORE))
        }
        return StaticSlot(item) { context ->
            val service = SlateAPI.anchorService() ?: return@StaticSlot
            val scope = SlateAPI.pluginScope() ?: return@StaticSlot
            scope.launch {
                runCatching { service.warmCache() }
                CommandMessages.dispatchForSender(context.player) {
                    GuiSounds.success(context.player)
                    CommandMessages.chat(
                        context.player,
                        MessageKeys.GUI_ANCHOR_MANAGER_REFRESH_RESULT,
                        mapOf("count" to anchors.size),
                    )
                }
            }
        }
    }

    private fun emptyTile(): StaticSlot {
        val item = ItemStack(Material.BARRIER)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_ANCHOR_MANAGER_EMPTY_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_ANCHOR_MANAGER_EMPTY_LORE))
        }
        return StaticSlot(item)
    }

    private fun prevTile(totalPages: Int): StaticSlot {
        val item = ItemStack(if (page > 0) Material.ARROW else Material.GRAY_DYE)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CONTROL_PREV_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_CONTROL_PREV_LORE))
        }
        return StaticSlot(item) { context ->
            if (page > 0) {
                page -= 1
                GuiSounds.pageTurn(context.player)
            } else {
                GuiSounds.denied(context.player)
            }
        }
    }

    private fun nextTile(totalPages: Int): StaticSlot {
        val item = ItemStack(if (page + 1 < totalPages) Material.ARROW else Material.GRAY_DYE)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CONTROL_NEXT_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_CONTROL_NEXT_LORE))
        }
        return StaticSlot(item) { context ->
            if (page + 1 < totalPages) {
                page += 1
                GuiSounds.pageTurn(context.player)
            } else {
                GuiSounds.denied(context.player)
            }
        }
    }

    private fun pageIndicator(totalPages: Int, count: Int): StaticSlot {
        val item = ItemStack(Material.PAPER)
        val placeholders = mapOf("page" to (page + 1), "total" to totalPages, "count" to count)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CONTROL_PAGE_INDICATOR_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_CONTROL_PAGE_INDICATOR_LORE, placeholders))
        }
        return StaticSlot(item)
    }

    private fun backTile(): StaticSlot {
        val factory = backFactory
        val item = ItemStack(Material.ARROW)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CONTROL_BACK_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_CONTROL_BACK_LORE))
        }
        return StaticSlot(item) { context ->
            GuiSounds.navigate(context.player)
            if (factory != null) SlateAPI.guiService()?.openGui(context.player, factory(context.player))
            else context.session.closeRequested = true
        }
    }

    companion object {
        private val GRID_SLOTS = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43,
        )
    }
}
