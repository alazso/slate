package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import so.alaz.slate.admin.AdminActions
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.message.MessageKeys

object ReloadCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("reload")
            .requires { it.sender.hasPermission(SlatePermissions.ADMIN_RELOAD) }
            .executes { context ->
                val result = AdminActions.reload()
                val summary = result.crateSummary
                if (summary == null) {
                    CommandMessages.notInitialized(context.source.sender)
                    return@executes 0
                }
                val configState = if (result.configValid) "config=ok" else "config=retained-last-good"
                CommandMessages.chat(
                    context.source.sender,
                    MessageKeys.RELOAD_COMPLETE,
                    mapOf(
                        "updated" to summary.filesUpdated,
                        "retained" to result.retained,
                        "failed" to summary.filesFailed,
                        "removed" to summary.filesRemoved,
                        "activeCrates" to summary.cratesLoaded,
                        "configState" to configState,
                        "failedFiles" to result.failedFiles.take(3).joinToString(", "),
                    ),
                )
                Command.SINGLE_SUCCESS
            }
            .build()
}
