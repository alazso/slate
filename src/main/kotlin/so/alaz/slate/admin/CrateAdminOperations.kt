package so.alaz.slate.admin

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.bukkit.configuration.file.YamlConfiguration
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.sub.ProbabilityCommandSupport
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.ReloadFileStatus

/**
 * Reusable crate management primitives shared by `/sla crate ...` and the admin GUI Crate Manager.
 *
 * Each method either succeeds or returns a typed failure so the caller can render an appropriate
 * message (chat or hotbar). All persistence goes through ProbabilityCommandSupport so YAML and the
 * runtime cache stay in lockstep.
 */
object CrateAdminOperations {
    sealed class Result {
        data class Ok(val id: String) : Result()
        data class AlreadyExists(val id: String) : Result()
        data class Missing(val id: String) : Result()
        data class ReloadFailed(val id: String, val reason: String?) : Result()
        data class IoFailed(val id: String, val cause: Throwable) : Result()
    }

    data class ValidationSummary(
        val total: Int,
        val failed: Int,
        val failedIds: List<String>,
    )

    fun cratesDir(): File? {
        val plugin = SlateAPI.plugin() ?: return null
        return File(plugin.dataFolder, "crates").apply { mkdirs() }
    }

    fun createDefault(crateId: String): Result {
        if (crateId.isBlank()) return Result.IoFailed(crateId, IllegalArgumentException("blank id"))
        val dir = cratesDir() ?: return Result.IoFailed(crateId, IllegalStateException("plugin not initialized"))
        if (ProbabilityCommandSupport.findCrateFile(crateId) != null) return Result.AlreadyExists(crateId)
        val file = File(dir, "${crateId}.yml")
        val yaml = YamlConfiguration().apply {
            set("crate-version", 1)
            set("name", crateId)
            set("selection.mode", "weighted")
            set("selection.structure", "flat")
            set("requiredKeys", 1)
            set("key.material", "TRIPWIRE_HOOK")
            set("prizes.default.name", "Default Reward")
            set("prizes.default.weight", 1)
            set("prizes.default.items", listOf("DIAMOND"))
        }
        return runCatching {
            yaml.save(file)
            val summary = SlateAPI.crateManager()?.reload()
            val ok = summary?.fileOutcomes?.get(file.path)?.status == ReloadFileStatus.UPDATED
            if (!ok) Result.ReloadFailed(crateId, summary?.fileOutcomes?.get(file.path)?.status?.name)
            else Result.Ok(crateId)
        }.getOrElse { Result.IoFailed(crateId, it) }
    }

    fun clone(sourceId: String, targetId: String): Result {
        if (targetId.isBlank()) return Result.IoFailed(targetId, IllegalArgumentException("blank id"))
        val source = ProbabilityCommandSupport.findCrateFile(sourceId) ?: return Result.Missing(sourceId)
        if (ProbabilityCommandSupport.findCrateFile(targetId) != null) return Result.AlreadyExists(targetId)
        val targetFile = File(source.parentFile, "${targetId}.yml")
        return runCatching {
            Files.copy(source.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            val summary = SlateAPI.crateManager()?.reload()
            val ok = summary?.fileOutcomes?.get(targetFile.path)?.status == ReloadFileStatus.UPDATED
            if (!ok) Result.ReloadFailed(targetId, summary?.fileOutcomes?.get(targetFile.path)?.status?.name)
            else Result.Ok(targetId)
        }.getOrElse { Result.IoFailed(targetId, it) }
    }

    fun delete(crateId: String): Result {
        val file = ProbabilityCommandSupport.findCrateFile(crateId) ?: return Result.Missing(crateId)
        return runCatching {
            if (!file.delete()) return Result.IoFailed(crateId, IllegalStateException("file delete failed"))
            SlateAPI.crateManager()?.reload()
            Result.Ok(crateId)
        }.getOrElse { Result.IoFailed(crateId, it) }
    }

    fun validateAll(): ValidationSummary {
        val crates = SlateAPI.crateManager()?.all().orEmpty()
        val validator = so.alaz.slate.config.ConfigValidator()
        val failures = mutableListOf<String>()
        for (crate in crates) {
            val loaded = ProbabilityCommandSupport.loadCrateFile(crate.id) ?: continue
            val result = validator.validate(loaded.first, loaded.second)
            if (!result.isValid) failures += crate.id
        }
        return ValidationSummary(total = crates.size, failed = failures.size, failedIds = failures)
    }
}
