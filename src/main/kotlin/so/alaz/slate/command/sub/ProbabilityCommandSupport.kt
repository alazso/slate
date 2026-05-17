package so.alaz.slate.command.sub

import java.io.File
import java.time.Instant
import org.bukkit.configuration.file.YamlConfiguration
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CrateState
import so.alaz.slate.crate.ReloadFileStatus

internal object ProbabilityCommandSupport {
    data class SaveReloadResult(
        val success: Boolean,
        val message: String? = null,
    )

    data class MutabilityDecision(
        val allowed: Boolean,
        val warning: String? = null,
    )

    fun findCrateFile(crateId: String): File? {
        val plugin = SlateAPI.plugin() ?: return null
        val recursive = SlateAPI.configManager()?.current?.crateScanRecursive == true
        val cratesDir = File(plugin.dataFolder, "crates")
        if (!cratesDir.exists()) return null
        val candidates = if (recursive) {
            cratesDir.walkTopDown().filter { it.isFile && it.extension.equals("yml", true) }
        } else {
            cratesDir.listFiles { file -> file.isFile && file.extension.equals("yml", true) }?.asSequence().orEmpty()
        }
        return candidates.firstOrNull { it.nameWithoutExtension.equals(crateId, ignoreCase = true) }
    }

    fun loadCrateFile(crateId: String): Pair<File, YamlConfiguration>? {
        val file = findCrateFile(crateId) ?: return null
        return file to YamlConfiguration.loadConfiguration(file)
    }

    fun saveAndReload(file: File, yaml: YamlConfiguration): SaveReloadResult {
        yaml.save(file)
        val summary = SlateAPI.crateManager()?.reload() ?: return SaveReloadResult(false, "Crate manager unavailable during reload.")
        val outcome = summary.fileOutcomes[file.path]
        return when (outcome?.status) {
            ReloadFileStatus.UPDATED -> SaveReloadResult(true)
            ReloadFileStatus.RETAINED_LAST_GOOD -> SaveReloadResult(false, "Reload retained the previous crate config. Changes were invalid.")
            ReloadFileStatus.FAILED -> SaveReloadResult(false, "Reload failed for this crate file.")
            ReloadFileStatus.REMOVED -> SaveReloadResult(false, "Crate file was removed during reload.")
            null -> SaveReloadResult(false, "Reload did not report an outcome for the edited crate file.")
        }
    }

    fun requireCrate(crateId: String): CrateDefinition? =
        SlateAPI.crateManager()?.byId(crateId)

    fun mutability(crate: CrateDefinition, now: Instant = Instant.now()): MutabilityDecision = when (crate.lifecycle.state) {
        CrateState.ARCHIVED -> MutabilityDecision(false, "Archived crates are immutable.")
        CrateState.DISABLED -> MutabilityDecision(true, "Editing a disabled crate. It cannot currently be opened.")
        CrateState.SEASONAL -> {
            if (crate.lifecycle.isOpenInSeason(now)) {
                MutabilityDecision(true)
            } else {
                MutabilityDecision(true, "Editing an out-of-season crate.")
            }
        }
        else -> MutabilityDecision(true)
    }
}
