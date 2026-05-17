package so.alaz.slate.item

import java.util.Optional
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import so.alaz.slate.util.ItemUtil

data class ItemBuildRequest(
    val provider: String? = null,
    val material: String? = null,
    val itemId: String? = null,
    val serialized: String? = null,
    val amount: Int = 1,
    val overlay: ItemOverlay = ItemOverlay(),
)

data class ItemOverlay(
    val name: String? = null,
    val lore: List<String>? = null,
    val glint: Boolean? = null,
    val customModelData: List<Float> = emptyList(),
    val itemModel: String? = null,
    val pdc: Map<String, String> = emptyMap(),
)

interface ItemProvider {
    val id: String
    fun isAvailable(): Boolean
    fun build(request: ItemBuildRequest): ItemStack?
}

object ItemFactory {
    fun build(request: ItemBuildRequest): ItemStack? {
        val stack = deserialize(request.serialized) ?: run {
            val provider = ItemProviderRegistry.resolve(request)
            provider.build(request)
        } ?: return null
        stack.amount = stack.amount.coerceAtLeast(1)
        ItemUtil.applyItemOverlay(stack, request.overlay)
        return stack
    }

    private fun deserialize(serialized: String?): ItemStack? {
        if (serialized.isNullOrBlank()) return null
        return ItemUtil.deserializeItemStack(serialized)
    }
}

object ItemProviderRegistry {
    private val providers: Map<String, ItemProvider> = listOf(
        VanillaItemProvider,
        NexoItemProvider,
        CraftEngineItemProvider,
        OraxenItemProvider,
        ItemsAdderItemProvider,
        MMOItemsItemProvider,
    ).associateBy { it.id }

    fun provider(id: String?): ItemProvider? {
        val normalized = id?.trim()?.lowercase() ?: return null
        return providers[normalized]
    }

    fun resolve(request: ItemBuildRequest): ItemProvider {
        provider(request.provider)?.let { return it }
        val itemId = request.itemId?.trim().orEmpty()
        return when {
            itemId.startsWith("nexo:", true) -> NexoItemProvider
            itemId.startsWith("craftengine:", true) -> CraftEngineItemProvider
            itemId.startsWith("oraxen:", true) -> OraxenItemProvider
            itemId.startsWith("itemsadder:", true) -> ItemsAdderItemProvider
            itemId.startsWith("mmoitems:", true) -> MMOItemsItemProvider
            else -> VanillaItemProvider
        }
    }
}

object VanillaItemProvider : ItemProvider {
    override val id: String = "vanilla"

    override fun isAvailable(): Boolean = true

    override fun build(request: ItemBuildRequest): ItemStack? {
        val raw = request.material?.trim().orEmpty()
        if (raw.isBlank()) return null
        val amountFromSuffix = parseAmountFromSuffix(raw)
        val materialToken = amountFromSuffix?.first ?: raw
        val amount = amountFromSuffix?.second ?: request.amount.coerceAtLeast(1)
        if (amount <= 0) return null
        val material = Material.matchMaterial(materialToken) ?: return null
        return ItemStack(material, amount)
    }

    private fun parseAmountFromSuffix(raw: String): Pair<String, Int>? {
        val starOrX = Regex("""^\s*(.+?)\s*[*xX]\s*(\d+)\s*$""").matchEntire(raw)
        if (starOrX != null) {
            return starOrX.groupValues[1].trim() to starOrX.groupValues[2].toInt()
        }

        val colon = Regex("""^\s*(.+):(\d+)\s*$""").matchEntire(raw)
        if (colon != null) {
            return colon.groupValues[1].trim() to colon.groupValues[2].toInt()
        }

        return null
    }
}

object NexoItemProvider : ReflectionItemProvider(
    id = "nexo",
    pluginName = "Nexo",
    itemIdPrefix = "nexo:",
    classCandidates = listOf(
        "com.nexomc.nexo.api.NexoItems",
        "com.nexomc.nexo.api.items.NexoItems",
    ),
    methodCandidates = listOf("itemFromId", "itemFromIdentifier", "getItemById", "itemById"),
)

