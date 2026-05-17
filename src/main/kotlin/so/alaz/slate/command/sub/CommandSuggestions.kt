package so.alaz.slate.command.sub

import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import java.util.concurrent.CompletableFuture
import org.bukkit.Bukkit
import so.alaz.slate.api.SlateAPI

object CommandSuggestions {
    private val keyMutationFlags = listOf("--virtual", "--physical", "--silent", "--reason")
    private val keyClearFlags = listOf("--confirm", "--reason")

    fun suggestOnlinePlayers(
        @Suppress("UNUSED_PARAMETER") source: CommandSourceStack,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        val remaining = builder.remaining.lowercase()
        Bukkit.getOnlinePlayers()
            .map { it.name }
            .distinct()
            .sorted()
            .filter { it.lowercase().startsWith(remaining) }
            .forEach(builder::suggest)
        return builder.buildFuture()
    }

    fun suggestKeyMutationFlags(
        @Suppress("UNUSED_PARAMETER") source: CommandSourceStack,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> = suggestFlags(builder, keyMutationFlags)

    fun suggestKeyClearFlags(
        @Suppress("UNUSED_PARAMETER") source: CommandSourceStack,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> = suggestFlags(builder, keyClearFlags)

    fun suggestCrateIds(
        @Suppress("UNUSED_PARAMETER") source: CommandSourceStack,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        val remaining = builder.remaining.lowercase()
        SlateAPI.crateManager()
            ?.all()
            .orEmpty()
            .asSequence()
            .map { it.id }
            .distinct()
            .sorted()
            .filter { it.lowercase().startsWith(remaining) }
            .forEach(builder::suggest)
        return builder.buildFuture()
    }

    private fun suggestFlags(
        builder: SuggestionsBuilder,
        flags: List<String>,
    ): CompletableFuture<Suggestions> {
        val remaining = builder.remaining
        val token = remaining.substringAfterLast(' ', remaining).lowercase()
        flags.asSequence()
            .filter { token.isBlank() || it.startsWith(token) }
            .forEach { flag ->
                val suggestion = if (flag == "--reason") "$flag " else flag
                builder.suggest(suggestion)
            }
        return builder.buildFuture()
    }
}
