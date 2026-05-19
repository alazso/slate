package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import so.alaz.slate.SlateKeys
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.item.ItemBuildRequest
import so.alaz.slate.item.ItemFactory
import so.alaz.slate.item.ItemOverlay
import so.alaz.slate.item.ItemProviderRegistry
import so.alaz.slate.key.PhysicalKeyUtil
import so.alaz.slate.message.MessageKeys
import so.alaz.slate.probability.ProbabilityVariableService
import so.alaz.slate.util.ItemUtil
import so.alaz.slate.util.WorldRestrictionUtil

object DebugCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("debug")
            .then(
                Commands.literal("helditem")
                    .requires { it.sender.hasPermission(SlatePermissions.ADMIN_DEBUG_ITEM) }
                    .executes { context ->
                        val player = context.source.sender as? Player
                        if (player == null) {
                            CommandMessages.chat(context.source.sender, MessageKeys.COMMON_ONLY_PLAYERS, mapOf("command" to "/sla debug helditem"))
                            return@executes 0
                        }
                        val item = player.inventory.itemInMainHand
                        if (item.type == Material.AIR) {
                            CommandMessages.chat(player, MessageKeys.DEBUG_HELDITEM_EMPTY)
                            return@executes 0
                        }
                        CommandMessages.chat(
                            player,
                            MessageKeys.DEBUG_HELDITEM_HEADER,
                            mapOf("type" to item.type, "amount" to item.amount),
                        )
                        val meta = item.itemMeta
                        CommandMessages.chat(player, MessageKeys.DEBUG_KEY_LINE, mapOf("line" to "display=${meta?.displayName()}"))
                        CommandMessages.chat(player, MessageKeys.DEBUG_KEY_LINE, mapOf("line" to "lore=${meta?.lore()}"))
                        CommandMessages.chat(player, MessageKeys.DEBUG_KEY_LINE, mapOf("line" to "modelData=${PhysicalKeyUtil.customModelData(item)}"))
                        val keys = meta?.persistentDataContainer?.keys.orEmpty().sortedBy { it.asString() }
                        if (keys.isEmpty()) {
                            CommandMessages.chat(player, MessageKeys.DEBUG_KEY_LINE, mapOf("line" to "pdc=none"))
                        } else {
                            keys.forEach { key ->
                                val value = meta?.persistentDataContainer?.get(key, PersistentDataType.STRING)
                                CommandMessages.chat(
                                    player,
                                    MessageKeys.DEBUG_KEY_LINE,
                                    mapOf("line" to "pdc ${key.asString()} = ${value ?: "<non-string>"}"),
                                )
                            }
                        }
                        Command.SINGLE_SUCCESS
                    },
            )
            .then(
                Commands.literal("key")
                    .requires { it.sender.hasPermission(SlatePermissions.ADMIN_DEBUG_ITEM) }
                    .executes { context ->
                        val player = context.source.sender as? Player
                        if (player == null) {
                            CommandMessages.chat(context.source.sender, MessageKeys.COMMON_ONLY_PLAYERS, mapOf("command" to "/sla debug key"))
                            return@executes 0
                        }
                        val item = player.inventory.itemInMainHand
                        if (item.type == Material.AIR) {
                            CommandMessages.chat(player, MessageKeys.DEBUG_KEY_EMPTY)
                            return@executes 0
                        }

                        val keyService = SlateAPI.keyService()
                        val crateManager = SlateAPI.crateManager()
                        if (keyService == null || crateManager == null) {
                            CommandMessages.notInitialized(player)
                            return@executes 0
                        }

                        val isSlateKey = PhysicalKeyUtil.isSlateKey(item)
                        val stampedCrateId = PhysicalKeyUtil.crateId(item)
                        val stampedProvider = PhysicalKeyUtil.providerType(item)
                        val stampedProviderItemId = PhysicalKeyUtil.providerItemId(item)
                        val stampedCustomItemId = ItemUtil.getData(item, SlateKeys.KEY_CUSTOM_ITEM_ID, PersistentDataType.STRING)
                        val recognizedCrate = keyService.getCrateForKey(item)
                        val configuredCrate = stampedCrateId?.let(crateManager::byId)
                        val strictMatch = configuredCrate?.let { PhysicalKeyUtil.matchesKeyDefinition(item, it) } ?: false

                        CommandMessages.chat(player, MessageKeys.DEBUG_KEY_HEADER)
                        CommandMessages.chat(player, MessageKeys.DEBUG_KEY_LINE, mapOf("line" to "item=${item.type} amount=${item.amount}"))
                        CommandMessages.chat(player, MessageKeys.DEBUG_KEY_LINE, mapOf("line" to "slate.isKey=$isSlateKey"))
                        CommandMessages.chat(player, MessageKeys.DEBUG_KEY_LINE, mapOf("line" to "pdc.crateId=${stampedCrateId ?: "null"}"))
                        CommandMessages.chat(player, MessageKeys.DEBUG_KEY_LINE, mapOf("line" to "pdc.provider=${stampedProvider?.name ?: "null"}"))
                        CommandMessages.chat(player, MessageKeys.DEBUG_KEY_LINE, mapOf("line" to "pdc.providerItemId=${stampedProviderItemId ?: "null"}"))
                        CommandMessages.chat(player, MessageKeys.DEBUG_KEY_LINE, mapOf("line" to "pdc.customItemId=${stampedCustomItemId ?: "null"}"))
                        CommandMessages.chat(player, MessageKeys.DEBUG_KEY_LINE, mapOf("line" to "modelData=${PhysicalKeyUtil.customModelData(item)}"))
                        CommandMessages.chat(player, MessageKeys.DEBUG_KEY_LINE, mapOf("line" to "configuredCrate=${configuredCrate?.id ?: "null"}"))
                        CommandMessages.chat(player, MessageKeys.DEBUG_KEY_LINE, mapOf("line" to "strictMatch=$strictMatch"))
                        CommandMessages.chat(player, MessageKeys.DEBUG_KEY_LINE, mapOf("line" to "recognizedCrate=${recognizedCrate?.id ?: "null"}"))

                        if (configuredCrate != null && !strictMatch) {
                            CommandMessages.chat(player, MessageKeys.DEBUG_KEY_MISMATCH, mapOf("crate" to configuredCrate.id))
                        }
                        if (recognizedCrate == null) {
                            CommandMessages.chat(player, MessageKeys.DEBUG_KEY_MISMATCH, mapOf("crate" to "unknown"))
                        } else {
                            CommandMessages.chat(player, MessageKeys.DEBUG_KEY_MATCH, mapOf("crate" to recognizedCrate.id))
                        }
                        Command.SINGLE_SUCCESS
                    },
            )
            .then(
                Commands.literal("crate")
                    .requires { it.sender.hasPermission(SlatePermissions.ADMIN_DEBUG_CRATE) }
                    .executes { context ->
                        debugCrates(context.source, crateId = null)
                    }
                    .then(
                        Commands.argument("crate", StringArgumentType.word())
                            .suggests { context, builder ->
                                CommandSuggestions.suggestCrateIds(context.source, builder)
                            }
                            .executes { context ->
                                debugCrates(context.source, StringArgumentType.getString(context, "crate"))
                            },
                    ),
            )
            .then(
                Commands.literal("player")
                    .requires { it.sender.hasPermission(SlatePermissions.ADMIN_DEBUG_PLAYER) }
                    .executes { context ->
                        val player = context.source.sender as? Player
                        if (player == null) {
                            CommandMessages.chat(context.source.sender, MessageKeys.COMMON_ONLY_PLAYERS, mapOf("command" to "/sla debug player"))
                            return@executes 0
                        }
                        debugPlayer(player)
                    },
            )
            .then(
                Commands.literal("eligibility")
                    .requires { it.sender.hasPermission(SlatePermissions.ADMIN_DEBUG_ELIGIBILITY) }
                    .then(
                        Commands.argument("crate", StringArgumentType.word())
                            .suggests { context, builder ->
                                CommandSuggestions.suggestCrateIds(context.source, builder)
                            }
                            .executes { context ->
                                debugEligibility(context.source, StringArgumentType.getString(context, "crate"))
                            },
                    ),
            )
            .then(
                Commands.literal("placeholder")
                    .requires { it.sender.hasPermission(SlatePermissions.ADMIN_DEBUG_PLACEHOLDER) }
                    .then(
                        Commands.argument("crate", StringArgumentType.word())
                            .suggests { context, builder ->
                                CommandSuggestions.suggestCrateIds(context.source, builder)
                            }
                            .executes { context ->
                                val crateId = StringArgumentType.getString(context, "crate")
                                val vars = ProbabilityVariableService.crateVariables(crateId)
                                CommandMessages.chat(context.source.sender, MessageKeys.DEBUG_KEY_LINE, mapOf("line" to "crate placeholders for '$crateId'"))
                                vars.toSortedMap().forEach { (k, v) ->
                                    CommandMessages.chat(context.source.sender, MessageKeys.DEBUG_KEY_LINE, mapOf("line" to "<$k>=$v"))
                                }
                                Command.SINGLE_SUCCESS
                            }
                            .then(
                                Commands.argument("reward", StringArgumentType.word())
                                    .executes { context ->
                                        val crateId = StringArgumentType.getString(context, "crate")
                                        val rewardId = StringArgumentType.getString(context, "reward")
                                        val vars = ProbabilityVariableService.rewardVariables(crateId, rewardId)
                                        CommandMessages.chat(context.source.sender, MessageKeys.DEBUG_KEY_LINE, mapOf("line" to "reward placeholders for '$crateId/$rewardId'"))
                                        vars.toSortedMap().forEach { (k, v) ->
                                            CommandMessages.chat(context.source.sender, MessageKeys.DEBUG_KEY_LINE, mapOf("line" to "<$k>=$v"))
                                        }
                                        Command.SINGLE_SUCCESS
                                    },
                            ),
                    ),
            )
            .then(
                Commands.literal("database")
                    .requires { it.sender.hasPermission(SlatePermissions.ADMIN_DEBUG_DATABASE) }
                    .executes { context ->
                        val cfg = SlateAPI.configManager()?.current
                        if (cfg == null) {
                            CommandMessages.notInitialized(context.source.sender)
                            return@executes 0
                        }
                        CommandMessages.chat(
                            context.source.sender,
                            MessageKeys.DEBUG_DATABASE_SUMMARY,
                            mapOf(
                                "type" to cfg.database.type,
                                "pool" to cfg.database.poolSize,
                                "timeoutMs" to cfg.database.connectionTimeout.inWholeMilliseconds,
                            ),
                        )
                        Command.SINGLE_SUCCESS
                    },
            )
            .then(
                Commands.literal("scheduler")
                    .requires { it.sender.hasPermission(SlatePermissions.ADMIN_DEBUG_SCHEDULER) }
                    .executes { context ->
                        val plugin = SlateAPI.plugin()
                        if (plugin == null) {
                            CommandMessages.notInitialized(context.source.sender)
                            return@executes 0
                        }
                        CommandMessages.chat(
                            context.source.sender,
                            MessageKeys.DEBUG_SCHEDULER_SUMMARY,
                            mapOf(
                                "platform" to so.alaz.slate.scheduler.ServerPlatform.current,
                                "dispatcher" to plugin.mainDispatcher::class.simpleName,
                            ),
                        )
                        Command.SINGLE_SUCCESS
                    },
            )
            .then(
                Commands.literal("integrations")
                    .requires { it.sender.hasPermission(SlatePermissions.ADMIN_DEBUG_INTEGRATIONS) }
                    .executes { context ->
                        val sender = context.source.sender
                        val plugin = SlateAPI.plugin()
                        if (plugin == null) {
                            CommandMessages.notInitialized(sender)
                            return@executes 0
                        }
                        val plugins = listOf("PlaceholderAPI", "Nexo", "CraftEngine", "Oraxen", "ItemsAdder", "MMOItems")
                        val pluginStates = plugins.joinToString(", ") { name ->
                            val enabled = plugin.server.pluginManager.getPlugin(name)?.isEnabled == true
                            "$name=${if (enabled) "on" else "off"}"
                        }
                        val providerStates = itemProviderStates()
                        val probeStates = resolverProbeStates()
                        val economyService = SlateAPI.economyService()
                        val currencyProviders = economyService?.providers()
                            ?.joinToString(", ") { "${it.id}=${if (it.isAvailable()) "ready" else "down"}" }
                            .orEmpty()
                            .ifBlank { "none" }
                        val defaultProvider = SlateAPI.configManager()?.current?.currency?.defaultProvider ?: "n/a"
                        val defaultCurrency = SlateAPI.configManager()?.current?.currency?.defaultCurrency ?: "n/a"
                        val states = "plugins[$pluginStates] providers[$providerStates] probes[$probeStates] currency[$currencyProviders default=$defaultProvider/$defaultCurrency]"
                        CommandMessages.chat(sender, MessageKeys.DEBUG_INTEGRATIONS_SUMMARY, mapOf("states" to states))
                        Command.SINGLE_SUCCESS
                    },
            )
            .then(
                Commands.literal("dump")
                    .requires { it.sender.hasPermission(SlatePermissions.ADMIN_DEBUG_DUMP) }
                    .executes { context ->
                        val crateManager = SlateAPI.crateManager()
                        if (crateManager == null) {
                            CommandMessages.notInitialized(context.source.sender)
                            return@executes 0
                        }
                        val crates = crateManager.all().sortedBy { it.id }
                        CommandMessages.chat(context.source.sender, MessageKeys.DEBUG_DUMP_HEADER, mapOf("count" to crates.size))
                        crates.take(20).forEach { crate ->
                            CommandMessages.chat(
                                context.source.sender,
                                MessageKeys.DEBUG_DUMP_LINE,
                                mapOf(
                                    "crate" to crate.id,
                                    "rewards" to crate.prizes.size,
                                    "mode" to "${crate.selection.mode}/${crate.selection.structure}",
                                ),
                            )
                        }
                        Command.SINGLE_SUCCESS
                    },
            )
            .then(
                Commands.literal("toggle")
                    .requires { it.sender.hasPermission(SlatePermissions.ADMIN_DEBUG_TOGGLE) }
                    .executes { context ->
                        val config = SlateAPI.configManager()?.current
                        if (config == null) {
                            CommandMessages.notInitialized(context.source.sender)
                            return@executes 0
                        }
                        config.debug = !config.debug
                        CommandMessages.chat(context.source.sender, MessageKeys.DEBUG_KEY_LINE, mapOf("line" to "Debug is now ${config.debug}"))
                        Command.SINGLE_SUCCESS
                    },
            )
            .build()

    private fun debugCrates(source: CommandSourceStack, crateId: String?): Int {
        val crateManager = SlateAPI.crateManager()
        if (crateManager == null) {
            CommandMessages.notInitialized(source.sender)
            return 0
        }
        if (crateId == null) {
            val crates = crateManager.all().sortedBy { it.id }
            CommandMessages.chat(source.sender, MessageKeys.DEBUG_CRATES_HEADER, mapOf("count" to crates.size))
            crates.take(20).forEach { crate ->
                CommandMessages.chat(
                    source.sender,
                    MessageKeys.DEBUG_CRATES_LINE,
                    mapOf(
                        "crate" to crate.id,
                        "prizes" to crate.prizes.size,
                        "tiers" to crate.tiers.size,
                        "mode" to "${crate.selection.mode}/${crate.selection.structure}",
                    ),
                )
            }
            return Command.SINGLE_SUCCESS
        }

        val crate = crateManager.byId(crateId)
        if (crate == null) {
            CommandMessages.chat(source.sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
            return 0
        }
        CommandMessages.chat(
            source.sender,
            MessageKeys.DEBUG_CRATE_HEADER,
            mapOf("crate" to crate.id, "name" to crate.name),
        )
        CommandMessages.chat(
            source.sender,
            MessageKeys.DEBUG_CRATE_CORE,
            mapOf(
                "requiredKeys" to crate.requiredKeys,
                "maxPulls" to crate.maxPulls,
                "cooldown" to crate.cooldown,
                "worlds" to crate.allowedWorlds.ifEmpty { setOf("any") }.joinToString(","),
            ),
        )
        CommandMessages.chat(
            source.sender,
            MessageKeys.DEBUG_CRATE_SELECTION,
            mapOf(
                "selection" to "${crate.selection.mode}/${crate.selection.structure}",
                "allowEmpty" to crate.selection.allowEmpty,
                "emptyWeight" to crate.selection.emptyWeight,
            ),
        )
        CommandMessages.chat(
            source.sender,
            MessageKeys.DEBUG_CRATE_KEY,
            mapOf(
                "provider" to crate.keyDefinition.provider,
                "material" to crate.keyDefinition.material,
                "modelData" to crate.keyDefinition.customModelData,
            ),
        )
        CommandMessages.chat(
            source.sender,
            MessageKeys.DEBUG_CRATE_TIERS,
            mapOf("tiers" to crate.tiers.keys.sorted().joinToString(",").ifBlank { "none" }),
        )
        CommandMessages.chat(
            source.sender,
            MessageKeys.DEBUG_CRATE_PRIZES,
            mapOf("prizes" to crate.prizes.map { it.id }.sorted().joinToString(",").ifBlank { "none" }),
        )
        return Command.SINGLE_SUCCESS
    }

    private fun debugPlayer(player: Player): Int {
        val location = player.location
        val maxHealth = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: player.health
        CommandMessages.chat(player, MessageKeys.DEBUG_PLAYER_HEADER, mapOf("player" to player.name, "uuid" to player.uniqueId))
        CommandMessages.chat(
            player,
            MessageKeys.DEBUG_PLAYER_LOCATION,
            mapOf("world" to player.world.key.asString(), "x" to location.blockX, "y" to location.blockY, "z" to location.blockZ),
        )
        CommandMessages.chat(
            player,
            MessageKeys.DEBUG_PLAYER_STATS,
            mapOf("gameMode" to player.gameMode, "op" to player.isOp, "health" to player.health, "maxHealth" to maxHealth),
        )
        CommandMessages.chat(
            player,
            MessageKeys.DEBUG_PLAYER_PERMISSIONS,
            mapOf(
                "open" to player.hasPermission(SlatePermissions.PLAYER_OPEN),
                "claim" to player.hasPermission(SlatePermissions.PLAYER_CLAIM),
                "keys" to player.hasPermission(SlatePermissions.PLAYER_KEYS),
            ),
        )
        return Command.SINGLE_SUCCESS
    }

    private fun debugEligibility(source: CommandSourceStack, crateId: String): Int {
        val player = source.sender as? Player
        if (player == null) {
            CommandMessages.chat(source.sender, MessageKeys.COMMON_ONLY_PLAYERS, mapOf("command" to "/sla debug eligibility"))
            return 0
        }
        val crateManager = SlateAPI.crateManager()
        val keyService = SlateAPI.keyService()
        val storage = SlateAPI.storageService()
        val scope = SlateAPI.pluginScope()
        val plugin = SlateAPI.plugin()
        if (crateManager == null || keyService == null || storage == null || scope == null || plugin == null) {
            CommandMessages.notInitialized(player)
            return 0
        }
        val crate = crateManager.byId(crateId)
        if (crate == null) {
            CommandMessages.chat(player, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
            return 0
        }

        CommandMessages.chat(player, MessageKeys.DEBUG_ELIGIBILITY_CHECKING, mapOf("crate" to crate.id))
        scope.launch {
            val worldAllowed = WorldRestrictionUtil.isAllowed(player.world, crate.allowedWorlds)
            val cooldown = storage.getCooldown(player.uniqueId, crate.id)
            val opens = storage.countOpens(player.uniqueId, crate.id)
            val pity = storage.getPityCount(player.uniqueId, crate.id)
            val virtualKeys = keyService.getVirtualKeys(player.uniqueId, crate.id)
            val hasVirtual = keyService.hasKey(player, crate, so.alaz.slate.key.KeyType.Virtual)
            val hasPhysical = keyService.hasKey(player, crate, so.alaz.slate.key.KeyType.Physical)
            withContext(plugin.mainDispatcher) {
                CommandMessages.chat(player, MessageKeys.DEBUG_ELIGIBILITY_HEADER, mapOf("crate" to crate.id))
                CommandMessages.chat(
                    player,
                    MessageKeys.DEBUG_ELIGIBILITY_PERMISSION,
                    mapOf(
                        "permission" to player.hasPermission("${SlatePermissions.OPEN_CRATE_PREFIX}${crate.id}"),
                        "worldAllowed" to worldAllowed,
                    ),
                )
                CommandMessages.chat(
                    player,
                    MessageKeys.DEBUG_ELIGIBILITY_KEYS,
                    mapOf("virtualKeys" to virtualKeys, "hasVirtual" to hasVirtual, "hasPhysical" to hasPhysical),
                )
                CommandMessages.chat(
                    player,
                    MessageKeys.DEBUG_ELIGIBILITY_OPENS,
                    mapOf("opens" to opens, "maxPulls" to crate.maxPulls, "pity" to pity, "cooldown" to (cooldown ?: "none")),
                )
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun itemProviderStates(): String {
        val providers = listOf("vanilla", "nexo", "craftengine", "oraxen", "itemsadder", "mmoitems")
        return providers.joinToString(", ") { providerId ->
            val provider = ItemProviderRegistry.provider(providerId)
            val state = when {
                provider == null -> "missing"
                provider.isAvailable() -> "ready"
                else -> "unavailable"
            }
            "$providerId=$state"
        }
    }

    private fun resolverProbeStates(): String {
        val crateManager = SlateAPI.crateManager() ?: return "crate-manager=unavailable"
        val crates = crateManager.all()
        val keyTargets = crates.filter {
            it.keyDefinition.provider != KeyProvider.Vanilla && !it.keyDefinition.providerItemId.isNullOrBlank()
        }
        val keyResolvable = keyTargets.count { crate ->
            ItemFactory.canResolve(
                ItemBuildRequest(
                    provider = crate.keyDefinition.provider.providerId,
                    material = crate.keyDefinition.material.name,
                    itemId = crate.keyDefinition.providerItemId,
                    amount = 1,
                ),
            )
        }

        val rewardTargets = crates
            .flatMap { it.prizes }
            .flatMap { it.items }
            .filter { !it.serialized.isNullOrBlank() || !it.itemId.isNullOrBlank() || !it.adapter.isNullOrBlank() }
        val rewardResolvable = rewardTargets.count { reward ->
            ItemFactory.canResolve(
                ItemBuildRequest(
                    provider = reward.adapter,
                    material = reward.material,
                    itemId = reward.itemId,
                    serialized = reward.serialized,
                    amount = 1,
                    overlay = ItemOverlay(
                        customModelData = reward.customModelData,
                        pdc = reward.pdc,
                    ),
                ),
            )
        }

        return "keys=$keyResolvable/${keyTargets.size}, rewards=$rewardResolvable/${rewardTargets.size}"
    }
}
