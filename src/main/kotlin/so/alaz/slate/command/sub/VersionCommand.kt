package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.message.MessageKeys

object VersionCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("version")
            .requires { it.sender.hasPermission(SlatePermissions.ADMIN_VERSION) }
            .executes { context ->
                val plugin = SlateAPI.plugin()
                if (plugin == null) {
                    CommandMessages.notInitialized(context.source.sender)
                    return@executes 0
                }
                val version = plugin.pluginMeta.version
                CommandMessages.chat(context.source.sender, MessageKeys.VERSION, mapOf("version" to version))
                Command.SINGLE_SUCCESS
            }
            .build()
}
