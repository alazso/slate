package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.config.ConfigValidator
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.probability.SelectionMode
import so.alaz.slate.probability.SelectionStructure
import so.alaz.slate.message.MessageKeys
import so.alaz.slate.probability.ChanceFormatter
import so.alaz.slate.probability.ProbabilityEngine
import so.alaz.slate.util.ItemUtil

object CrateProbabilityCommand {
    private const val MAX_SIMULATION_ROLLS = 250_000
    private val deleteFlags = listOf("--confirm")
    private val editablePaths = listOf(
        "name",
        "state",
        "requiredKeys",
        "maxPulls",
        "cooldown",
        "allowedWorlds",
        "selection.mode",
        "selection.structure",
        "selection.allow-empty",
        "selection.empty-weight",
        "key.provider",
        "key.providerItemId",
        "key.material",
        "key.name",
    )

    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("crate")
            .requires {
                it.sender.hasPermission(SlatePermissions.ADMIN_CRATE) ||
                    it.sender.hasPermission(SlatePermissions.ADMIN_PROBABILITY) ||
                    it.sender.hasPermission(SlatePermissions.CRATE_LIST) ||
                    it.sender.hasPermission(SlatePermissions.CRATE_INFO) ||
                    it.sender.hasPermission(SlatePermissions.CRATE_CREATE) ||
                    it.sender.hasPermission(SlatePermissions.CRATE_CLONE) ||
                    it.sender.hasPermission(SlatePermissions.CRATE_RENAME) ||
                    it.sender.hasPermission(SlatePermissions.CRATE_DELETE) ||
                    it.sender.hasPermission(SlatePermissions.CRATE_GET) ||
                    it.sender.hasPermission(SlatePermissions.CRATE_SET) ||
                    it.sender.hasPermission(SlatePermissions.CRATE_UNSET) ||
                    it.sender.hasPermission(SlatePermissions.CRATE_ADD_REWARD) ||
                    it.sender.hasPermission(SlatePermissions.CRATE_CHANCES) ||
                    it.sender.hasPermission(SlatePermissions.CRATE_VALIDATE) ||
                    it.sender.hasPermission(SlatePermissions.CRATE_SIMULATE)
            }
            .then(listNode())
            .then(infoNode())
            .then(createNode())
            .then(cloneNode())
            .then(renameNode())
            .then(deleteNode("delete"))
            .then(deleteNode("remove"))
            .then(getNode())
            .then(setNode())
            .then(unsetNode())
            .then(addRewardNode())
            .then(chancesNode())
            .then(validateNode())
            .then(simulateNode())
            .build()

    private fun listNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("list")
            .requires { hasPermission(it, SlatePermissions.CRATE_LIST) }
            .executes { context ->
                val crates = SlateAPI.crateManager()?.all().orEmpty().sortedBy { it.id }
                if (crates.isEmpty()) {
                    CommandMessages.chat(context.source.sender, MessageKeys.LIST_EMPTY)
                    return@executes Command.SINGLE_SUCCESS
                }
                val list = crates.joinToString(", ") { it.id }
                CommandMessages.chat(context.source.sender, MessageKeys.LIST_LOADED, mapOf("count" to crates.size, "list" to list))
                Command.SINGLE_SUCCESS
            }
            .build()

    private fun infoNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("info")
            .requires { hasPermission(it, SlatePermissions.CRATE_INFO) }
            .then(
                Commands.argument("crate", StringArgumentType.word())
                    .suggests { context, builder -> CommandSuggestions.suggestCrateIds(context.source, builder) }
                    .executes { context ->
                        val crateId = StringArgumentType.getString(context, "crate")
                        val crate = ProbabilityCommandSupport.requireCrate(crateId)
                        if (crate == null) {
                            CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
                            return@executes 0
                        }
                        CommandMessages.raw(
                            context.source.sender,
                            "<green>${crate.id}</green> <gray>name=${crate.name} mode=${crate.selection.mode}/${crate.selection.structure} rewards=${crate.prizes.size} tiers=${crate.tiers.size}</gray>",
                        )
                        Command.SINGLE_SUCCESS
                    },
            )
            .build()

