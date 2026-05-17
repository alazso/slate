package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.launch
import org.bukkit.entity.Player
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.gui.menu.CrateMenuGui
import so.alaz.slate.message.MessageKeys

object CratesCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("crates")
            .requires { it.sender.hasPermission(SlatePermissions.PLAYER_MENU) }
            .executes { context ->
                CommandMessages.chat(context.source.sender, MessageKeys.HELP)
                Command.SINGLE_SUCCESS
            }
            .then(
                Commands.literal("menu")
                    .requires { it.sender.hasPermission(SlatePermissions.PLAYER_MENU) }
                    .executes { context ->
                        val player = context.source.sender as? Player ?: return@executes 0
                        val guiService = SlateAPI.guiService()
                        val scope = SlateAPI.pluginScope()
                        if (guiService == null || scope == null) {
                            CommandMessages.notInitialized(player)
                            return@executes 0
                        }
                        scope.launch {
                            guiService.openGui(player, CrateMenuGui(player))
                        }
                        Command.SINGLE_SUCCESS
                    },
            )
            .then(ListCommand.node())
            .then(PreviewCommand.node())
            .then(OpenCommand.node())
            .then(ClaimCommand.node())
            .then(
                Commands.literal("locate")
                    .requires { it.sender.hasPermission(SlatePermissions.PLAYER_LOCATE) }
                    .executes { context ->
                        locateNearest(context.source.sender as? Player ?: return@executes 0)
                        Command.SINGLE_SUCCESS
                    },
            )
            .then(
                Commands.literal("history")
                    .requires { it.sender.hasPermission(SlatePermissions.PLAYER_HISTORY) }
                    .executes { context ->
                        history(context.source.sender as? Player ?: return@executes 0, 10)
                        Command.SINGLE_SUCCESS
                    }
                    .then(
                        Commands.argument("crate", StringArgumentType.word())
                            .suggests { context, builder ->
                                CommandSuggestions.suggestCrateIds(context.source, builder)
                            }
                            .executes { context ->
                                history(
                                    context.source.sender as? Player ?: return@executes 0,
                                    25,
                                    StringArgumentType.getString(context, "crate"),
                                )
                                Command.SINGLE_SUCCESS
                            },
                    ),
            )
            .then(
                Commands.literal("pity")
                    .requires { it.sender.hasPermission(SlatePermissions.PLAYER_PITY) }
                    .executes { context ->
                        pity(context.source.sender as? Player ?: return@executes 0, null)
                        Command.SINGLE_SUCCESS
                    }
                    .then(
                        Commands.argument("crate", StringArgumentType.word())
                            .suggests { context, builder ->
                                CommandSuggestions.suggestCrateIds(context.source, builder)
                            }
                            .executes { context ->
                                pity(
                                    context.source.sender as? Player ?: return@executes 0,
                                    StringArgumentType.getString(context, "crate"),
                                )
                                Command.SINGLE_SUCCESS
                            },
                    ),
            )
            .build()

    private fun locateNearest(player: Player) {
        val anchorService = SlateAPI.anchorService()
        val pluginScope = SlateAPI.pluginScope()
        if (anchorService == null || pluginScope == null) {
            CommandMessages.notInitialized(player)
            return
        }
        pluginScope.launch {
            val anchors = anchorService.allAnchors()
            val nearest = anchors
                .filter { it.worldKey == player.world.key.asString() }
                .minByOrNull { anchor ->
                    val dx = player.location.blockX - anchor.x
                    val dy = player.location.blockY - anchor.y
                    val dz = player.location.blockZ - anchor.z
                    (dx * dx) + (dy * dy) + (dz * dz)
                }
            val text = if (nearest == null) {
                null
            } else {
                nearest
            }
            val plugin = SlateAPI.plugin()
            if (plugin != null) {
                plugin.server.scheduler.runTask(
                    plugin,
                    Runnable {
                        if (text == null) {
                            CommandMessages.chat(player, MessageKeys.CRATES_LOCATE_NONE)
                        } else {
                            CommandMessages.chat(
                                player,
                                MessageKeys.CRATES_LOCATE_NEAREST,
                                mapOf("crate" to text.crateId, "x" to text.x, "y" to text.y, "z" to text.z),
                            )
                        }
                    },
                )
            }
        }
    }

    private fun history(player: Player, limit: Int, crateIdFilter: String? = null) {
        val storage = SlateAPI.storageService()
        val pluginScope = SlateAPI.pluginScope()
        if (storage == null || pluginScope == null) {
            CommandMessages.notInitialized(player)
            return
        }
        pluginScope.launch {
            val entries = storage.listOpenHistory(player.uniqueId, limit)
                .filter { crateIdFilter == null || it.crateId == crateIdFilter }
            val lines = if (entries.isEmpty()) {
                emptyList()
            } else {
                entries.take(10).map {
                    mapOf("crate" to it.crateId, "prize" to it.prizeName, "keyType" to it.keyType)
                }
            }
            val plugin = SlateAPI.plugin()
            if (plugin != null) {
                plugin.server.scheduler.runTask(
                    plugin,
                    Runnable {
                        if (lines.isEmpty()) {
                            CommandMessages.chat(player, MessageKeys.CRATES_HISTORY_EMPTY)
                        } else {
                            lines.forEach { CommandMessages.chat(player, MessageKeys.CRATES_HISTORY_LINE, it) }
                        }
                    },
                )
            }
        }
    }

    private fun pity(player: Player, crateId: String?) {
        val storage = SlateAPI.storageService()
        val crateManager = SlateAPI.crateManager()
        val pluginScope = SlateAPI.pluginScope()
        if (storage == null || crateManager == null || pluginScope == null) {
            CommandMessages.notInitialized(player)
            return
        }
        pluginScope.launch {
            val crateIds = if (crateId.isNullOrBlank()) crateManager.all().map { it.id }.sorted() else listOf(crateId)
            val lines = crateIds.map { id ->
                val count = storage.getPityCount(player.uniqueId, id)
                mapOf("crate" to id, "count" to count)
            }
            val plugin = SlateAPI.plugin()
            if (plugin != null) {
                plugin.server.scheduler.runTask(
                    plugin,
                    Runnable {
                        if (lines.isEmpty()) {
                            CommandMessages.chat(player, MessageKeys.CRATES_PITY_EMPTY)
                        } else {
                            lines.forEach { CommandMessages.chat(player, MessageKeys.CRATES_PITY_LINE, it) }
                        }
                    },
                )
            }
        }
    }
}
