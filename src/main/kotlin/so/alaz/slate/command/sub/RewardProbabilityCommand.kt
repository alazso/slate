package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.message.MessageKeys
import so.alaz.slate.probability.SelectionMode
import so.alaz.slate.probability.SelectionStructure

object RewardProbabilityCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("reward")
            .requires {
                it.sender.hasPermission(SlatePermissions.ADMIN_REWARD) ||
                    it.sender.hasPermission(SlatePermissions.ADMIN_PROBABILITY) ||
                    it.sender.hasPermission(SlatePermissions.REWARD_WEIGHT) ||
                    it.sender.hasPermission(SlatePermissions.REWARD_CHANCE) ||
                    it.sender.hasPermission(SlatePermissions.REWARD_TIER)
            }
            .then(weightNode())
            .then(chanceNode())
            .then(tierNode())
            .build()

    private fun weightNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("weight")
            .requires { hasPermission(it, SlatePermissions.REWARD_WEIGHT) }
            .then(
                Commands.argument("crate", StringArgumentType.word())
                    .suggests { context, builder ->
                        CommandSuggestions.suggestCrateIds(context.source, builder)
                    }
                    .then(
                        Commands.argument("reward", StringArgumentType.word())
                            .then(
                                Commands.argument("weight", IntegerArgumentType.integer(1))
                                    .executes { context ->
                                        val crateId = StringArgumentType.getString(context, "crate")
                                        val rewardId = StringArgumentType.getString(context, "reward")
                                        val weight = IntegerArgumentType.getInteger(context, "weight")
                                        val crate = ProbabilityCommandSupport.requireCrate(crateId)
                                        if (crate == null) {
                                            CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
                                            return@executes 0
                                        }
                                        if (crate.selection.mode != SelectionMode.WEIGHTED) {
                                            CommandMessages.chat(context.source.sender, MessageKeys.REWARD_WEIGHT_ONLY)
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
                                        val state = RewardMutationSupport.fromYaml(yaml)
                                        val mutation = RewardMutationSupport.setRewardWeight(state, crate, rewardId, weight)
                                        if (mutation.error == RewardMutationSupport.Error.UNKNOWN_REWARD) {
                                            CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_REWARD, mapOf("reward" to rewardId))
                                            return@executes 0
                                        }
                                        if (mutation.error == RewardMutationSupport.Error.INVALID_MODE) {
                                            CommandMessages.chat(context.source.sender, MessageKeys.REWARD_WEIGHT_ONLY)
                                            return@executes 0
                                        }
                                        if (!mutation.success) {
                                            return@executes 0
                                        }
                                        RewardMutationSupport.writeToYaml(state, yaml)
                                        val reloadResult = ProbabilityCommandSupport.saveAndReload(file, yaml)
                                        if (!reloadResult.success) {
                                            CommandMessages.raw(context.source.sender, "<red>${reloadResult.message ?: "Reload failed."}</red>")
                                            return@executes 0
                                        }
                                        CommandMessages.chat(context.source.sender, MessageKeys.REWARD_WEIGHT_SET, mapOf("reward" to rewardId, "weight" to weight, "crate" to crateId))
                                        Command.SINGLE_SUCCESS
                                    },
                            ),
                    ),
            )
            .build()

    private fun chanceNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("chance")
            .requires { hasPermission(it, SlatePermissions.REWARD_CHANCE) }
            .then(
                Commands.argument("crate", StringArgumentType.word())
                    .suggests { context, builder ->
                        CommandSuggestions.suggestCrateIds(context.source, builder)
                    }
                    .then(
                        Commands.argument("reward", StringArgumentType.word())
                            .then(
                                Commands.argument("chance", DoubleArgumentType.doubleArg(0.000001, 100.0))
                                    .executes { context ->
                                        val crateId = StringArgumentType.getString(context, "crate")
                                        val rewardId = StringArgumentType.getString(context, "reward")
                                        val chance = DoubleArgumentType.getDouble(context, "chance")
                                        val crate = ProbabilityCommandSupport.requireCrate(crateId)
                                        if (crate == null) {
                                            CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
                                            return@executes 0
                                        }
                                        if (crate.selection.mode != SelectionMode.PERCENT || crate.selection.structure != SelectionStructure.FLAT) {
                                            CommandMessages.chat(context.source.sender, MessageKeys.REWARD_CHANCE_ONLY)
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
                                        val state = RewardMutationSupport.fromYaml(yaml)
                                        val mutation = RewardMutationSupport.setRewardChance(state, crate, rewardId, chance)
                                        if (mutation.error == RewardMutationSupport.Error.UNKNOWN_REWARD) {
                                            CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_REWARD, mapOf("reward" to rewardId))
                                            return@executes 0
                                        }
                                        if (mutation.error == RewardMutationSupport.Error.INVALID_MODE) {
                                            CommandMessages.chat(context.source.sender, MessageKeys.REWARD_CHANCE_ONLY)
                                            return@executes 0
                                        }
                                        if (mutation.error == RewardMutationSupport.Error.INVALID_CHANCE) {
                                            CommandMessages.chat(context.source.sender, MessageKeys.REWARD_CHANCE_ONLY)
                                            return@executes 0
                                        }
                                        if (!mutation.success) {
                                            return@executes 0
                                        }
                                        RewardMutationSupport.writeToYaml(state, yaml)
                                        val reloadResult = ProbabilityCommandSupport.saveAndReload(file, yaml)
                                        if (!reloadResult.success) {
                                            CommandMessages.raw(context.source.sender, "<red>${reloadResult.message ?: "Reload failed."}</red>")
                                            return@executes 0
                                        }
                                        CommandMessages.chat(context.source.sender, MessageKeys.REWARD_CHANCE_SET, mapOf("reward" to rewardId, "chance" to chance, "crate" to crateId))
                                        Command.SINGLE_SUCCESS
                                    },
                            ),
                    ),
            )
            .build()

    private fun tierNode(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("tier")
            .requires { hasPermission(it, SlatePermissions.REWARD_TIER) }
            .then(
                Commands.argument("crate", StringArgumentType.word())
                    .suggests { context, builder ->
                        CommandSuggestions.suggestCrateIds(context.source, builder)
                    }
                    .then(
                        Commands.argument("reward", StringArgumentType.word())
                            .then(
                                Commands.argument("tier", StringArgumentType.word())
                                    .executes { context ->
                                        val crateId = StringArgumentType.getString(context, "crate")
                                        val rewardId = StringArgumentType.getString(context, "reward")
                                        val tierId = StringArgumentType.getString(context, "tier")
                                        val crate = ProbabilityCommandSupport.requireCrate(crateId)
                                        if (crate == null) {
                                            CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
                                            return@executes 0
                                        }
                                        if (crate.selection.structure != SelectionStructure.TIERS) {
                                            CommandMessages.chat(context.source.sender, MessageKeys.REWARD_TIER_ONLY)
                                            return@executes 0
                                        }
                                        if (!crate.tiers.containsKey(tierId)) {
                                            CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_TIER, mapOf("tier" to tierId))
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
                                        val state = RewardMutationSupport.fromYaml(yaml)
                                        val mutation = RewardMutationSupport.setRewardTier(state, crate, rewardId, tierId)
                                        when (mutation.error) {
                                            RewardMutationSupport.Error.UNKNOWN_REWARD -> {
                                                CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_REWARD, mapOf("reward" to rewardId))
                                                return@executes 0
                                            }
                                            RewardMutationSupport.Error.INVALID_TIER -> {
                                                CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_TIER, mapOf("tier" to tierId))
                                                return@executes 0
                                            }
                                            RewardMutationSupport.Error.INVALID_MODE -> {
                                                CommandMessages.chat(context.source.sender, MessageKeys.REWARD_TIER_ONLY)
                                                return@executes 0
                                            }
                                            else -> Unit
                                        }
                                        if (!mutation.success) return@executes 0
                                        RewardMutationSupport.writeToYaml(state, yaml)
                                        val reloadResult = ProbabilityCommandSupport.saveAndReload(file, yaml)
                                        if (!reloadResult.success) {
                                            CommandMessages.raw(context.source.sender, "<red>${reloadResult.message ?: "Reload failed."}</red>")
                                            return@executes 0
                                        }
                                        CommandMessages.chat(context.source.sender, MessageKeys.REWARD_TIER_SET, mapOf("reward" to rewardId, "tier" to tierId, "crate" to crateId))
                                        Command.SINGLE_SUCCESS
                                    },
                            ),
                    ),
            )
            .build()

    private fun hasPermission(source: CommandSourceStack, permission: String): Boolean =
        source.sender.hasPermission(permission) ||
            source.sender.hasPermission(SlatePermissions.ADMIN_PROBABILITY) ||
            source.sender.hasPermission(SlatePermissions.ADMIN_REWARD)
}
