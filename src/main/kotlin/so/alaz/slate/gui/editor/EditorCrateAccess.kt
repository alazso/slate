package so.alaz.slate.gui.editor

import java.io.File
import org.bukkit.configuration.file.YamlConfiguration
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.sub.ProbabilityCommandSupport
import so.alaz.slate.crate.CrateDefinition

internal object EditorCrateAccess {
    data class EditableCrateFile(
        val crate: CrateDefinition,
        val file: File,
        val yaml: YamlConfiguration,
    )

    fun listCrates(): List<CrateDefinition> =
        SlateAPI.crateManager()
            ?.all()
            ?.sortedBy { it.id }
            .orEmpty()

    fun findCrate(crateId: String): CrateDefinition? =
        ProbabilityCommandSupport.requireCrate(crateId)

    fun loadForEdit(crateId: String): EditableCrateFile? {
        val crate = findCrate(crateId) ?: return null
        val loaded = ProbabilityCommandSupport.loadCrateFile(crate.id) ?: return null
        return EditableCrateFile(
            crate = crate,
            file = loaded.first,
            yaml = loaded.second,
        )
    }

    fun saveAndReload(file: File, yaml: YamlConfiguration): ProbabilityCommandSupport.SaveReloadResult =
        ProbabilityCommandSupport.saveAndReload(file, yaml)

    fun mutability(crate: CrateDefinition): ProbabilityCommandSupport.MutabilityDecision =
        ProbabilityCommandSupport.mutability(crate)
}
