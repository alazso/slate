package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.message.MessageKeys

object MigrateCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("migrate")
            .requires { it.sender.hasPermission(SlatePermissions.ADMIN_MIGRATE) }
            .executes { context ->
                val plugin = SlateAPI.plugin()
                if (plugin == null) {
                    CommandMessages.notInitialized(context.source.sender)
                    return@executes 0
                }
                CommandMessages.chat(context.source.sender, MessageKeys.MIGRATE_DRY_RUN)
                Command.SINGLE_SUCCESS
            }
            .build()
}
