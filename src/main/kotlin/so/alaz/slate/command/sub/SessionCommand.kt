package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.message.MessageKeys

object SessionCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("session")
            .requires { it.sender.hasPermission(SlatePermissions.ADMIN_SESSION) }
            .executes { context ->
                CommandMessages.chat(context.source.sender, MessageKeys.SESSION_SUMMARY)
                Command.SINGLE_SUCCESS
            }
            .build()
}
