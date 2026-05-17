package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.launch
import org.bukkit.entity.Player
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.display.DisplayTextResolver
import so.alaz.slate.gui.preview.PreviewGui
import so.alaz.slate.probability.ChanceFormatter
import so.alaz.slate.message.MessageKeys
import so.alaz.slate.probability.ProbabilityEngine
import so.alaz.slate.probability.SelectionStructure

object PreviewCommand {
    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("preview")
            .requires { it.sender.hasPermission(SlatePermissions.PLAYER_PREVIEW) }
            .then(
                Commands.argument("crate", StringArgumentType.word())
                    .suggests { context, builder ->
                        CommandSuggestions.suggestCrateIds(context.source, builder)
                    }
                    .executes { context ->
                        val crateId = StringArgumentType.getString(context, "crate")
                        if (!context.source.sender.hasPermission("${SlatePermissions.PREVIEW_CRATE_PREFIX}$crateId")) {
                            CommandMessages.chat(context.source.sender, MessageKeys.PREVIEW_FORBIDDEN, mapOf("crate" to crateId))
                            return@executes 0
                        }
                        val crate = SlateAPI.crateManager()?.byId(crateId)
                        if (crate == null) {
                            CommandMessages.chat(context.source.sender, MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId))
                            return@executes 0
                        }
                        val eligibilityService = SlateAPI.crateEligibilityService()
                        if (eligibilityService == null) {
                            CommandMessages.notInitialized(context.source.sender)
                            return@executes 0
                        }
                        val previewEligibility = eligibilityService.canPreview(context.source.sender, crate)
                        if (!previewEligibility.allowed) {
                            CommandMessages.chat(
                                context.source.sender,
                                MessageKeys.OPEN_FAILED_REASON,
                                mapOf("reason" to CommandMessages.openFailureReason(previewEligibility.failureReason ?: so.alaz.slate.opening.FailureReason.CANCELLED_BY_EVENT)),
                            )
                            return@executes 0
                        }
                        val player = context.source.sender as? Player
                        if (player != null && crate.preview?.enabled == true) {
                            val guiService = SlateAPI.guiService()
                            val scope = SlateAPI.pluginScope()
                            if (guiService != null && scope != null) {
                                scope.launch {
                                    guiService.openGui(player, PreviewGui(crate))
                                }
                                return@executes Command.SINGLE_SUCCESS
                            }
                        }
                        val table = ProbabilityEngine().buildTable(crate)
                        val tiers = crate.tiers.keys.sorted().joinToString(", ").ifBlank { "none" }
                        val firstPrizes = table.rows.sortedByDescending { it.effectiveChance }.take(5)
                        CommandMessages.chat(
                            context.source.sender,
                            MessageKeys.PREVIEW_HEADER,
                            mapOf(
                                "crate" to crate.id,
                                "name" to crate.name,
                                "prizes" to crate.prizes.size,
                                "requiredKeys" to crate.requiredKeys,
                                "cooldown" to crate.cooldown,
                                "mode" to table.mode,
                                "structure" to table.structure,
                            ),
                        )
                        if (table.emptyChance > 0.0) {
                            CommandMessages.chat(
                                context.source.sender,
                                MessageKeys.PREVIEW_EMPTY,
                                mapOf("empty" to ChanceFormatter.percent(table.emptyChance)),
                            )
                        }
                        CommandMessages.chat(context.source.sender, MessageKeys.PREVIEW_TIERS, mapOf("tiers" to tiers))
                        CommandMessages.chat(
                            context.source.sender,
                            MessageKeys.PREVIEW_SUMMARY,
                            mapOf("summary" to DisplayTextResolver.renderCrateTemplate(crate.id, "top=<top_reward> (<top_chance>) rarest=<rarest_reward> (<rarest_chance>)")),
                        )
                        firstPrizes.forEach { row ->
                            val tierPart = if (table.structure == SelectionStructure.TIERS) {
                                " tier=${row.tierId} tierChance=${ChanceFormatter.percent(row.tierChance ?: 0.0)} local=${ChanceFormatter.percent(row.localChance)}"
                            } else {
                                ""
                            }
                            CommandMessages.chat(
                                context.source.sender,
                                MessageKeys.PREVIEW_PRIZE_LINE,
                                mapOf(
                                    "prize" to row.prize.id,
                                    "chance" to ChanceFormatter.percent(row.effectiveChance),
                                    "ratio" to ChanceFormatter.ratio(row.effectiveChance),
                                    "tierPart" to tierPart,
                                ),
                            )
                        }
                        Command.SINGLE_SUCCESS
                    },
            )
            .build()
}
