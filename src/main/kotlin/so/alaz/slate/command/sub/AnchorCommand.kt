package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.message.MessageKeys

object AnchorCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("anchor")
            .then(
                Commands.literal("set")
                    .requires { it.sender.hasPermission(SlatePermissions.ADMIN_ANCHOR_SET) }
                    .then(
                        Commands.argument("crate", StringArgumentType.word())
                            .suggests { context, builder ->
                                CommandSuggestions.suggestCrateIds(context.source, builder)
                            }
                            .executes { context ->
                                val sender = context.source.sender as? Player
                                if (sender == null) {
                                    CommandMessages.chat(context.source.sender, MessageKeys.COMMON_ONLY_PLAYERS, mapOf("command" to "/sla anchor set"))
                                    return@executes 0
                                }
                                val block = sender.getTargetBlockExact(6)
                                if (block == null) {
                                    CommandMessages.chat(sender, MessageKeys.ANCHOR_LOOK_AT_BLOCK)
                                    return@executes 0
                                }
                                val crateId = StringArgumentType.getString(context, "crate")
                                val crate = SlateAPI.crateManager()?.byId(crateId)
                                if (crate == null) {
                                    CommandMessages.chat(sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
                                    return@executes 0
                                }
                                val anchorService = SlateAPI.anchorService()
                                val scope = SlateAPI.pluginScope()
                                if (anchorService == null || scope == null) {
                                    CommandMessages.notInitialized(sender)
                                    return@executes 0
                                }
                                scope.launch {
                                    anchorService.addOrReplaceAnchor(crate.id, block.location)
                                    SlateAPI.displayService()?.reloadDisplays()
                                }
                                CommandMessages.chat(
                                    sender,
                                    MessageKeys.ANCHOR_SET_SUCCESS,
                                    mapOf(
                                        "crate" to crate.id,
                                        "x" to block.location.blockX,
                                        "y" to block.location.blockY,
                                        "z" to block.location.blockZ,
                                    ),
                                )
                                Command.SINGLE_SUCCESS
                            },
                    ),
            )
            .then(
                Commands.literal("remove")
                    .requires { it.sender.hasPermission(SlatePermissions.ADMIN_ANCHOR_REMOVE) }
                    .executes { context ->
                        val sender = context.source.sender as? Player
                        if (sender == null) {
                            CommandMessages.chat(context.source.sender, MessageKeys.COMMON_ONLY_PLAYERS, mapOf("command" to "/sla anchor remove"))
                            return@executes 0
                        }
                        val block = sender.getTargetBlockExact(6)
                        if (block == null) {
                            CommandMessages.chat(sender, MessageKeys.ANCHOR_LOOK_AT_ANCHORED)
                            return@executes 0
                        }
                        val anchorService = SlateAPI.anchorService()
                        val scope = SlateAPI.pluginScope()
                        if (anchorService == null || scope == null) {
                            CommandMessages.notInitialized(sender)
                            return@executes 0
                        }
                        val removedCrateId = anchorService.crateIdAtCached(block.location)
                        scope.launch {
                            val removed = anchorService.removeAnchor(block.location)
                            if (removed) {
                                removedCrateId?.let { crateId ->
                                    SlateAPI.displayService()?.removeDisplayGroupAt(
                                        ownerType = "crate",
                                        ownerId = crateId,
                                        location = block.location.clone().add(0.5, 0.5, 0.5),
                                    )
                                }
                                SlateAPI.displayService()?.reloadDisplays()
                            }
                            val plugin = SlateAPI.plugin()
                            if (plugin != null) {
                                plugin.server.scheduler.runTask(
                                    plugin,
                                    Runnable {
                                        if (removed) CommandMessages.chat(sender, MessageKeys.ANCHOR_REMOVE_SUCCESS)
                                        else CommandMessages.chat(sender, MessageKeys.ANCHOR_REMOVE_NONE)
                                    },
                                )
                            }
                        }
                        Command.SINGLE_SUCCESS
                    },
            )
            .then(
                Commands.literal("list")
                    .requires { it.sender.hasPermission(SlatePermissions.ADMIN_ANCHOR_LIST) }
                    .executes { context ->
                        val anchorService = SlateAPI.anchorService()
                        val scope = SlateAPI.pluginScope()
                        val plugin = SlateAPI.plugin()
                        if (anchorService == null || scope == null || plugin == null) {
                            CommandMessages.notInitialized(context.source.sender)
                            return@executes 0
                        }
                        val sender = context.source.sender
                        scope.launch {
                            val anchors = anchorService.allAnchors()
                            plugin.server.scheduler.runTask(
                                plugin,
                                Runnable {
                                    if (anchors.isEmpty()) {
                                        CommandMessages.chat(sender, MessageKeys.ANCHOR_LIST_EMPTY)
                                    } else {
                                        val text = anchors.take(8).joinToString(" | ") { "${it.crateId}@${it.worldKey}:${it.x},${it.y},${it.z}" }
                                        CommandMessages.chat(sender, MessageKeys.ANCHOR_LIST, mapOf("count" to anchors.size, "text" to text))
                                    }
                                },
                            )
                        }
                        Command.SINGLE_SUCCESS
                    },
            )
            .then(
                Commands.literal("tp")
                    .requires { it.sender.hasPermission(SlatePermissions.ADMIN_ANCHOR_TP) }
                    .then(
                        Commands.argument("crate", StringArgumentType.word())
                            .suggests { context, builder ->
                                CommandSuggestions.suggestCrateIds(context.source, builder)
                            }
                            .executes { context ->
                                teleportToAnchor(context.source, StringArgumentType.getString(context, "crate"))
                            },
                    ),
            )
            .then(
                Commands.literal("scan")
                    .requires { it.sender.hasPermission(SlatePermissions.ADMIN_ANCHOR_SCAN) }
                    .executes { context ->
                        CommandMessages.chat(context.source.sender, MessageKeys.COMMON_NOT_IMPLEMENTED, mapOf("feature" to "/sla anchor scan"))
                        Command.SINGLE_SUCCESS
                    },
            )
            .then(
                Commands.literal("refresh")
                    .requires { it.sender.hasPermission(SlatePermissions.ADMIN_ANCHOR_REFRESH) }
                    .executes { context ->
                        refreshAnchors(context.source)
                    },
            )
            .then(
                Commands.literal("repair")
                    .requires { it.sender.hasPermission(SlatePermissions.ADMIN_ANCHOR_REPAIR) }
                    .executes { context ->
                        CommandMessages.chat(context.source.sender, MessageKeys.COMMON_NOT_IMPLEMENTED, mapOf("feature" to "/sla anchor repair"))
                        Command.SINGLE_SUCCESS
                    },
            )
            .build()

    private fun teleportToAnchor(source: CommandSourceStack, crateId: String): Int {
        val player = source.sender as? Player
        if (player == null) {
            CommandMessages.chat(source.sender, MessageKeys.COMMON_ONLY_PLAYERS, mapOf("command" to "/sla anchor tp"))
            return 0
        }
        val anchorService = SlateAPI.anchorService()
        val crateManager = SlateAPI.crateManager()
        val scope = SlateAPI.pluginScope()
        val plugin = SlateAPI.plugin()
        if (anchorService == null || crateManager == null || scope == null || plugin == null) {
            CommandMessages.notInitialized(player)
            return 0
        }
        if (crateManager.byId(crateId) == null) {
            CommandMessages.chat(player, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
            return 0
        }

        scope.launch {
            val anchor = anchorService.allAnchors().firstOrNull { it.crateId == crateId }
            plugin.server.scheduler.runTask(
                plugin,
                Runnable {
                    if (anchor == null) {
                        CommandMessages.chat(player, MessageKeys.ANCHOR_TP_NOT_FOUND, mapOf("crate" to crateId))
                        return@Runnable
                    }
                    val worldKey = NamespacedKey.fromString(anchor.worldKey)
                    val world = worldKey?.let(Bukkit::getWorld)
                    if (world == null) {
                        CommandMessages.chat(player, MessageKeys.ANCHOR_TP_WORLD_UNLOADED, mapOf("world" to anchor.worldKey))
                        return@Runnable
                    }
                    val target = Location(
                        world,
                        anchor.x + 0.5,
                        anchor.y + 1.0,
                        anchor.z + 0.5,
                        player.location.yaw,
                        player.location.pitch,
                    )
                    player.teleport(target)
                    CommandMessages.chat(
                        player,
                        MessageKeys.ANCHOR_TP_SUCCESS,
                        mapOf("crate" to crateId, "x" to anchor.x, "y" to anchor.y, "z" to anchor.z),
                    )
                },
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun refreshAnchors(source: CommandSourceStack): Int {
        val anchorService = SlateAPI.anchorService()
        val scope = SlateAPI.pluginScope()
        val plugin = SlateAPI.plugin()
        if (anchorService == null || scope == null || plugin == null) {
            CommandMessages.notInitialized(source.sender)
            return 0
        }
        val sender = source.sender
        scope.launch {
            anchorService.warmCache()
            val count = anchorService.allAnchors().size
            plugin.server.scheduler.runTask(
                plugin,
                Runnable {
                    CommandMessages.chat(sender, MessageKeys.ANCHOR_REFRESH_COMPLETE, mapOf("count" to count))
                },
            )
        }
        return Command.SINGLE_SUCCESS
    }
}
