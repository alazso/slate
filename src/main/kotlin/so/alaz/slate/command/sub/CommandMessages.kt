package so.alaz.slate.command.sub

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.message.MessageKeys
import so.alaz.slate.opening.FailureReason
import so.alaz.slate.opening.OpeningResult
import so.alaz.slate.util.ComponentUtil

internal object CommandMessages {
    fun chat(
        sender: CommandSender,
        key: String,
        placeholders: Map<String, Any?> = emptyMap(),
    ) {
        val messageService = SlateAPI.messageService()
        if (messageService == null) {
            sender.sendMessage(ComponentUtil.parse("<red>Slate is not initialized.</red>"))
            return
        }
        messageService.chat(sender, key, placeholders)
    }

    fun notInitialized(sender: CommandSender) {
        chat(sender, MessageKeys.COMMON_NOT_INITIALIZED)
    }

    fun raw(
        sender: CommandSender,
        template: String,
        placeholders: Map<String, Any?> = emptyMap(),
        withPrefix: Boolean = false,
    ) {
        val messageService = SlateAPI.messageService()
        if (messageService == null) {
            sender.sendMessage(ComponentUtil.parse("<red>Slate is not initialized.</red>"))
            return
        }
        messageService.chatRaw(sender, template, placeholders, withPrefix)
    }

    fun openFailureReason(reason: FailureReason): String =
        OpenFailureMessages.reason(reason)

    fun openFailureReason(failure: OpeningResult.Failure): String =
        OpenFailureMessages.reason(failure)

    fun openStoppedSuffix(reason: FailureReason): String =
        OpenFailureMessages.stoppedSuffix(reason)

    fun openStoppedSuffix(failure: OpeningResult.Failure): String =
        OpenFailureMessages.stoppedSuffix(failure)

    fun dispatchForSender(
        sender: CommandSender,
        block: () -> Unit,
    ) {
        val scheduler = SlateAPI.scheduler()
        if (scheduler == null) {
            block()
            return
        }
        val player = sender as? Player
        if (player != null) {
            scheduler.runForPlayerEntity(player, block)
            return
        }
        scheduler.runGlobal(block)
    }
}
