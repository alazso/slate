package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.config.DatabaseType
import so.alaz.slate.message.MessageKeys

object RestoreCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("restore")
            .requires { it.sender.hasPermission(SlatePermissions.ADMIN_RESTORE) }
            .then(
                Commands.argument("backupFile", StringArgumentType.greedyString())
                    .executes { context ->
                        val plugin = SlateAPI.plugin()
                        val cfg = SlateAPI.configManager()?.current
                        if (plugin == null || cfg == null) {
                            CommandMessages.notInitialized(context.source.sender)
                            return@executes 0
                        }
                        if (cfg.database.type != DatabaseType.SQLITE) {
                            CommandMessages.chat(context.source.sender, MessageKeys.RESTORE_SQLITE_ONLY)
                            return@executes Command.SINGLE_SUCCESS
                        }
                        val backupFile = StringArgumentType.getString(context, "backupFile")
                        val backup = plugin.dataFolder.toPath().resolve("backups").resolve(backupFile)
                        val db = plugin.dataFolder.toPath().resolve("slate.db")
                        if (!Files.exists(backup)) {
                            CommandMessages.chat(context.source.sender, MessageKeys.RESTORE_BACKUP_MISSING, mapOf("file" to backupFile))
                            return@executes 0
                        }
                        Files.copy(backup, db, StandardCopyOption.REPLACE_EXISTING)
                        CommandMessages.chat(
                            context.source.sender,
                            MessageKeys.RESTORE_COMPLETE,
                            mapOf("file" to backupFile),
                        )
                        Command.SINGLE_SUCCESS
                    },
            )
            .build()
}
