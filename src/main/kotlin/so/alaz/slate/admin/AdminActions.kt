package so.alaz.slate.admin

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.config.DatabaseType
import so.alaz.slate.crate.ReloadFileStatus
import so.alaz.slate.crate.ReloadSummary
import so.alaz.slate.probability.ProbabilityVariableService

/**
 * Reusable admin operations shared by `/sla reload`, `/sla backup`, and the admin GUI buttons.
 *
 * Extracting this keeps the chat commands and the GUI panel as two thin frontends over the same
 * logic instead of duplicating procedure inside click handlers.
 */
object AdminActions {
    data class ReloadResult(
        val success: Boolean,
        val crateSummary: ReloadSummary?,
        val configValid: Boolean,
        val failedFiles: List<String>,
        val retained: Int,
    )

    sealed class BackupResult {
        data object SqliteOnly : BackupResult()
        data object DatabaseMissing : BackupResult()
        data class Created(val file: Path) : BackupResult()
        data class Failed(val error: Throwable) : BackupResult()
    }

    data class HealthReport(
        val crateCount: Int,
        val onlineCount: Int,
        val debug: Boolean,
    )

    fun reload(): ReloadResult {
        val configResult = SlateAPI.configManager()?.reload()
        val summary = SlateAPI.crateManager()?.reload()
            ?: return ReloadResult(
                success = false,
                crateSummary = null,
                configValid = configResult?.isValid != false,
                failedFiles = emptyList(),
                retained = 0,
            )
        ProbabilityVariableService.clearCache()
        SlateAPI.messageService()?.reload(SlateAPI.configManager()?.current?.language ?: "en")
        SlateAPI.displayService()?.reloadDisplays()
        val failedFiles = summary.fileOutcomes
            .filterValues { it.status == ReloadFileStatus.FAILED }
            .keys
            .toList()
        val retained = summary.fileOutcomes.count { it.value.status == ReloadFileStatus.RETAINED_LAST_GOOD }
        return ReloadResult(
            success = true,
            crateSummary = summary,
            configValid = configResult?.isValid != false,
            failedFiles = failedFiles,
            retained = retained,
        )
    }

    fun backup(): BackupResult {
        val plugin = SlateAPI.plugin() ?: return BackupResult.Failed(IllegalStateException("plugin not initialized"))
        val cfg = SlateAPI.configManager()?.current ?: return BackupResult.Failed(IllegalStateException("config not initialized"))
        if (cfg.database.type != DatabaseType.SQLITE) return BackupResult.SqliteOnly
        val db = plugin.dataFolder.toPath().resolve("slate.db")
        if (!Files.exists(db)) return BackupResult.DatabaseMissing
        return runCatching {
            val dir = plugin.dataFolder.toPath().resolve("backups")
            Files.createDirectories(dir)
            val target = dir.resolve("slate-${Instant.now().epochSecond}.db.bak")
            Files.copy(db, target)
            BackupResult.Created(target)
        }.getOrElse { BackupResult.Failed(it) }
    }

    fun health(): HealthReport {
        val crateCount = SlateAPI.crateManager()?.all()?.size ?: 0
        val online = SlateAPI.plugin()?.server?.onlinePlayers?.size ?: 0
        val debug = SlateAPI.plugin()?.configManager?.current?.debug ?: false
        return HealthReport(crateCount = crateCount, onlineCount = online, debug = debug)
    }
}
