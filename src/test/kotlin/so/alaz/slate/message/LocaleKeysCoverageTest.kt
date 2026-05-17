package so.alaz.slate.message

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class LocaleKeysCoverageTest {
    @Test
    fun `new migration message keys exist in en locale`() {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream("locales/en.yml")) {
            "Missing locales/en.yml test resource"
        }
        val localeText = stream.bufferedReader().use { it.readText() }
        val yaml = YamlConfiguration()
        yaml.loadFromString(localeText)

        val expectedKeys = listOf(
            MessageKeys.ANCHOR_TP_NOT_FOUND,
            MessageKeys.ANCHOR_TP_WORLD_UNLOADED,
            MessageKeys.ANCHOR_TP_SUCCESS,
            MessageKeys.ANCHOR_REFRESH_COMPLETE,
            MessageKeys.CRATES_LOCATE_NONE,
            MessageKeys.CRATES_LOCATE_NEAREST,
            MessageKeys.CRATES_HISTORY_EMPTY,
            MessageKeys.CRATES_HISTORY_LINE,
            MessageKeys.CRATES_PITY_LINE,
            MessageKeys.CRATES_PITY_EMPTY,
            MessageKeys.ADMIN_PITY_UNKNOWN_PLAYER,
            MessageKeys.ADMIN_PITY_GET_RESULT,
            MessageKeys.ADMIN_PITY_RESET_RESULT,
            MessageKeys.BACKUP_SQLITE_ONLY,
            MessageKeys.BACKUP_DB_MISSING,
            MessageKeys.BACKUP_CREATED,
            MessageKeys.RESTORE_SQLITE_ONLY,
            MessageKeys.RESTORE_BACKUP_MISSING,
            MessageKeys.RESTORE_COMPLETE,
            MessageKeys.MIGRATE_DRY_RUN,
            MessageKeys.AUDIT_LOADING,
            MessageKeys.AUDIT_EMPTY,
            MessageKeys.AUDIT_HEADER,
            MessageKeys.AUDIT_ROW,
            MessageKeys.SESSION_SUMMARY,
            MessageKeys.DEBUG_HELDITEM_EMPTY,
            MessageKeys.DEBUG_HELDITEM_HEADER,
            MessageKeys.DEBUG_KEY_EMPTY,
            MessageKeys.DEBUG_DATABASE_SUMMARY,
            MessageKeys.DEBUG_SCHEDULER_SUMMARY,
            MessageKeys.DEBUG_INTEGRATIONS_SUMMARY,
            MessageKeys.DEBUG_DUMP_HEADER,
            MessageKeys.DEBUG_DUMP_LINE,
            MessageKeys.DEBUG_CRATES_HEADER,
            MessageKeys.DEBUG_CRATES_LINE,
            MessageKeys.DEBUG_CRATE_HEADER,
            MessageKeys.DEBUG_CRATE_CORE,
            MessageKeys.DEBUG_CRATE_SELECTION,
            MessageKeys.DEBUG_CRATE_KEY,
            MessageKeys.DEBUG_CRATE_TIERS,
            MessageKeys.DEBUG_CRATE_PRIZES,
            MessageKeys.DEBUG_PLAYER_HEADER,
            MessageKeys.DEBUG_PLAYER_LOCATION,
            MessageKeys.DEBUG_PLAYER_STATS,
            MessageKeys.DEBUG_PLAYER_PERMISSIONS,
            MessageKeys.DEBUG_ELIGIBILITY_CHECKING,
            MessageKeys.DEBUG_ELIGIBILITY_HEADER,
            MessageKeys.DEBUG_ELIGIBILITY_PERMISSION,
            MessageKeys.DEBUG_ELIGIBILITY_KEYS,
            MessageKeys.DEBUG_ELIGIBILITY_OPENS,
            MessageKeys.KEYS_VIRTUAL_RECEIVED,
        )

        expectedKeys.forEach { key ->
            assertNotNull(yaml.getString(key), "Missing locale entry for key: $key")
        }
    }
}
