package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.crate.ReloadFileStatus
import so.alaz.slate.message.MessageKeys
import so.alaz.slate.probability.ProbabilityVariableService

object ReloadCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("reload")
            .requires { it.sender.hasPermission(SlatePermissions.ADMIN_RELOAD) }
            .executes { context ->
                val configResult = SlateAPI.configManager()?.reload()
                val summary = SlateAPI.crateManager()?.reload()
                if (summary == null) {
                    CommandMessages.notInitialized(context.source.sender)
                    return@executes 0
                }
                ProbabilityVariableService.clearCache()
                SlateAPI.messageService()?.reload(SlateAPI.configManager()?.current?.language ?: "en")
                SlateAPI.displayService()?.reloadDisplays()

                val failedFiles = summary.fileOutcomes
                    .filterValues { it.status == ReloadFileStatus.FAILED }
                    .keys
                    .take(3)
                    .joinToString(", ")
                val retainedFiles = summary.fileOutcomes
                    .count { it.value.status == ReloadFileStatus.RETAINED_LAST_GOOD }
                val configState = if (configResult?.isValid != false) "config=ok" else "config=retained-last-good"

                CommandMessages.chat(
                    context.source.sender,
                    MessageKeys.RELOAD_COMPLETE,
                    mapOf(
                        "updated" to summary.filesUpdated,
                        "retained" to retainedFiles,
                        "failed" to summary.filesFailed,
                        "removed" to summary.filesRemoved,
                        "activeCrates" to summary.cratesLoaded,
                        "configState" to configState,
                        "failedFiles" to failedFiles,
                    ),
                )
                Command.SINGLE_SUCCESS
            }
            .build()
}
