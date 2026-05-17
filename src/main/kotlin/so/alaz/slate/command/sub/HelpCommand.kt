package so.alaz.slate.command.sub

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.message.MessageKeys

object HelpCommand {
    private const val PAGE_SIZE = 8

    fun node(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("help")
            .requires { it.sender.hasPermission(SlatePermissions.ADMIN_HELP) }
            .executes { context -> execute(context.source, 1) }
            .then(
                Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes { context ->
                        execute(context.source, IntegerArgumentType.getInteger(context, "page"))
                    },
            )
            .build()

    private fun execute(source: CommandSourceStack, requestedPage: Int): Int {
        val sender = source.sender
        val visible = allEntries().filter { sender.hasPermission(it.permission) }
        if (visible.isEmpty()) {
            CommandMessages.chat(sender, MessageKeys.HELP_EMPTY)
            return Command.SINGLE_SUCCESS
        }

        val totalPages = ((visible.size - 1) / PAGE_SIZE) + 1
        val page = requestedPage.coerceIn(1, totalPages)
        if (page != requestedPage) {
            CommandMessages.chat(
                sender,
                MessageKeys.HELP_PAGE_INVALID,
                mapOf("page" to requestedPage, "resolvedPage" to page),
            )
        }

        val start = (page - 1) * PAGE_SIZE
        val end = minOf(start + PAGE_SIZE, visible.size)
        val entries = visible.subList(start, end)

        CommandMessages.chat(
            sender,
            MessageKeys.HELP_HEADER,
            mapOf("page" to page, "totalPages" to totalPages),
        )
        entries.forEach { entry ->
            CommandMessages.chat(
                sender,
                MessageKeys.HELP_LINE,
                mapOf("command" to entry.syntax, "description" to entry.description),
            )
        }
        if (page < totalPages) {
            CommandMessages.chat(
                sender,
                MessageKeys.HELP_FOOTER,
                mapOf("nextPage" to (page + 1)),
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun allEntries(): List<HelpEntry> = listOf(
        HelpEntry("/sla reload", SlatePermissions.ADMIN_RELOAD, "Reload config, locales, and crate definitions."),
        HelpEntry("/sla health", SlatePermissions.ADMIN_HEALTH, "Show plugin runtime health status."),
        HelpEntry("/sla version", SlatePermissions.ADMIN_VERSION, "Show current plugin version."),
        HelpEntry("/sla key give <player> <crate> <amount> [flags]", SlatePermissions.ADMIN_KEY_GIVE, "Give virtual or physical keys."),
        HelpEntry("/sla key take <player> <crate> <amount> [flags]", SlatePermissions.ADMIN_KEY_TAKE, "Take virtual or physical keys."),
        HelpEntry("/sla key set <player> <crate> <amount> [flags]", SlatePermissions.ADMIN_KEY_SET, "Set virtual key balance."),
        HelpEntry("/sla key clear <player> [crate] --confirm", SlatePermissions.ADMIN_KEY_CLEAR, "Clear virtual key balances."),
        HelpEntry("/sla key balance <player> [crate]", SlatePermissions.ADMIN_KEY_BALANCE, "View virtual key balances."),
        HelpEntry("/sla anchor set <crate>", SlatePermissions.ADMIN_ANCHOR_SET, "Set targeted block as crate anchor."),
        HelpEntry("/sla anchor remove", SlatePermissions.ADMIN_ANCHOR_REMOVE, "Remove anchor at targeted block."),
        HelpEntry("/sla anchor list", SlatePermissions.ADMIN_ANCHOR_LIST, "List current anchors."),
        HelpEntry("/sla crate chances <crate>", SlatePermissions.CRATE_CHANCES, "Show calculated chance table."),
        HelpEntry("/sla crate validate <crate>", SlatePermissions.CRATE_VALIDATE, "Validate crate configuration."),
        HelpEntry("/sla crate simulate <crate> <rolls>", SlatePermissions.CRATE_SIMULATE, "Run probability simulation."),
        HelpEntry("/sla reward weight <crate> <reward> <weight>", SlatePermissions.REWARD_WEIGHT, "Set reward weight."),
        HelpEntry("/sla reward chance <crate> <reward> <chance>", SlatePermissions.REWARD_CHANCE, "Set reward chance."),
        HelpEntry("/sla reward tier <crate> <reward> <tier>", SlatePermissions.REWARD_TIER, "Move reward to tier."),
        HelpEntry("/sla tier list <crate>", SlatePermissions.TIER_LIST, "List tiers for a crate."),
        HelpEntry("/sla tier create <crate> <tier>", SlatePermissions.TIER_CREATE, "Create a new tier."),
        HelpEntry("/sla tier delete <crate> <tier>", SlatePermissions.TIER_DELETE, "Delete a tier (if unused)."),
        HelpEntry("/sla tier rename <crate> <old> <new>", SlatePermissions.TIER_RENAME, "Rename a tier."),
        HelpEntry("/sla tier weight <crate> <tier> <weight>", SlatePermissions.TIER_WEIGHT, "Set tier weight."),
        HelpEntry("/sla tier info <crate> <tier>", SlatePermissions.TIER_INFO, "Show tier details."),
        HelpEntry("/sla tier rewards <crate> <tier>", SlatePermissions.TIER_REWARDS, "List rewards in tier."),
        HelpEntry("/sla debug key", SlatePermissions.ADMIN_DEBUG_ITEM, "Inspect held key diagnostics."),
    )

    private data class HelpEntry(
        val syntax: String,
        val permission: String,
        val description: String,
    )
}
