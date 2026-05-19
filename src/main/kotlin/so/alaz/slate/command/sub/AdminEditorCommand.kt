package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.launch
import org.bukkit.entity.Player
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.gui.editor.AdminPanelGui
import so.alaz.slate.message.MessageKeys

object AdminEditorCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("editor")
            .requires { it.sender.hasPermission(SlatePermissions.CRATE_EDITOR) }
            .executes { context ->
                val player = context.source.sender as? Player
                if (player == null) {
                    CommandMessages.chat(context.source.sender, MessageKeys.COMMON_ONLY_PLAYERS, mapOf("command" to "/sla editor"))
                    return@executes 0
                }
                val guiService = SlateAPI.guiService()
                val scope = SlateAPI.pluginScope()
                if (guiService == null || scope == null) {
                    CommandMessages.notInitialized(player)
                    return@executes 0
                }
                scope.launch {
                    guiService.openGui(player, AdminPanelGui())
                }
                Command.SINGLE_SUCCESS
            }
            .build()
}

