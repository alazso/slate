package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import java.nio.file.Files
import java.time.Instant
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.config.DatabaseType
import so.alaz.slate.message.MessageKeys

object BackupCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("backup")
            .requires { it.sender.hasPermission(SlatePermissions.ADMIN_BACKUP) }
            .executes { context ->
                val plugin = SlateAPI.plugin()
                val cfg = SlateAPI.configManager()?.current
                if (plugin == null || cfg == null) {
                    CommandMessages.notInitialized(context.source.sender)
                    return@executes 0
                }
                if (cfg.database.type != DatabaseType.SQLITE) {
                    CommandMessages.chat(context.source.sender, MessageKeys.BACKUP_SQLITE_ONLY)
                    return@executes Command.SINGLE_SUCCESS
                }
                val db = plugin.dataFolder.toPath().resolve("slate.db")
                if (!Files.exists(db)) {
                    CommandMessages.chat(context.source.sender, MessageKeys.BACKUP_DB_MISSING)
                    return@executes Command.SINGLE_SUCCESS
                }
                val backupsDir = plugin.dataFolder.toPath().resolve("backups")
                Files.createDirectories(backupsDir)
                val target = backupsDir.resolve("slate-${Instant.now().epochSecond}.db.bak")
                Files.copy(db, target)
                CommandMessages.chat(context.source.sender, MessageKeys.BACKUP_CREATED, mapOf("file" to target.fileName))
                Command.SINGLE_SUCCESS
            }
            .build()
}
