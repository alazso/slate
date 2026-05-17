package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.crate.CrateVisibility
import so.alaz.slate.message.MessageKeys

object ListCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("list")
            .requires { it.sender.hasPermission(SlatePermissions.PLAYER_LIST) }
            .executes { context ->
                val crateManager = SlateAPI.crateManager()
                val eligibilityService = SlateAPI.crateEligibilityService()
                if (crateManager == null || eligibilityService == null) {
                    CommandMessages.notInitialized(context.source.sender)
                    return@executes 0
                }

                val crates = crateManager.all()
                    .filter { eligibilityService.visibilityFor(context.source.sender, it) == CrateVisibility.VISIBLE }
                    .sortedBy { it.id }
                if (crates.isEmpty()) {
                    CommandMessages.chat(context.source.sender, MessageKeys.LIST_EMPTY)
                    return@executes Command.SINGLE_SUCCESS
                }

                val list = crates.joinToString(", ") { it.id }
                CommandMessages.chat(context.source.sender, MessageKeys.LIST_LOADED, mapOf("count" to crates.size, "list" to list))
                Command.SINGLE_SUCCESS
            }
            .build()
}
