package so.alaz.slate.config

import java.io.File
import kotlin.io.path.createTempFile
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfigValidatorTest {
    private val validator = ConfigValidator()

    @Test
    fun `collects multiple errors from invalid crate config`() {
        val file = createTempFile(suffix = ".yml").toFile()
        val yaml = YamlConfiguration()
        yaml.set("name", "")
        yaml.set("requiredKeys", 0)
        yaml.set("maxPulls", -2)
        yaml.set("cooldown", "bad")
        yaml.set("key.material", "NOT_A_MATERIAL")

        val result = validator.validate(file, yaml)
        assertFalse(result.isValid)
        assertTrue(result.errors.size >= 4)
    }

    @Test
    fun `valid root config passes`() {
        val file = File("config.yml")
        val yaml = YamlConfiguration()
        yaml.set("config-version", 1)
        yaml.set("database.type", "sqlite")
        yaml.set("maxBulkOpenAmount", 10)
        val result = validator.validate(file, yaml)
        assertTrue(result.isValid)
    }

    @Test
    fun `root config rejects invalid config version`() {
        val file = File("config.yml")
        val yaml = YamlConfiguration()
        yaml.set("config-version", 0)
        yaml.set("database.type", "sqlite")
        yaml.set("maxBulkOpenAmount", 10)
        val result = validator.validate(file, yaml)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.keyPath == "config-version" })
    }

    @Test
    fun `root config rejects invalid gui menu rows`() {
        val file = File("config.yml")
        val yaml = YamlConfiguration()
        yaml.set("config-version", 1)
        yaml.set("database.type", "sqlite")
        yaml.set("maxBulkOpenAmount", 10)
        yaml.set("gui.menu.rows", 8)
        val result = validator.validate(file, yaml)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.keyPath == "gui.menu.rows" })
    }

    @Test
    fun `root config rejects non positive max bulk open`() {
        val file = File("config.yml")
        val yaml = YamlConfiguration()
        yaml.set("database.type", "sqlite")
        yaml.set("maxBulkOpenAmount", 0)
        val result = validator.validate(file, yaml)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.keyPath == "maxBulkOpenAmount" })
    }

    @Test
    fun `invalid key customModelData type fails validation`() {
        val file = createTempFile(suffix = ".yml").toFile()
        val yaml = YamlConfiguration()
        yaml.set("name", "Demo")
        yaml.set("key.material", "TRIPWIRE_HOOK")
        yaml.set("key.customModelData", "bad-value")
        yaml.set("prizes.p1.weight", 1)
        yaml.set("prizes.p1.messages", listOf("<green>ok</green>"))

        val result = validator.validate(file, yaml)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.keyPath == "key.customModelData" })
    }

    @Test
    fun `malformed customItemId emits warning`() {
        val file = createTempFile(suffix = ".yml").toFile()
        val yaml = YamlConfiguration()
        yaml.set("name", "Demo")
        yaml.set("key.material", "TRIPWIRE_HOOK")
        yaml.set("key.customItemId", "INVALID*VALUE")
        yaml.set("prizes.p1.weight", 1)
        yaml.set("prizes.p1.messages", listOf("<green>ok</green>"))

        val result = validator.validate(file, yaml)
        assertTrue(result.warnings.any { it.keyPath == "key.customItemId" })
    }

    @Test
    fun `invalid key provider fails validation`() {
        val file = createTempFile(suffix = ".yml").toFile()
        val yaml = YamlConfiguration()
        yaml.set("name", "Demo")
        yaml.set("key.material", "TRIPWIRE_HOOK")
        yaml.set("key.provider", "invalid")
        yaml.set("prizes.p1.weight", 1)
        yaml.set("prizes.p1.messages", listOf("<green>ok</green>"))

        val result = validator.validate(file, yaml)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.keyPath == "key.provider" })
    }

    @Test
    fun `nexo provider requires providerItemId`() {
        val file = createTempFile(suffix = ".yml").toFile()
        val yaml = YamlConfiguration()
        yaml.set("name", "Demo")
        yaml.set("key.material", "TRIPWIRE_HOOK")
        yaml.set("key.provider", "nexo")
        yaml.set("prizes.p1.weight", 1)
        yaml.set("prizes.p1.messages", listOf("<green>ok</green>"))

        val result = validator.validate(file, yaml)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.keyPath == "key.providerItemId" })
    }

    @Test
    fun `percent mode requires chance total to equal 100 when empty disabled`() {
        val file = createTempFile(suffix = ".yml").toFile()
        val yaml = YamlConfiguration()
        yaml.set("name", "Demo")
        yaml.set("selection.mode", "percent")
        yaml.set("selection.structure", "flat")
        yaml.set("key.material", "TRIPWIRE_HOOK")
        yaml.set("prizes.p1.chance", 70.0)
        yaml.set("prizes.p1.messages", listOf("ok"))
        yaml.set("prizes.p2.chance", 20.0)
        yaml.set("prizes.p2.messages", listOf("ok"))

        val result = validator.validate(file, yaml)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.keyPath == "prizes" && it.message.contains("total chance") })
    }

    @Test
    fun `tiered weighted requires reward tiers and tier weights`() {
        val file = createTempFile(suffix = ".yml").toFile()
        val yaml = YamlConfiguration()
        yaml.set("name", "Demo")
        yaml.set("selection.mode", "weighted")
        yaml.set("selection.structure", "tiers")
        yaml.set("key.material", "TRIPWIRE_HOOK")
        yaml.set("tiers.common.weight", 100)
        yaml.set("prizes.p1.weight", 1)
        yaml.set("prizes.p1.messages", listOf("ok"))

        val result = validator.validate(file, yaml)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.keyPath == "prizes.p1.tier" })
    }

    @Test
    fun `seasonal crate requires valid windows`() {
        val file = createTempFile(suffix = ".yml").toFile()
        val yaml = YamlConfiguration()
        yaml.set("name", "Demo")
        yaml.set("state", "seasonal")
        yaml.set("key.material", "TRIPWIRE_HOOK")
        yaml.set("prizes.p1.weight", 1)
        yaml.set("prizes.p1.messages", listOf("ok"))

        val result = validator.validate(file, yaml)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.keyPath == "seasonal.windows" })
    }

    @Test
    fun `requirements reject blank permission nodes`() {
        val file = createTempFile(suffix = ".yml").toFile()
        val yaml = YamlConfiguration()
        yaml.set("name", "Demo")
        yaml.set("key.material", "TRIPWIRE_HOOK")
        yaml.set("requirements.permissions.open", listOf(" ", "slate.crate.demo.open"))
        yaml.set("prizes.p1.weight", 1)
        yaml.set("prizes.p1.messages", listOf("ok"))

        val result = validator.validate(file, yaml)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.keyPath == "requirements.permissions.open" })
    }

    @Test
    fun `preview rejects out of bounds content slots`() {
        val file = createTempFile(suffix = ".yml").toFile()
        val yaml = YamlConfiguration()
        yaml.set("name", "Demo")
        yaml.set("key.material", "TRIPWIRE_HOOK")
        yaml.set("prizes.p1.weight", 1)
        yaml.set("prizes.p1.messages", listOf("ok"))
        yaml.set("preview.rows", 3)
        yaml.set("preview.content-slots", "10-50")

        val result = validator.validate(file, yaml)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.keyPath == "preview.content-slots" })
    }

    @Test
    fun `animation rejects invalid template`() {
        val file = createTempFile(suffix = ".yml").toFile()
        val yaml = YamlConfiguration()
        yaml.set("name", "Demo")
        yaml.set("key.material", "TRIPWIRE_HOOK")
        yaml.set("prizes.p1.weight", 1)
        yaml.set("prizes.p1.messages", listOf("ok"))
        yaml.set("animation.enabled", true)
        yaml.set("animation.template", "roulette")

        val result = validator.validate(file, yaml)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.keyPath == "animation.template" })
    }
}
