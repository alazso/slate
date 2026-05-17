package so.alaz.slate.util

import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Logger
import so.alaz.slate.config.ValidationError
import so.alaz.slate.config.ValidationWarning

object LogUtil {
    private var logger: Logger = Logger.getLogger("Slate")
    private var debugEnabledProvider: () -> Boolean = { false }

    fun init(plugin: JavaPlugin, debugEnabledProvider: () -> Boolean = { false }) {
        logger = plugin.logger
        this.debugEnabledProvider = debugEnabledProvider
    }

    fun info(message: String) = logger.info("[Slate] $message")
    fun warn(message: String) = logger.warning("[Slate] $message")
    fun error(message: String, cause: Throwable? = null) {
        if (cause == null) logger.severe("[Slate] $message")
        else logger.severe("[Slate] $message :: ${cause.message}")
    }

    fun debug(message: String) {
        if (debugEnabledProvider()) {
            logger.info("[Slate:DEBUG] $message")
        }
    }

    fun validationError(error: ValidationError) {
        val suggestion = error.suggestion?.let { " Suggestion: $it" }.orEmpty()
        error("[Config] ${error.filePath} :: ${error.keyPath} :: ${error.message}$suggestion")
    }

    fun validationWarning(warning: ValidationWarning) {
        warn("[Config] ${warning.filePath} :: ${warning.keyPath} :: ${warning.message}")
    }
}
