package so.alaz.slate.gui.editor

import so.alaz.slate.api.SlateAPI
import so.alaz.slate.gui.GuiClickContext
import so.alaz.slate.message.MessageService

internal object EditorPromptSupport {
    suspend fun prompt(
        context: GuiClickContext,
        prompt: String,
        messageService: MessageService?,
    ): String? {
        val plugin = SlateAPI.plugin()
        val scheduler = SlateAPI.scheduler()
        if (plugin == null || scheduler == null) {
            messageService?.chatRaw(context.player, "<red>Slate is not initialized.</red>", withPrefix = true)
            return null
        }
        messageService?.chatRaw(context.player, "<yellow>$prompt</yellow>", withPrefix = true)
        val input = ChatInputHandler(plugin, scheduler).awaitChatInput(context.player)
        if (input == null) {
            messageService?.chatRaw(context.player, "<gray>Input cancelled or timed out.</gray>", withPrefix = true)
        }
        return input
    }
}
