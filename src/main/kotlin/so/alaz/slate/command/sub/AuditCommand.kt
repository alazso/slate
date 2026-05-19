package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.launch
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.message.MessageKeys

object AuditCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("audit")
            .requires { it.sender.hasPermission(SlatePermissions.ADMIN_AUDIT) }
            .executes { context ->
                listRecent(context.source, 20)
            }
            .then(
                Commands.argument("limit", IntegerArgumentType.integer(1, 100))
                    .executes { context ->
                        listRecent(context.source, IntegerArgumentType.getInteger(context, "limit"))
                    },
            )
            .build()

    private fun listRecent(source: CommandSourceStack, limit: Int): Int {
        val storage = SlateAPI.storageService()
        val scope = SlateAPI.pluginScope()
        if (storage == null || scope == null) {
            CommandMessages.notInitialized(source.sender)
            return 0
        }
        CommandMessages.chat(source.sender, MessageKeys.AUDIT_LOADING)
        scope.launch {
            val rows = storage.listRecentOpenOperations(limit)
            CommandMessages.dispatchForSender(source.sender) {
                if (rows.isEmpty()) {
                    CommandMessages.chat(source.sender, MessageKeys.AUDIT_EMPTY)
                    return@dispatchForSender
                }
                CommandMessages.chat(source.sender, MessageKeys.AUDIT_HEADER, mapOf("count" to rows.size))
                rows.take(limit).forEach { op ->
                    CommandMessages.chat(
                        source.sender,
                        MessageKeys.AUDIT_ROW,
                        mapOf(
                            "operationId" to op.operationId.take(8),
                            "crate" to op.crateId,
                            "state" to op.state,
                            "prize" to (op.prizeId ?: "-"),
                            "error" to (op.errorMessage ?: "-"),
                        ),
                    )
                }
            }
        }
        return Command.SINGLE_SUCCESS
    }
}
