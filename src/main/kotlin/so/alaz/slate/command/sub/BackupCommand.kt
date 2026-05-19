package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import so.alaz.slate.admin.AdminActions
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.message.MessageKeys

object BackupCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("backup")
            .requires { it.sender.hasPermission(SlatePermissions.ADMIN_BACKUP) }
            .executes { context ->
                val sender = context.source.sender
                when (val result = AdminActions.backup()) {
                    is AdminActions.BackupResult.SqliteOnly ->
                        CommandMessages.chat(sender, MessageKeys.BACKUP_SQLITE_ONLY)
                    is AdminActions.BackupResult.DatabaseMissing ->
                        CommandMessages.chat(sender, MessageKeys.BACKUP_DB_MISSING)
                    is AdminActions.BackupResult.Created ->
                        CommandMessages.chat(sender, MessageKeys.BACKUP_CREATED, mapOf("file" to result.file.fileName))
                    is AdminActions.BackupResult.Failed -> {
                        CommandMessages.notInitialized(sender)
                        return@executes 0
                    }
                }
                Command.SINGLE_SUCCESS
            }
            .build()
}
