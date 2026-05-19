package so.alaz.slate.gui.editor

import so.alaz.slate.api.SlateAPI
import so.alaz.slate.gui.GuiClickContext
import so.alaz.slate.gui.SlateGui

internal object EditorNavigationSupport {
    suspend fun openGui(context: GuiClickContext, gui: SlateGui) {
        SlateAPI.guiService()?.openGui(context.player, gui)
    }
}