object CraftEngineItemProvider : ReflectionItemProvider(
    id = "craftengine",
    pluginName = "CraftEngine",
    itemIdPrefix = "craftengine:",
    classCandidates = listOf(
        "net.momirealms.craftengine.bukkit.api.CraftEngineAPI",
        "net.momirealms.craftengine.api.CraftEngineAPI",
        "net.momirealms.craftengine.bukkit.CraftEngine",
    ),
    methodCandidates = listOf("itemById", "getItemById", "getItem", "buildItem", "createItem"),
)

object OraxenItemProvider : ReflectionItemProvider(
    id = "oraxen",
    pluginName = "Oraxen",
    itemIdPrefix = "oraxen:",
    classCandidates = listOf("io.th0rgal.oraxen.api.OraxenItems"),
    methodCandidates = listOf("getItemById", "getItemStackById", "getItemStack"),
)

object ItemsAdderItemProvider : ReflectionItemProvider(
    id = "itemsadder",
    pluginName = "ItemsAdder",
    itemIdPrefix = "itemsadder:",
    classCandidates = listOf("dev.lone.itemsadder.api.CustomStack"),
    methodCandidates = listOf("getInstance", "byItemStack", "getItemStack", "getItemStackByName"),
)

object MMOItemsItemProvider : ReflectionItemProvider(
    id = "mmoitems",
    pluginName = "MMOItems",
    itemIdPrefix = "mmoitems:",
    classCandidates = listOf("net.Indyuce.mmoitems.api.Type", "net.Indyuce.mmoitems.MMOItems"),
    methodCandidates = listOf("getItem", "getItemById", "getItemStack"),
)

abstract class ReflectionItemProvider(
    override val id: String,
    private val pluginName: String,
    private val itemIdPrefix: String,
    private val classCandidates: List<String>,
    private val methodCandidates: List<String>,
) : ItemProvider {
    override fun isAvailable(): Boolean = Bukkit.getPluginManager().getPlugin(pluginName)?.isEnabled == true

    override fun build(request: ItemBuildRequest): ItemStack? {
        if (!isAvailable()) return null
        val itemId = request.itemId?.removePrefix(itemIdPrefix)?.trim().orEmpty()
        if (itemId.isBlank()) return null
        val raw = callProvider(itemId) ?: return null
        val stack = unwrapToItemStack(raw) ?: return null
        stack.amount = request.amount.coerceAtLeast(1)
        return stack
    }

    private fun callProvider(itemId: String): Any? {
        for (className in classCandidates) {
            val clazz = runCatching { Class.forName(className) }.getOrNull() ?: continue
            for (methodName in methodCandidates) {
                val staticResult = clazz.methods.firstOrNull {
                    it.name == methodName && it.parameterCount == 1 && java.lang.reflect.Modifier.isStatic(it.modifiers)
                }?.let { runCatching { it.invoke(null, itemId) }.getOrNull() }
                if (staticResult != null) return staticResult

                val instance = clazz.methods.firstOrNull {
                    it.name == "getInstance" && it.parameterCount == 0 && java.lang.reflect.Modifier.isStatic(it.modifiers)
                }?.let { runCatching { it.invoke(null) }.getOrNull() } ?: continue

                val result = clazz.methods.firstOrNull { it.name == methodName && it.parameterCount == 1 }
                    ?.let { runCatching { it.invoke(instance, itemId) }.getOrNull() }
                if (result != null) return result
            }
        }
        return null
    }

    private fun unwrapToItemStack(raw: Any?): ItemStack? {
        var current = raw ?: return null
        repeat(6) {
            when (current) {
                is ItemStack -> return current.clone()
                is Optional<*> -> {
                    current = current.orElse(null) ?: return null
                    return@repeat
                }
            }
            val accessor = listOf("build", "buildItemStack", "getItemStack", "itemStack", "toItemStack")
                .firstNotNullOfOrNull { name ->
                    current.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }
                } ?: return null
            current = runCatching { accessor.invoke(current) }.getOrNull() ?: return null
        }
        return current as? ItemStack
    }
}
