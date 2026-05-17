package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.message.MessageKeys

object HealthCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("health")
            .requires { it.sender.hasPermission(SlatePermissions.ADMIN_HEALTH) }
            .executes { context ->
                val plugin = SlateAPI.plugin()
                if (plugin == null) {
                    CommandMessages.notInitialized(context.source.sender)
                    return@executes 0
                }
                val crates = SlateAPI.crateManager()?.all()?.size ?: 0
                CommandMessages.chat(
                    context.source.sender,
                    MessageKeys.HEALTH_SUMMARY,
                    mapOf(
                        "crates" to crates,
                        "debug" to plugin.configManager.current.debug,
                    ),
                )
                Command.SINGLE_SUCCESS
            }
            .build()
}