    private fun createNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("create")
            .requires { hasPermission(it, SlatePermissions.CRATE_CREATE) }
            .then(
                Commands.argument("crate", StringArgumentType.word())
                    .executes { context ->
                        val crateId = StringArgumentType.getString(context, "crate")
                        val existing = ProbabilityCommandSupport.findCrateFile(crateId)
                        if (existing != null) {
                            CommandMessages.raw(context.source.sender, "<red>Crate '${crateId}' already exists.</red>")
                            return@executes 0
                        }
                        val cratesDir = cratesDir() ?: return@executes 0
                        val file = File(cratesDir, "${crateId}.yml")
                        val yaml = YamlConfiguration().apply {
                            set("crate-version", 1)
                            set("name", crateId)
                            set("selection.mode", "weighted")
                            set("selection.structure", "flat")
                            set("requiredKeys", 1)
                            set("key.material", "TRIPWIRE_HOOK")
                            set("prizes.default.name", "Default Reward")
                            set("prizes.default.weight", 1)
                            set("prizes.default.messages", listOf("<green>You won ${crateId}.</green>"))
                        }
                        val result = ProbabilityCommandSupport.saveAndReload(file, yaml)
                        if (!result.success) {
                            CommandMessages.raw(context.source.sender, "<red>${result.message ?: "Failed to create crate."}</red>")
                            return@executes 0
                        }
                        CommandMessages.chat(context.source.sender, MessageKeys.CRATE_CREATED, mapOf("crate" to crateId))
                        Command.SINGLE_SUCCESS
                    },
            )
            .build()

