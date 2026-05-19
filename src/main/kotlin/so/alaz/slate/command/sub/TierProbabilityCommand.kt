package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.message.MessageKeys
import so.alaz.slate.probability.ChanceFormatter
import so.alaz.slate.probability.ProbabilityEngine
import so.alaz.slate.probability.SelectionStructure

object TierProbabilityCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("tier")
            .requires {
                it.sender.hasPermission(SlatePermissions.ADMIN_PROBABILITY) ||
                    it.sender.hasPermission(SlatePermissions.TIER_LIST) ||
                    it.sender.hasPermission(SlatePermissions.TIER_CREATE) ||
                    it.sender.hasPermission(SlatePermissions.TIER_DELETE) ||
                    it.sender.hasPermission(SlatePermissions.TIER_RENAME) ||
                    it.sender.hasPermission(SlatePermissions.TIER_WEIGHT) ||
                    it.sender.hasPermission(SlatePermissions.TIER_INFO) ||
                    it.sender.hasPermission(SlatePermissions.TIER_REWARDS)
            }
            .then(listNode())
            .then(createNode())
            .then(deleteNode())
            .then(renameNode())
            .then(weightNode())
            .then(infoNode())
            .then(rewardsNode())
            .build()

    private fun listNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("list")
            .requires { hasPermission(it, SlatePermissions.TIER_LIST) }
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
                        if (crate.selection.structure != SelectionStructure.TIERS) {
                            CommandMessages.chat(context.source.sender, MessageKeys.TIER_NOT_TIERED, mapOf("crate" to crateId))
                            return@executes 0
                        }
                        val list = crate.tiers.values.sortedBy { it.id }.joinToString(", ") { "${it.id}(w=${it.weight})" }
                        CommandMessages.chat(context.source.sender, MessageKeys.TIER_LIST, mapOf("crate" to crateId, "list" to list))
                        Command.SINGLE_SUCCESS
                    },
            )
            .build()

    private fun createNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("create")
            .requires { hasPermission(it, SlatePermissions.TIER_CREATE) }
            .then(
                Commands.argument("crate", StringArgumentType.word())
                    .suggests { context, builder ->
                        CommandSuggestions.suggestCrateIds(context.source, builder)
                    }
                    .then(
                        Commands.argument("tier", StringArgumentType.word())
                            .executes { context ->
                                val crateId = StringArgumentType.getString(context, "crate")
                                val tierId = StringArgumentType.getString(context, "tier")
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
                                if (mutability.warning != null) {
                                    CommandMessages.raw(context.source.sender, "<yellow>${mutability.warning}</yellow>")
                                }
                                val loaded = ProbabilityCommandSupport.loadCrateFile(crateId)
                                if (loaded == null) {
                                    CommandMessages.chat(context.source.sender, MessageKeys.CRATE_VALIDATE_ERRORS, mapOf("crate" to crateId, "count" to 1))
                                    return@executes 0
                                }
                                val (file, yaml) = loaded
                                val state = TierMutationSupport.fromYaml(yaml)
                                val mutation = TierMutationSupport.createTier(state, tierId, displayName = tierId, weight = 1)
                                if (!mutation.success) {
                                    CommandMessages.chat(context.source.sender, MessageKeys.TIER_ALREADY_EXISTS, mapOf("tier" to tierId))
                                    return@executes 0
                                }
                                TierMutationSupport.writeToYaml(state, yaml)
                                val reloadResult = ProbabilityCommandSupport.saveAndReload(file, yaml)
                                if (!reloadResult.success) {
                                    CommandMessages.raw(context.source.sender, "<red>${reloadResult.message ?: "Reload failed."}</red>")
                                    return@executes 0
                                }
                                CommandMessages.chat(context.source.sender, MessageKeys.TIER_CREATED, mapOf("tier" to tierId, "crate" to crateId))
                                Command.SINGLE_SUCCESS
                            },
                    ),
            )
            .build()

    private fun deleteNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("delete")
            .requires { hasPermission(it, SlatePermissions.TIER_DELETE) }
            .then(
                Commands.argument("crate", StringArgumentType.word())
                    .suggests { context, builder ->
                        CommandSuggestions.suggestCrateIds(context.source, builder)
                    }
                    .then(
                        Commands.argument("tier", StringArgumentType.word())
                            .executes { context ->
                                val crateId = StringArgumentType.getString(context, "crate")
                                val tierId = StringArgumentType.getString(context, "tier")
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
                                if (mutability.warning != null) {
                                    CommandMessages.raw(context.source.sender, "<yellow>${mutability.warning}</yellow>")
                                }
                                val loaded = ProbabilityCommandSupport.loadCrateFile(crateId)
                                if (loaded == null) {
                                    CommandMessages.chat(context.source.sender, MessageKeys.CRATE_VALIDATE_ERRORS, mapOf("crate" to crateId, "count" to 1))
                                    return@executes 0
                                }
                                val (file, yaml) = loaded
                                val state = TierMutationSupport.fromYaml(yaml)
                                val mutation = TierMutationSupport.deleteTier(state, tierId)
                                if (!mutation.success) {
                                    CommandMessages.chat(
                                        context.source.sender,
                                        MessageKeys.TIER_DELETE_REFERENCED,
                                        mapOf("tier" to tierId, "rewards" to mutation.referencedRewards.joinToString(", ")),
                                    )
                                    return@executes 0
                                }
                                TierMutationSupport.writeToYaml(state, yaml)
                                val reloadResult = ProbabilityCommandSupport.saveAndReload(file, yaml)
                                if (!reloadResult.success) {
                                    CommandMessages.raw(context.source.sender, "<red>${reloadResult.message ?: "Reload failed."}</red>")
                                    return@executes 0
                                }
                                CommandMessages.chat(context.source.sender, MessageKeys.TIER_DELETED, mapOf("tier" to tierId, "crate" to crateId))
                                Command.SINGLE_SUCCESS
                            },
                    ),
            )
            .build()

    private fun renameNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("rename")
            .requires { hasPermission(it, SlatePermissions.TIER_RENAME) }
            .then(
                Commands.argument("crate", StringArgumentType.word())
                    .suggests { context, builder ->
                        CommandSuggestions.suggestCrateIds(context.source, builder)
                    }
                    .then(
                        Commands.argument("old-tier", StringArgumentType.word())
                            .then(
                                Commands.argument("new-tier", StringArgumentType.word())
                                    .executes { context ->
                                        val crateId = StringArgumentType.getString(context, "crate")
                                        val oldTier = StringArgumentType.getString(context, "old-tier")
                                        val newTier = StringArgumentType.getString(context, "new-tier")
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
                                        if (mutability.warning != null) {
                                            CommandMessages.raw(context.source.sender, "<yellow>${mutability.warning}</yellow>")
                                        }
                                        val loaded = ProbabilityCommandSupport.loadCrateFile(crateId)
                                        if (loaded == null) {
                                            CommandMessages.chat(context.source.sender, MessageKeys.CRATE_VALIDATE_ERRORS, mapOf("crate" to crateId, "count" to 1))
                                            return@executes 0
                                        }
                                        val (file, yaml) = loaded
                                        val state = TierMutationSupport.fromYaml(yaml)
                                        val mutation = TierMutationSupport.renameTier(state, oldTier, newTier)
                                        when (mutation.error) {
                                            TierMutationSupport.Error.UNKNOWN_TIER -> {
                                            CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_TIER, mapOf("tier" to oldTier))
                                            return@executes 0
                                        }
                                            TierMutationSupport.Error.TIER_ALREADY_EXISTS -> {
                                            CommandMessages.chat(context.source.sender, MessageKeys.TIER_ALREADY_EXISTS, mapOf("tier" to newTier))
                                            return@executes 0
                                        }
                                            else -> Unit
                                        }
                                        if (!mutation.success) return@executes 0
                                        TierMutationSupport.writeToYaml(state, yaml)
                                        val reloadResult = ProbabilityCommandSupport.saveAndReload(file, yaml)
                                        if (!reloadResult.success) {
                                            CommandMessages.raw(context.source.sender, "<red>${reloadResult.message ?: "Reload failed."}</red>")
                                            return@executes 0
                                        }
                                        CommandMessages.chat(context.source.sender, MessageKeys.TIER_RENAMED, mapOf("oldTier" to oldTier, "newTier" to newTier, "crate" to crateId))
                                        Command.SINGLE_SUCCESS
                                    },
                            ),
                    ),
            )
            .build()

    private fun weightNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("weight")
            .requires { hasPermission(it, SlatePermissions.TIER_WEIGHT) }
            .then(
                Commands.argument("crate", StringArgumentType.word())
                    .suggests { context, builder ->
                        CommandSuggestions.suggestCrateIds(context.source, builder)
                    }
                    .then(
                        Commands.argument("tier", StringArgumentType.word())
                            .then(
                                Commands.argument("weight", IntegerArgumentType.integer(1))
                                    .executes { context ->
                                        val crateId = StringArgumentType.getString(context, "crate")
                                        val tierId = StringArgumentType.getString(context, "tier")
                                        val weight = IntegerArgumentType.getInteger(context, "weight")
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
                                        if (mutability.warning != null) {
                                            CommandMessages.raw(context.source.sender, "<yellow>${mutability.warning}</yellow>")
                                        }
                                        val loaded = ProbabilityCommandSupport.loadCrateFile(crateId)
                                        if (loaded == null) {
                                            CommandMessages.chat(context.source.sender, MessageKeys.CRATE_VALIDATE_ERRORS, mapOf("crate" to crateId, "count" to 1))
                                            return@executes 0
                                        }
                                        val (file, yaml) = loaded
                                        val state = TierMutationSupport.fromYaml(yaml)
                                        val mutation = TierMutationSupport.setTierWeight(state, tierId, weight)
                                        if (mutation.error == TierMutationSupport.Error.UNKNOWN_TIER) {
                                            CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_TIER, mapOf("tier" to tierId))
                                            return@executes 0
                                        }
                                        if (!mutation.success) {
                                            return@executes 0
                                        }
                                        TierMutationSupport.writeToYaml(state, yaml)
                                        val reloadResult = ProbabilityCommandSupport.saveAndReload(file, yaml)
                                        if (!reloadResult.success) {
                                            CommandMessages.raw(context.source.sender, "<red>${reloadResult.message ?: "Reload failed."}</red>")
                                            return@executes 0
                                        }
                                        CommandMessages.chat(context.source.sender, MessageKeys.TIER_WEIGHT_SET, mapOf("tier" to tierId, "weight" to weight, "crate" to crateId))
                                        Command.SINGLE_SUCCESS
                                    },
                            ),
                    ),
            )
            .build()

    private fun infoNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("info")
            .requires { hasPermission(it, SlatePermissions.TIER_INFO) }
            .then(
                Commands.argument("crate", StringArgumentType.word())
                    .suggests { context, builder ->
                        CommandSuggestions.suggestCrateIds(context.source, builder)
                    }
                    .then(
                        Commands.argument("tier", StringArgumentType.word())
                            .executes { context ->
                                val crateId = StringArgumentType.getString(context, "crate")
                                val tierId = StringArgumentType.getString(context, "tier")
                                val crate = ProbabilityCommandSupport.requireCrate(crateId)
                                if (crate == null) {
                                    CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
                                    return@executes 0
                                }
                                val tier = crate.tiers[tierId]
                                if (tier == null) {
                                    CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_TIER, mapOf("tier" to tierId))
                                    return@executes 0
                                }
                                val table = ProbabilityEngine().buildTable(crate)
                                val tierRows = table.rows.filter { it.tierId == tierId }
                                val tierChance = tierRows.firstOrNull()?.tierChance ?: 0.0
                                CommandMessages.chat(
                                    context.source.sender,
                                    MessageKeys.TIER_INFO,
                                    mapOf(
                                        "tier" to tierId,
                                        "name" to tier.displayName,
                                        "weight" to tier.weight,
                                        "chance" to ChanceFormatter.percent(tierChance),
                                        "rewards" to tierRows.size,
                                    ),
                                )
                                Command.SINGLE_SUCCESS
                            },
                    ),
            )
            .build()

    private fun rewardsNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("rewards")
            .requires { hasPermission(it, SlatePermissions.TIER_REWARDS) }
            .then(
                Commands.argument("crate", StringArgumentType.word())
                    .suggests { context, builder ->
                        CommandSuggestions.suggestCrateIds(context.source, builder)
                    }
                    .then(
                        Commands.argument("tier", StringArgumentType.word())
                            .executes { context ->
                                val crateId = StringArgumentType.getString(context, "crate")
                                val tierId = StringArgumentType.getString(context, "tier")
                                val crate = ProbabilityCommandSupport.requireCrate(crateId)
                                if (crate == null) {
                                    CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
                                    return@executes 0
                                }
                                val table = ProbabilityEngine().buildTable(crate)
                                val tierRows = table.rows.filter { it.tierId == tierId }.sortedByDescending { it.effectiveChance }
                                if (tierRows.isEmpty()) {
                                    CommandMessages.chat(context.source.sender, MessageKeys.TIER_REWARDS_EMPTY, mapOf("tier" to tierId))
                                    return@executes Command.SINGLE_SUCCESS
                                }
                                CommandMessages.chat(context.source.sender, MessageKeys.TIER_REWARDS_HEADER, mapOf("tier" to tierId))
                                tierRows.forEach { row ->
                                    CommandMessages.chat(
                                        context.source.sender,
                                        MessageKeys.TIER_REWARD_LINE,
                                        mapOf(
                                            "prize" to row.prize.id,
                                            "local" to ChanceFormatter.percent(row.localChance),
                                            "effective" to ChanceFormatter.percent(row.effectiveChance),
                                        ),
                                    )
                                }
                                Command.SINGLE_SUCCESS
                            },
                    ),
            )
            .build()

    private fun hasPermission(source: CommandSourceStack, permission: String): Boolean =
        source.sender.hasPermission(permission) || source.sender.hasPermission(SlatePermissions.ADMIN_PROBABILITY)
}