    private fun cloneNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("clone")
            .requires { hasPermission(it, SlatePermissions.CRATE_CLONE) }
            .then(
                Commands.argument("source", StringArgumentType.word())
                    .suggests { context, builder -> CommandSuggestions.suggestCrateIds(context.source, builder) }
                    .then(
                        Commands.argument("target", StringArgumentType.word())
                            .executes { context ->
                                val sourceId = StringArgumentType.getString(context, "source")
                                val targetId = StringArgumentType.getString(context, "target")
                                val sourceFile = ProbabilityCommandSupport.findCrateFile(sourceId)
                                if (sourceFile == null) {
                                    CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to sourceId))
                                    return@executes 0
                                }
                                if (ProbabilityCommandSupport.findCrateFile(targetId) != null) {
                                    CommandMessages.raw(context.source.sender, "<red>Crate '${targetId}' already exists.</red>")
                                    return@executes 0
                                }
                                val targetFile = File(sourceFile.parentFile, "${targetId}.yml")
                                Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                                val summary = SlateAPI.crateManager()?.reload()
                                if (summary?.fileOutcomes?.get(targetFile.path)?.status != so.alaz.slate.crate.ReloadFileStatus.UPDATED) {
                                    CommandMessages.raw(context.source.sender, "<red>Cloned file but reload failed for '${targetId}'.</red>")
                                    return@executes 0
                                }
                                CommandMessages.chat(
                                    context.source.sender,
                                    MessageKeys.CRATE_CLONED,
                                    mapOf("source" to sourceId, "target" to targetId),
                                )
                                Command.SINGLE_SUCCESS
                            },
                    ),
            )
            .build()

    private fun renameNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("rename")
            .requires { hasPermission(it, SlatePermissions.CRATE_RENAME) }
            .then(
                Commands.argument("old", StringArgumentType.word())
                    .suggests { context, builder -> CommandSuggestions.suggestCrateIds(context.source, builder) }
                    .then(
                        Commands.argument("new", StringArgumentType.word())
                            .executes { context ->
                                val oldId = StringArgumentType.getString(context, "old")
                                val newId = StringArgumentType.getString(context, "new")
                                val file = ProbabilityCommandSupport.findCrateFile(oldId)
                                if (file == null) {
                                    CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to oldId))
                                    return@executes 0
                                }
                                if (ProbabilityCommandSupport.findCrateFile(newId) != null) {
                                    CommandMessages.raw(context.source.sender, "<red>Crate '${newId}' already exists.</red>")
                                    return@executes 0
                                }
                                val target = File(file.parentFile, "${newId}.yml")
                                Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                                val summary = SlateAPI.crateManager()?.reload()
                                val updated = summary?.fileOutcomes?.get(target.path)?.status == so.alaz.slate.crate.ReloadFileStatus.UPDATED
                                if (!updated) {
                                    CommandMessages.raw(context.source.sender, "<red>Renamed file but reload failed for '${newId}'.</red>")
                                    return@executes 0
                                }
                                CommandMessages.chat(
                                    context.source.sender,
                                    MessageKeys.CRATE_RENAMED,
                                    mapOf("old" to oldId, "new" to newId),
                                )
                                Command.SINGLE_SUCCESS
                            },
                    ),
            )
            .build()

    private fun deleteNode(literal: String): LiteralCommandNode<CommandSourceStack> =
        Commands.literal(literal)
            .requires { hasPermission(it, SlatePermissions.CRATE_DELETE) }
            .then(
                Commands.argument("crate", StringArgumentType.word())
                    .suggests { context, builder -> CommandSuggestions.suggestCrateIds(context.source, builder) }
                    .executes { context ->
                        CommandMessages.chat(context.source.sender, MessageKeys.CRATE_CONFIRM_REQUIRED)
                        0
                    }
                    .then(
                        Commands.argument("flags", StringArgumentType.greedyString())
                            .suggests { _, builder -> suggestFlags(builder, deleteFlags) }
                            .executes { context ->
                                val flags = CommandFlagUtil.tokenize(StringArgumentType.getString(context, "flags"))
                                if (!CommandFlagUtil.hasFlag(flags, "--confirm")) {
                                    CommandMessages.chat(context.source.sender, MessageKeys.CRATE_CONFIRM_REQUIRED)
                                    return@executes 0
                                }
                                val crateId = StringArgumentType.getString(context, "crate")
                                val file = ProbabilityCommandSupport.findCrateFile(crateId)
                                if (file == null) {
                                    CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
                                    return@executes 0
                                }
                                if (!file.delete()) {
                                    CommandMessages.raw(context.source.sender, "<red>Failed to delete crate file for '${crateId}'.</red>")
                                    return@executes 0
                                }
                                SlateAPI.crateManager()?.reload()
                                CommandMessages.chat(context.source.sender, MessageKeys.CRATE_DELETED, mapOf("crate" to crateId))
                                Command.SINGLE_SUCCESS
                            },
                    ),
            )
            .build()

    private fun getNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("get")
            .requires { hasPermission(it, SlatePermissions.CRATE_GET) }
            .then(
                Commands.argument("crate", StringArgumentType.word())
                    .suggests { context, builder -> CommandSuggestions.suggestCrateIds(context.source, builder) }
                    .then(
                        Commands.argument("path", StringArgumentType.word())
                            .suggests { context, builder -> suggestPaths(builder) }
                            .executes { context ->
                                val crateId = StringArgumentType.getString(context, "crate")
                                val path = StringArgumentType.getString(context, "path")
                                val loaded = ProbabilityCommandSupport.loadCrateFile(crateId)
                                if (loaded == null) {
                                    CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
                                    return@executes 0
                                }
                                val value = loaded.second.get(path)
                                CommandMessages.chat(
                                    context.source.sender,
                                    MessageKeys.CRATE_PATH_VALUE,
                                    mapOf("path" to path, "value" to renderYamlValue(value)),
                                )
                                Command.SINGLE_SUCCESS
                            },
                    ),
            )
            .build()

    private fun setNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("set")
            .requires { hasPermission(it, SlatePermissions.CRATE_SET) }
            .then(
                Commands.argument("crate", StringArgumentType.word())
                    .suggests { context, builder -> CommandSuggestions.suggestCrateIds(context.source, builder) }
                    .then(
                        Commands.argument("path", StringArgumentType.word())
                            .suggests { context, builder -> suggestPaths(builder) }
                            .then(
                                Commands.argument("value", StringArgumentType.greedyString())
                                    .executes { context ->
                                        val crateId = StringArgumentType.getString(context, "crate")
                                        val path = StringArgumentType.getString(context, "path")
                                        val rawValue = StringArgumentType.getString(context, "value")
                                        val crate = ProbabilityCommandSupport.requireCrate(crateId)
                                        if (crate == null) {
                                            CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
                                            return@executes 0
                                        }
                                        val mutability = ProbabilityCommandSupport.mutability(crate)
                                        if (!mutability.allowed) {
                                            CommandMessages.raw(context.source.sender, "<red>${mutability.warning ?: "This crate cannot be edited."}</red>")
                                            return@executes 0
                                        }
                                        val loaded = ProbabilityCommandSupport.loadCrateFile(crateId)
                                        if (loaded == null) {
                                            CommandMessages.raw(context.source.sender, "<red>Crate file for '${crateId}' is missing.</red>")
                                            return@executes 0
                                        }
                                        val (file, yaml) = loaded
                                        yaml.set(path, parseYamlValue(rawValue))
                                        val reloadResult = ProbabilityCommandSupport.saveAndReload(file, yaml)
                                        if (!reloadResult.success) {
                                            CommandMessages.raw(context.source.sender, "<red>${reloadResult.message ?: "Reload failed."}</red>")
                                            return@executes 0
                                        }
                                        CommandMessages.chat(
                                            context.source.sender,
                                            MessageKeys.CRATE_PATH_SET,
                                            mapOf("path" to path, "crate" to crateId),
                                        )
                                        Command.SINGLE_SUCCESS
                                    },
                            ),
                    ),
            )
            .build()

    private fun unsetNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("unset")
            .requires { hasPermission(it, SlatePermissions.CRATE_UNSET) }
            .then(
                Commands.argument("crate", StringArgumentType.word())
                    .suggests { context, builder -> CommandSuggestions.suggestCrateIds(context.source, builder) }
                    .then(
                        Commands.argument("path", StringArgumentType.word())
                            .suggests { context, builder -> suggestPaths(builder) }
                            .executes { context ->
                                val crateId = StringArgumentType.getString(context, "crate")
                                val path = StringArgumentType.getString(context, "path")
                                val crate = ProbabilityCommandSupport.requireCrate(crateId)
                                if (crate == null) {
                                    CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
                                    return@executes 0
                                }
                                val mutability = ProbabilityCommandSupport.mutability(crate)
                                if (!mutability.allowed) {
                                    CommandMessages.raw(context.source.sender, "<red>${mutability.warning ?: "This crate cannot be edited."}</red>")
                                    return@executes 0
                                }
                                val loaded = ProbabilityCommandSupport.loadCrateFile(crateId)
                                if (loaded == null) {
                                    CommandMessages.raw(context.source.sender, "<red>Crate file for '${crateId}' is missing.</red>")
                                    return@executes 0
                                }
                                val (file, yaml) = loaded
                                yaml.set(path, null)
                                val reloadResult = ProbabilityCommandSupport.saveAndReload(file, yaml)
                                if (!reloadResult.success) {
                                    CommandMessages.raw(context.source.sender, "<red>${reloadResult.message ?: "Reload failed."}</red>")
                                    return@executes 0
                                }
                                CommandMessages.chat(
                                    context.source.sender,
                                    MessageKeys.CRATE_PATH_UNSET,
                                    mapOf("path" to path, "crate" to crateId),
                                )
                                Command.SINGLE_SUCCESS
                            },
                    ),
            )
            .build()

    private fun addRewardNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("addreward")
            .requires { hasPermission(it, SlatePermissions.CRATE_ADD_REWARD) }
            .then(
                Commands.argument("crate", StringArgumentType.word())
                    .suggests { context, builder -> CommandSuggestions.suggestCrateIds(context.source, builder) }
                    .then(addRewardHandNode())
                    .then(addRewardInventoryNode())
                    .then(addRewardItemNode()),
            )
            .build()

    private fun addRewardHandNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("hand")
            .then(
                Commands.argument("reward", StringArgumentType.word())
                    .executes { context -> addRewardFromHand(context, null, null) }
                    .then(
                        Commands.literal("tier")
                            .then(
                                Commands.argument("tierValue", StringArgumentType.word())
                                    .suggests { context, builder -> suggestTierIds(context, builder) }
                                    .executes { context ->
                                        addRewardFromHand(
                                            context,
                                            StringArgumentType.getString(context, "tierValue"),
                                            null,
                                        )
                                    }
                                    .then(
                                        Commands.literal("value")
                                            .then(
                                                Commands.argument("number", DoubleArgumentType.doubleArg(0.000001))
                                                    .executes { context ->
                                                        addRewardFromHand(
                                                            context,
                                                            StringArgumentType.getString(context, "tierValue"),
                                                            DoubleArgumentType.getDouble(context, "number"),
                                                        )
                                                    },
                                            ),
                                    ),
                            ),
                    )
                    .then(
                        Commands.literal("value")
                            .then(
                                Commands.argument("number", DoubleArgumentType.doubleArg(0.000001))
                                    .executes { context ->
                                        addRewardFromHand(context, null, DoubleArgumentType.getDouble(context, "number"))
                                    },
                            ),
                    ),
            )
            .build()

    private fun addRewardInventoryNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("inventory")
            .then(
                Commands.argument("prefix", StringArgumentType.word())
                    .executes { context -> addRewardsFromInventory(context, null, null) }
                    .then(
                        Commands.literal("tier")
                            .then(
                                Commands.argument("tierValue", StringArgumentType.word())
                                    .suggests { context, builder -> suggestTierIds(context, builder) }
                                    .executes { context ->
                                        addRewardsFromInventory(
                                            context,
                                            StringArgumentType.getString(context, "tierValue"),
                                            null,
                                        )
                                    }
                                    .then(
                                        Commands.literal("value")
                                            .then(
                                                Commands.argument("number", DoubleArgumentType.doubleArg(0.000001))
                                                    .executes { context ->
                                                        addRewardsFromInventory(
                                                            context,
                                                            StringArgumentType.getString(context, "tierValue"),
                                                            DoubleArgumentType.getDouble(context, "number"),
                                                        )
                                                    },
                                            ),
                                    ),
                            ),
                    )
                    .then(
                        Commands.literal("value")
                            .then(
                                Commands.argument("number", DoubleArgumentType.doubleArg(0.000001))
                                    .executes { context ->
                                        addRewardsFromInventory(context, null, DoubleArgumentType.getDouble(context, "number"))
                                    },
                            ),
                    ),
            )
            .build()

    private fun addRewardItemNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("item")
            .then(
                Commands.argument("reward", StringArgumentType.word())
                    .then(
                        Commands.argument("item", StringArgumentType.greedyString())
                            .executes { context ->
                                val itemText = StringArgumentType.getString(context, "item")
                                addRewardFromItemString(context, itemText, null, null)
                            },
                    ),
            )
            .build()

    private fun addRewardFromHand(
        context: CommandContext<CommandSourceStack>,
        tier: String?,
        value: Double?,
    ): Int {
        val player = context.source.sender as? Player
        if (player == null) {
            CommandMessages.chat(context.source.sender, MessageKeys.COMMON_ONLY_PLAYERS, mapOf("command" to "/sla crate addreward hand"))
            return 0
        }
        val held = player.inventory.itemInMainHand
        if (held.type.isAir) {
            CommandMessages.raw(player, "<red>Hold an item in your main hand.</red>")
            return 0
        }
        val rewardId = StringArgumentType.getString(context, "reward")
        return addSerializedReward(
            source = context.source,
            crateId = StringArgumentType.getString(context, "crate"),
            rewardId = rewardId,
            stack = held.clone(),
            tier = tier,
            value = value,
        )
    }

    private fun addRewardsFromInventory(
        context: CommandContext<CommandSourceStack>,
        tier: String?,
        value: Double?,
    ): Int {
        val player = context.source.sender as? Player
        if (player == null) {
            CommandMessages.chat(context.source.sender, MessageKeys.COMMON_ONLY_PLAYERS, mapOf("command" to "/sla crate addreward inventory"))
            return 0
        }
        val prefix = StringArgumentType.getString(context, "prefix")
        val crateId = StringArgumentType.getString(context, "crate")
        val crate = ProbabilityCommandSupport.requireCrate(crateId)
        if (crate == null) {
            CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
            return 0
        }
        val mutability = ProbabilityCommandSupport.mutability(crate)
        if (!mutability.allowed) {
            CommandMessages.raw(context.source.sender, "<red>${mutability.warning ?: "This crate cannot be edited."}</red>")
            return 0
        }
        val loaded = ProbabilityCommandSupport.loadCrateFile(crateId)
        if (loaded == null) {
            CommandMessages.raw(context.source.sender, "<red>Crate file for '${crateId}' is missing.</red>")
            return 0
        }
        val (file, yaml) = loaded
        val items = player.inventory.contents.filterNotNull().filter { !it.type.isAir }
        if (items.isEmpty()) {
            CommandMessages.raw(context.source.sender, "<red>Your inventory is empty.</red>")
            return 0
        }
        val existing = yaml.getConfigurationSection("prizes")?.getKeys(false).orEmpty().toMutableSet()
        var added = 0
        items.forEachIndexed { index, stack ->
            val rewardId = uniqueRewardId(existing, "${prefix}_${index + 1}")
            val applied = applyRewardToYaml(
                yaml = yaml,
                crate = crate,
                rewardId = rewardId,
                stack = stack.clone(),
                tier = tier,
                value = value,
            )
            if (applied) {
                existing += rewardId
                added++
            }
        }
        if (added == 0) {
            return 0
        }
        val reloadResult = ProbabilityCommandSupport.saveAndReload(file, yaml)
        if (!reloadResult.success) {
            CommandMessages.raw(context.source.sender, "<red>${reloadResult.message ?: "Reload failed."}</red>")
            return 0
        }
        CommandMessages.chat(
            context.source.sender,
            MessageKeys.CRATE_ADDREWARD_BULK_ADDED,
            mapOf("count" to added, "crate" to crateId),
        )
        return Command.SINGLE_SUCCESS
    }

    private fun addRewardFromItemString(
        context: CommandContext<CommandSourceStack>,
        itemText: String,
        tier: String?,
        value: Double?,
    ): Int {
        val stack = parseMinecraftItem(itemText)
        if (stack == null) {
            CommandMessages.raw(context.source.sender, "<red>Could not parse item string.</red>")
            return 0
        }
        return addSerializedReward(
            source = context.source,
            crateId = StringArgumentType.getString(context, "crate"),
            rewardId = StringArgumentType.getString(context, "reward"),
            stack = stack,
            tier = tier,
            value = value,
        )
    }

    private fun addSerializedReward(
        source: CommandSourceStack,
        crateId: String,
        rewardId: String,
        stack: ItemStack,
        tier: String?,
        value: Double?,
    ): Int {
        val crate = ProbabilityCommandSupport.requireCrate(crateId)
        if (crate == null) {
            CommandMessages.chat(source.sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
            return 0
        }
        val mutability = ProbabilityCommandSupport.mutability(crate)
        if (!mutability.allowed) {
            CommandMessages.raw(source.sender, "<red>${mutability.warning ?: "This crate cannot be edited."}</red>")
            return 0
        }
        val loaded = ProbabilityCommandSupport.loadCrateFile(crateId)
        if (loaded == null) {
            CommandMessages.raw(source.sender, "<red>Crate file for '${crateId}' is missing.</red>")
            return 0
        }
        val (file, yaml) = loaded
        val existing = yaml.getConfigurationSection("prizes")?.getKeys(false).orEmpty()
        if (existing.contains(rewardId)) {
            CommandMessages.raw(source.sender, "<red>Reward '${rewardId}' already exists in '${crateId}'.</red>")
            return 0
        }
        val applied = applyRewardToYaml(
            yaml = yaml,
            crate = crate,
            rewardId = rewardId,
            stack = stack,
            tier = tier,
            value = value,
        )
        if (!applied) {
            return 0
        }
        val reloadResult = ProbabilityCommandSupport.saveAndReload(file, yaml)
        if (!reloadResult.success) {
            CommandMessages.raw(source.sender, "<red>${reloadResult.message ?: "Reload failed."}</red>")
            return 0
        }
        CommandMessages.chat(
            source.sender,
            MessageKeys.CRATE_ADDREWARD_ADDED,
            mapOf("reward" to rewardId, "crate" to crateId),
        )
        return Command.SINGLE_SUCCESS
    }

    private fun applyRewardToYaml(
        yaml: YamlConfiguration,
        crate: CrateDefinition,
        rewardId: String,
        stack: ItemStack,
        tier: String?,
        value: Double?,
    ): Boolean {
        if (crate.selection.structure == SelectionStructure.TIERS) {
            if (tier.isNullOrBlank()) {
                return false
            }
            if (!crate.tiers.containsKey(tier)) {
                return false
            }
            yaml.set("prizes.${rewardId}.tier", tier)
        }
        when (crate.selection.mode) {
            SelectionMode.WEIGHTED -> {
                val numeric = value?.toInt() ?: 1
                if (numeric <= 0) return false
                yaml.set("prizes.${rewardId}.weight", numeric)
                yaml.set("prizes.${rewardId}.chance", null)
            }

            SelectionMode.PERCENT -> {
                if (value == null) {
                    return false
                }
                yaml.set("prizes.${rewardId}.chance", value)
                yaml.set("prizes.${rewardId}.weight", null)
            }
        }
        val serialized = ItemUtil.serializeItemStack(stack)
        if (serialized == null) {
            return false
        }
        yaml.set("prizes.${rewardId}.name", rewardId)
        yaml.set("prizes.${rewardId}.items", listOf(mapOf("serialized" to serialized, "amount" to stack.amount)))
        return true
    }

    private fun uniqueRewardId(existing: Set<String>, base: String): String {
        if (!existing.contains(base)) return base
        var idx = 2
        while (existing.contains("${base}_$idx")) {
            idx++
        }
        return "${base}_$idx"
    }

    private fun parseMinecraftItem(raw: String): ItemStack? {
        val factory = Bukkit.getItemFactory()
        val method = factory.javaClass.methods.firstOrNull {
            it.name == "createItemStack" &&
                it.parameterCount == 1 &&
                it.parameterTypes[0] == String::class.java
        } ?: return null
        return runCatching { method.invoke(factory, raw) as? ItemStack }.getOrNull()
    }

    private fun chancesNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("chances")
            .requires { hasPermission(it, SlatePermissions.CRATE_CHANCES) }
            .then(
                Commands.argument("crate", StringArgumentType.word())
                    .suggests { context, builder ->
                        CommandSuggestions.suggestCrateIds(context.source, builder)
                    }
                    .executes { context ->
                        val crateId = StringArgumentType.getString(context, "crate")
                        val crate = ProbabilityCommandSupport.requireCrate(crateId)
                        if (crate == null) {
                            CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
                            return@executes 0
                        }
                        val table = ProbabilityEngine().buildTable(crate)
                        CommandMessages.chat(
                            context.source.sender,
                            MessageKeys.CRATE_CHANCES_HEADER,
                            mapOf("crate" to crate.id, "mode" to table.mode, "structure" to table.structure, "total" to table.totalConfiguredValue),
                        )
                        if (table.emptyChance > 0.0) {
                            CommandMessages.chat(
                                context.source.sender,
                                MessageKeys.CRATE_CHANCES_EMPTY,
                                mapOf("empty" to ChanceFormatter.display(table.emptyChance, hidden = false)),
                            )
                        }
                        table.rows.sortedByDescending { it.effectiveChance }.forEach { row ->
                            val tierPart = if (table.structure == SelectionStructure.TIERS) {
                                " tier=${row.tierId} tierChance=${ChanceFormatter.percent(row.tierChance ?: 0.0)} local=${ChanceFormatter.percent(row.localChance)}"
                            } else {
                                ""
                            }
                            CommandMessages.chat(
                                context.source.sender,
                                MessageKeys.CRATE_CHANCES_LINE,
                                mapOf(
                                    "prize" to row.prize.id,
                                    "configured" to row.configuredValue,
                                    "tierPart" to tierPart,
                                    "effective" to ChanceFormatter.display(row.effectiveChance, hidden = false),
                                ),
                            )
                        }
                        Command.SINGLE_SUCCESS
                    },
            )
            .build()

    private fun validateNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("validate")
            .requires { hasPermission(it, SlatePermissions.CRATE_VALIDATE) }
            .then(
                Commands.argument("crate", StringArgumentType.word())
                    .suggests { context, builder ->
                        CommandSuggestions.suggestCrateIds(context.source, builder)
                    }
                    .executes { context ->
                        val crateId = StringArgumentType.getString(context, "crate")
                        val loaded = ProbabilityCommandSupport.loadCrateFile(crateId)
                        if (loaded == null) {
                            CommandMessages.chat(context.source.sender, MessageKeys.CRATE_VALIDATE_ERRORS, mapOf("crate" to crateId, "count" to 1))
                            return@executes 0
                        }
                        val (file, yaml) = loaded
                        val result = ConfigValidator().validate(file, yaml)
                        if (result.isValid) {
                            CommandMessages.chat(context.source.sender, MessageKeys.CRATE_VALIDATE_OK, mapOf("crate" to crateId))
                            return@executes Command.SINGLE_SUCCESS
                        }
                        CommandMessages.chat(context.source.sender, MessageKeys.CRATE_VALIDATE_ERRORS, mapOf("crate" to crateId, "count" to result.errors.size))
                        result.errors.take(10).forEach { err ->
                            CommandMessages.chat(context.source.sender, MessageKeys.CRATE_VALIDATE_ERROR_LINE, mapOf("path" to err.keyPath, "message" to err.message))
                        }
                        Command.SINGLE_SUCCESS
                    },
            )
            .build()

    private fun simulateNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("simulate")
            .requires { hasPermission(it, SlatePermissions.CRATE_SIMULATE) }
            .then(
                Commands.argument("crate", StringArgumentType.word())
                    .suggests { context, builder ->
                        CommandSuggestions.suggestCrateIds(context.source, builder)
                    }
                    .then(
                        Commands.argument("rolls", IntegerArgumentType.integer(1))
                            .executes { context ->
                                val crateId = StringArgumentType.getString(context, "crate")
                                val requestedRolls = IntegerArgumentType.getInteger(context, "rolls")
                                val crate = ProbabilityCommandSupport.requireCrate(crateId)
                                if (crate == null) {
                                    CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
                                    return@executes 0
                                }
                                val rolls = requestedRolls.coerceAtMost(MAX_SIMULATION_ROLLS)
                                if (requestedRolls > MAX_SIMULATION_ROLLS) {
                                    CommandMessages.chat(context.source.sender, MessageKeys.CRATE_SIM_CAP, mapOf("max" to MAX_SIMULATION_ROLLS))
                                }
                                val scope = SlateAPI.pluginScope()
                                val plugin = SlateAPI.plugin()
                                if (scope == null || plugin == null) {
                                    CommandMessages.notInitialized(context.source.sender)
                                    return@executes 0
                                }
                                CommandMessages.chat(context.source.sender, MessageKeys.CRATE_SIM_START, mapOf("rolls" to rolls, "crate" to crate.id))
                                scope.launch {
                                    val engine = ProbabilityEngine()
                                    val counts = mutableMapOf<String, Int>()
                                    var empty = 0
                                    repeat(rolls) {
                                        val pick = engine.roll(crate).prize
                                        if (pick == null) {
                                            empty++
                                        } else {
                                            counts[pick.id] = (counts[pick.id] ?: 0) + 1
                                        }
                                    }
                                    val total = rolls.toDouble()
                                    val lines = counts.entries
                                        .sortedByDescending { it.value }
                                        .take(10)
                                        .map { (id, count) ->
                                            val chance = count.toDouble() / total
                                            "$id=$count (${ChanceFormatter.percent(chance)})"
                                        }
                                    withContext(plugin.mainDispatcher) {
                                        CommandMessages.chat(context.source.sender, MessageKeys.CRATE_SIM_DONE, mapOf("crate" to crate.id, "rolls" to rolls))
                                        lines.forEach { line ->
                                            CommandMessages.chat(context.source.sender, MessageKeys.CRATE_SIM_LINE, mapOf("line" to line))
                                        }
                                        if (empty > 0) {
                                            val chance = empty.toDouble() / total
                                            CommandMessages.chat(context.source.sender, MessageKeys.CRATE_SIM_LINE, mapOf("line" to "empty=$empty (${ChanceFormatter.percent(chance)})"))
                                        }
                                    }
                                }
                                Command.SINGLE_SUCCESS
                            },
                    ),
            )
            .build()

    private fun hasPermission(source: CommandSourceStack, permission: String): Boolean =
        source.sender.hasPermission(permission) ||
            source.sender.hasPermission(SlatePermissions.ADMIN_PROBABILITY) ||
            source.sender.hasPermission(SlatePermissions.ADMIN_CRATE)

    private fun suggestFlags(
        builder: com.mojang.brigadier.suggestion.SuggestionsBuilder,
        flags: List<String>,
    ): java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> {
        val token = builder.remaining.substringAfterLast(' ', builder.remaining).lowercase()
        flags.filter { token.isBlank() || it.startsWith(token) }.forEach(builder::suggest)
        return builder.buildFuture()
    }

    private fun suggestPaths(
        builder: com.mojang.brigadier.suggestion.SuggestionsBuilder,
    ): java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> {
        val token = builder.remaining.lowercase()
        editablePaths
            .asSequence()
            .filter { token.isBlank() || it.lowercase().startsWith(token) }
            .forEach(builder::suggest)
        return builder.buildFuture()
    }

    private fun suggestTierIds(
        context: CommandContext<CommandSourceStack>,
        builder: com.mojang.brigadier.suggestion.SuggestionsBuilder,
    ): java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> {
        val crateId = runCatching { StringArgumentType.getString(context, "crate") }.getOrNull()
        val crate = crateId?.let { ProbabilityCommandSupport.requireCrate(it) }
        val token = builder.remaining.lowercase()
        crate?.tiers?.keys
            ?.sorted()
            ?.asSequence()
            ?.filter { token.isBlank() || it.lowercase().startsWith(token) }
            ?.forEach(builder::suggest)
        return builder.buildFuture()
    }

    private fun parseYamlValue(raw: String): Any? {
        val trimmed = raw.trim()
        if (trimmed.equals("null", true) || trimmed == "~") return null
        val temp = YamlConfiguration()
        return runCatching {
            temp.loadFromString("value: $raw")
            temp.get("value")
        }.getOrElse { raw }
    }

    private fun renderYamlValue(value: Any?): String = when (value) {
        null -> "null"
        is List<*> -> value.joinToString(prefix = "[", postfix = "]")
        is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { "${it.key}=${it.value}" }
        else -> value.toString()
    }

    private fun cratesDir(): File? {
        val plugin = SlateAPI.plugin()
        if (plugin == null) return null
        return File(plugin.dataFolder, "crates").apply { mkdirs() }
    }
}
