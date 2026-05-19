package so.alaz.slate.opening

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Bukkit
import org.bukkit.event.Event
import so.alaz.slate.crate.CrateCost
import so.alaz.slate.crate.CrateEligibilityService
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.PrizeGrant
import so.alaz.slate.currency.domain.CurrencyAccount
import so.alaz.slate.economy.EconomyBalanceResult
import so.alaz.slate.economy.EconomyContext
import so.alaz.slate.economy.EconomyMutationResult
import so.alaz.slate.economy.SlateEconomyService
import so.alaz.slate.event.CrateOpenPostEvent
import so.alaz.slate.event.CrateOpenPreEvent
import so.alaz.slate.event.CratePrizeWinEvent
import so.alaz.slate.event.KeyConsumeEvent
import so.alaz.slate.key.KeyService
import so.alaz.slate.key.KeyType
import so.alaz.slate.prize.PityTracker
import so.alaz.slate.prize.PrizeEngine
import so.alaz.slate.probability.ProbabilityVariableService
import so.alaz.slate.scheduler.PlatformScheduler
import so.alaz.slate.storage.OpenOperationCreate
import so.alaz.slate.storage.OpenOperationState
import so.alaz.slate.storage.StorageService
import so.alaz.slate.util.ItemUtil
import so.alaz.slate.util.LogUtil
import java.util.UUID

class OpeningPipeline(
    private val keyService: KeyService,
    private val prizeEngine: PrizeEngine,
    private val pityTracker: PityTracker,
    private val storageService: StorageService,
    private val scheduler: PlatformScheduler,
    private val crateEligibilityService: CrateEligibilityService,
    private val economyService: SlateEconomyService? = null,
) {
    suspend fun preflight(
        player: org.bukkit.entity.Player,
        crate: CrateDefinition,
        keyType: KeyType = KeyType.Virtual,
        checkCooldown: Boolean = true,
        amount: Int = 1,
    ): OpenPreflightResult {
        val requestedAmount = amount.coerceAtLeast(1)
        val eligibility = crateEligibilityService.canOpen(player, crate)
        if (!eligibility.allowed) {
            return OpenPreflightResult.Blocked(eligibility.failureReason ?: FailureReason.CANCELLED_BY_EVENT)
        }

        if (checkCooldown) {
            val cooldownRemaining = cooldownRemainingMillis(player.uniqueId, crate)
            if (cooldownRemaining > 0L) {
                return OpenPreflightResult.Blocked(
                    reason = FailureReason.ON_COOLDOWN,
                    cooldownRemainingMillis = cooldownRemaining,
                )
            }
        }

        if (crate.maxPulls != -1 && storageService.countOpens(player.uniqueId, crate.id) >= crate.maxPulls) {
            return OpenPreflightResult.Blocked(FailureReason.MAX_PULLS_REACHED)
        }

        val availableKeys = keyService.countKeys(player, crate, keyType)
        val requiredKeys = crate.requiredKeys * requestedAmount
        if (availableKeys < requiredKeys) {
            return OpenPreflightResult.Blocked(
                reason = if (availableKeys <= 0) FailureReason.NO_KEY else FailureReason.INSUFFICIENT_KEYS,
                requiredKeys = requiredKeys,
                availableKeys = availableKeys,
            )
        }

        val account = CurrencyAccount.player(player.uniqueId)
        val currencyCosts = crate.costs.filterIsInstance<CrateCost.CurrencyCost>()
        if (currencyCosts.isNotEmpty() && economyService == null) {
            return OpenPreflightResult.Blocked(FailureReason.INSUFFICIENT_CURRENCY)
        }
        val balances = mutableMapOf<Pair<String, String>, so.alaz.slate.currency.domain.CurrencyAmount>()
        for (cost in currencyCosts) {
            val key = (cost.provider ?: "default") to cost.currency
            val balance = balances.getOrPut(key) {
                when (val result = economyService!!.getBalance(account, cost.provider, cost.currency)) {
                    is EconomyBalanceResult.Success -> result.balance
                    else -> return OpenPreflightResult.Blocked(FailureReason.INSUFFICIENT_CURRENCY)
                }
            }
            val totalCost = cost.amount.multipliedBy(requestedAmount)
            if (balance < totalCost) {
                return OpenPreflightResult.Blocked(FailureReason.INSUFFICIENT_CURRENCY)
            }
        }

        return OpenPreflightResult.Ready(
            requiredKeys = requiredKeys,
            availableKeys = availableKeys,
            currencyCosts = currencyCosts,
            requestedAmount = requestedAmount,
        )
    }

    /**
     * Returns the highest amount the player can open right now without violating any preflight gate.
     *
     * Used by the bulk-open confirmation menu to populate its MAX button.
     * Bounded by `cap` so callers can stop at the server-wide `maxBulkOpenAmount`.
     */
    suspend fun maxAffordable(
        player: org.bukkit.entity.Player,
        crate: CrateDefinition,
        cap: Int,
        keyType: KeyType = KeyType.Virtual,
    ): Int {
        if (cap <= 0) return 0
        val baseline = preflight(player, crate, keyType, checkCooldown = true, amount = 1)
        if (baseline !is OpenPreflightResult.Ready) return 0
        val keysAvailable = baseline.availableKeys
        val maxByKeys = if (crate.requiredKeys <= 0) cap else keysAvailable / crate.requiredKeys
        var bound = maxByKeys.coerceAtMost(cap)
        if (bound <= 0) return 0

        val currencyCosts = crate.costs.filterIsInstance<CrateCost.CurrencyCost>()
        if (currencyCosts.isEmpty() || economyService == null) return bound

        val account = CurrencyAccount.player(player.uniqueId)
        val balances = mutableMapOf<Pair<String, String>, so.alaz.slate.currency.domain.CurrencyAmount>()
        for (cost in currencyCosts) {
            val key = (cost.provider ?: "default") to cost.currency
            val balance = balances.getOrPut(key) {
                when (val result = economyService.getBalance(account, cost.provider, cost.currency)) {
                    is EconomyBalanceResult.Success -> result.balance
                    else -> return 0
                }
            }
            if (cost.amount.minorUnits.signum() <= 0) continue
            val maxByCost = balance.minorUnits.divide(cost.amount.minorUnits).toLong()
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            bound = bound.coerceAtMost(maxByCost)
            if (bound <= 0) return 0
        }
        return bound
    }

    suspend fun isCooldownActive(
        playerId: UUID,
        crate: CrateDefinition,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = cooldownRemainingMillis(playerId, crate, nowMillis) > 0L

    suspend fun cooldownRemainingMillis(
        playerId: UUID,
        crate: CrateDefinition,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        val cooldown = storageService.getCooldown(playerId, crate.id) ?: return 0L
        if (!crate.cooldown.isPositive()) return 0L
        val elapsedMs = nowMillis - cooldown.toEpochMilli()
        val remaining = crate.cooldown.inWholeMilliseconds - elapsedMs
        return remaining.coerceAtLeast(0L)
    }

    suspend fun setCooldownNow(
        playerId: UUID,
        crate: CrateDefinition,
    ) {
        storageService.setCooldown(playerId, crate.id, java.time.Instant.now())
    }

    suspend fun execute(
        player: org.bukkit.entity.Player,
        crate: CrateDefinition,
        keyType: KeyType,
        requestId: String? = null,
        checkCooldown: Boolean = true,
        writeCooldown: Boolean = true,
    ): OpeningResult {
        val operation = storageService.createOrGetOpenOperation(
            OpenOperationCreate(
                operationId = requestId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                requestId = requestId,
                uuid = player.uniqueId,
                crateId = crate.id,
                keyType = keyType,
            ),
        )
        when (operation.state) {
            OpenOperationState.DELIVERED -> return OpeningResult.Success(operation.prizeId ?: "__delivered__")
            OpenOperationState.RECOVERABLE -> return OpeningResult.Failure(FailureReason.DELIVERY_FAILED)
            OpenOperationState.FAILED -> return OpeningResult.Failure(FailureReason.CANCELLED_BY_EVENT)
            else -> Unit
        }

        val session = OpeningSession(player, crate, keyType)
        val eligibility = crateEligibilityService.canOpen(player, crate)
        if (!eligibility.allowed) {
            failOperation(operation.operationId, "eligibility failed: ${eligibility.failureReason}")
            return OpeningResult.Failure(eligibility.failureReason ?: FailureReason.CANCELLED_BY_EVENT)
        }

        if (checkCooldown && isCooldownActive(player.uniqueId, crate)) {
                failOperation(operation.operationId, "cooldown active")
                return OpeningResult.Failure(FailureReason.ON_COOLDOWN)
        }

        if (crate.maxPulls != -1 && storageService.countOpens(player.uniqueId, crate.id) >= crate.maxPulls) {
            failOperation(operation.operationId, "max pulls reached")
            return OpeningResult.Failure(FailureReason.MAX_PULLS_REACHED)
        }

        val availableKeys = keyService.countKeys(player, crate, keyType)
        if (availableKeys < crate.requiredKeys) {
            val reason = if (availableKeys <= 0) FailureReason.NO_KEY else FailureReason.INSUFFICIENT_KEYS
            failOperation(operation.operationId, "insufficient keys: required=${crate.requiredKeys}, available=$availableKeys")
            return OpeningResult.Failure(
                reason = reason,
                requiredKeys = crate.requiredKeys,
                availableKeys = availableKeys,
            )
        }

        val account = CurrencyAccount.player(player.uniqueId)
        val currencyCosts = crate.costs.filterIsInstance<CrateCost.CurrencyCost>()
        if (currencyCosts.isNotEmpty() && economyService == null) {
            failOperation(operation.operationId, "currency service unavailable")
            return OpeningResult.Failure(FailureReason.INSUFFICIENT_CURRENCY)
        }
        for ((index, cost) in currencyCosts.withIndex()) {
            val balanceResult = economyService!!.getBalance(account, cost.provider, cost.currency)
            when (balanceResult) {
                is EconomyBalanceResult.Success -> {
                    if (balanceResult.balance < cost.amount) {
                        failOperation(operation.operationId, "insufficient currency: provider=${cost.provider} currency=${cost.currency}")
                        storageService.appendOpenOperationStep(operation.operationId, OpenOperationState.FAILED, "currency_check failed index=$index")
                        return OpeningResult.Failure(FailureReason.INSUFFICIENT_CURRENCY)
                    }
                }

                else -> {
                    failOperation(operation.operationId, "currency provider unavailable: provider=${cost.provider} currency=${cost.currency}")
                    storageService.appendOpenOperationStep(operation.operationId, OpenOperationState.FAILED, "currency_check failed index=$index")
                    return OpeningResult.Failure(FailureReason.INSUFFICIENT_CURRENCY)
                }
            }
        }
        if (currencyCosts.isNotEmpty()) {
            storageService.appendOpenOperationStep(operation.operationId, OpenOperationState.CREATED, "currency_check success count=${currencyCosts.size}")
        }

        val preEvent = CrateOpenPreEvent(player, crate, keyType)
        fireForPlayer(player, preEvent)
        if (preEvent.isCancelled) {
            failOperation(operation.operationId, "cancelled by event")
            return OpeningResult.Failure(FailureReason.CANCELLED_BY_EVENT)
        }

        val successfulCharges = mutableListOf<AppliedCurrencyCharge>()
        for ((index, cost) in currencyCosts.withIndex()) {
            val withdrawResult = economyService!!.withdraw(
                account = account,
                providerId = cost.provider,
                currencyId = cost.currency,
                amount = cost.amount,
                context = EconomyContext(
                    operationId = operation.operationId,
                    transactionKey = "cost:$index",
                    reason = "crate_open_cost:${crate.id}",
                    metadata = mapOf("crateId" to crate.id, "keyType" to keyType.serialized()),
                ),
            )
            when (withdrawResult) {
                is EconomyMutationResult.Success -> {
                    successfulCharges += AppliedCurrencyCharge(index, cost)
                    storageService.appendOpenOperationStep(operation.operationId, OpenOperationState.CREATED, "currency_withdraw success index=$index")
                }

                is EconomyMutationResult.InsufficientFunds -> {
                    refundCharges(operation.operationId, account, crate.id, keyType, successfulCharges)
                    failOperation(operation.operationId, "currency withdraw failed: insufficient funds")
                    return OpeningResult.Failure(FailureReason.INSUFFICIENT_CURRENCY)
                }

                else -> {
                    refundCharges(operation.operationId, account, crate.id, keyType, successfulCharges)
                    failOperation(operation.operationId, "currency withdraw failed: ${withdrawResult.javaClass.simpleName}")
                    return OpeningResult.Failure(FailureReason.INSUFFICIENT_CURRENCY)
                }
            }
        }

        if (!keyService.consumeKey(player, crate, keyType)) {
            refundCharges(operation.operationId, account, crate.id, keyType, successfulCharges)
            failOperation(operation.operationId, "key consume failed")
            val remainingKeys = keyService.countKeys(player, crate, keyType)
            return OpeningResult.Failure(
                reason = FailureReason.INSUFFICIENT_KEYS,
                requiredKeys = crate.requiredKeys,
                availableKeys = remainingKeys,
            )
        }
        session.keyConsumed = true
        storageService.updateOpenOperation(operation.operationId, OpenOperationState.KEY_CONSUMED)
        storageService.appendOpenOperationStep(operation.operationId, OpenOperationState.KEY_CONSUMED, "key consumed")
        fireForPlayer(player, KeyConsumeEvent(player, crate, keyType))

        val pityCount = pityTracker.increment(player.uniqueId, crate.id)
        val prize = prizeEngine.selectPrize(
            crate = crate,
            pityTier = crate.pityConfig?.guaranteedTier?.takeIf { crate.pityConfig.enabled && pityCount >= crate.pityConfig.opensRequired },
            pityCount = pityCount,
        )
        session.selectedPrize = prize
        storageService.updateOpenOperation(
            operation.operationId,
            OpenOperationState.PRIZE_SELECTED,
            prizeId = prize?.id,
        )
        storageService.appendOpenOperationStep(operation.operationId, OpenOperationState.PRIZE_SELECTED, "prize selected")

        if (prize != null && crate.pityConfig?.enabled == true && prize.tier == crate.pityConfig.guaranteedTier) {
            pityTracker.reset(player.uniqueId, crate.id)
        }

        if (prize == null) {
            storageService.logOpen(player.uniqueId, crate.id, "__empty__", "Empty Roll", keyType)
            if (writeCooldown) {
                setCooldownNow(player.uniqueId, crate)
            }
            storageService.updateOpenOperation(operation.operationId, OpenOperationState.DELIVERED, prizeId = "__empty__")
            storageService.appendOpenOperationStep(operation.operationId, OpenOperationState.DELIVERED, "empty roll delivered")
            fireForPlayer(player, CrateOpenPostEvent(player, crate))
            return OpeningResult.Success("__empty__")
        }

        val animationConfig = crate.animation
        val skipAnimation = animationConfig?.skipPermission
            ?.takeIf { it.isNotBlank() }
            ?.let { permission ->
                // Only skip when this specific permission is explicitly set for the player.
                // This avoids OP/default-fallback contexts accidentally disabling animations.
                player.isPermissionSet(permission) && player.hasPermission(permission)
            }
            ?: false
        if (animationConfig?.enabled == true && !skipAnimation) {
            val guiService = so.alaz.slate.api.SlateAPI.guiService()
            if (guiService != null) {
                val decoys = crate.prizes
                    .filter { it.id != prize.id }
                    .shuffled()
                    .take(animationConfig.decoyCount.coerceAtLeast(0))
                runCatching {
                    guiService.playAnimation(player, animationConfig, prize, decoys)
                }.onFailure {
                    LogUtil.warn("Animation phase failed for crate ${crate.id}: ${it.message}")
                }
            }
        }

        val deliveryResult = runCatching { deliverPrize(session, operation.operationId) }
        if (deliveryResult.isFailure) {
            storageService.addPendingPrize(player.uniqueId, crate.id, prize.id)
            LogUtil.error("Failed to deliver prize; queued pending", deliveryResult.exceptionOrNull())
            storageService.updateOpenOperation(
                operation.operationId,
                OpenOperationState.RECOVERABLE,
                prizeId = prize.id,
                errorMessage = deliveryResult.exceptionOrNull()?.message,
            )
            storageService.appendOpenOperationStep(operation.operationId, OpenOperationState.RECOVERABLE, "delivery failed, queued pending")
            return OpeningResult.Failure(FailureReason.DELIVERY_FAILED)
        }

        storageService.logOpen(player.uniqueId, crate.id, prize.id, prize.name, keyType)
        if (writeCooldown) {
            setCooldownNow(player.uniqueId, crate)
        }
        storageService.updateOpenOperation(operation.operationId, OpenOperationState.DELIVERED, prizeId = prize.id)
        storageService.appendOpenOperationStep(operation.operationId, OpenOperationState.DELIVERED, "prize delivered")
        fireForPlayer(player, CrateOpenPostEvent(player, crate))
        fireForPlayer(player, CratePrizeWinEvent(player, crate, prize))
        return OpeningResult.Success(prize.id)
    }

    private suspend fun deliverPrize(session: OpeningSession, operationId: String) {
        val prize = requireNotNull(session.selectedPrize) { "Prize must be selected before delivery" }
        runForPlayer(session.player) {
            val rewardStacks = prize.items.flatMap { prizeItem ->
                ItemUtil.expandPrizeItem(prizeItem)
                    ?: throw IllegalStateException("Invalid prize item '${prizeItem.material}' in prize '${prize.id}'")
            }
            if (rewardStacks.isNotEmpty()) {
                val overflow = session.player.inventory.addItem(*rewardStacks.toTypedArray())
                if (overflow.isNotEmpty()) {
                    throw IllegalStateException("Inventory full while delivering prize '${prize.id}'")
                }
            }
            for (cmd in prize.commands) {
                val prepared = cmd.replace("%player%", session.player.name)
                val ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), prepared)
                if (!ok) {
                    throw IllegalStateException("Command delivery failed for '$prepared'")
                }
            }
            for (message in prize.messages) {
                val variables = ProbabilityVariableService.crateVariables(session.crate.id) +
                    ProbabilityVariableService.rewardVariables(session.crate.id, prize.id) +
                    (prize.tier?.let { ProbabilityVariableService.tierVariables(session.crate.id, it) } ?: emptyMap())
                val rendered = ProbabilityVariableService.render(message, variables)
                val messageService = so.alaz.slate.api.SlateAPI.messageService()
                if (messageService != null) {
                    messageService.chatRaw(session.player, rendered)
                }
            }
        }
        val account = CurrencyAccount.player(session.player.uniqueId)
        val grants = prize.grants.filterIsInstance<PrizeGrant.CurrencyGrant>()
        if (grants.isNotEmpty() && economyService == null) {
            error("Currency service unavailable for reward grants")
        }
        for ((index, grant) in grants.withIndex()) {
            val result = economyService!!.deposit(
                account = account,
                providerId = grant.provider,
                currencyId = grant.currency,
                amount = grant.amount,
                context = EconomyContext(
                    operationId = operationId,
                    transactionKey = "reward:$index",
                    reason = "crate_reward_grant:${session.crate.id}:${prize.id}",
                    metadata = mapOf("crateId" to session.crate.id, "prizeId" to prize.id),
                ),
            )
            when (result) {
                is EconomyMutationResult.Success -> Unit

                else -> error("Currency reward grant failed for provider='${grant.provider}', currency='${grant.currency}'")
            }
        }
        if (grants.isNotEmpty()) {
            storageService.appendOpenOperationStep(operationId, OpenOperationState.CREATED, "currency_reward_deposit success count=${grants.size}")
        }
    }

    private suspend fun fireForPlayer(player: org.bukkit.entity.Player, event: Event) = runForPlayer(player) {
        Bukkit.getPluginManager().callEvent(event)
    }

    private suspend fun runForPlayer(player: org.bukkit.entity.Player, task: () -> Unit) =
        suspendCancellableCoroutine<Unit> { cont ->
            scheduler.runForPlayerEntity(player) {
                runCatching(task)
                    .onSuccess { cont.resume(Unit) }
                    .onFailure { cont.resumeWithException(it) }
            }
        }

    private suspend fun failOperation(operationId: String, message: String) {
        storageService.updateOpenOperation(
            operationId = operationId,
            state = OpenOperationState.FAILED,
            errorMessage = message,
        )
        storageService.appendOpenOperationStep(operationId, OpenOperationState.FAILED, message)
    }

    private suspend fun refundCharges(
        operationId: String,
        account: CurrencyAccount,
        crateId: String,
        keyType: KeyType,
        charges: List<AppliedCurrencyCharge>,
    ) {
        for (charge in charges) {
            val result = economyService?.deposit(
                account = account,
                providerId = charge.cost.provider,
                currencyId = charge.cost.currency,
                amount = charge.cost.amount,
                context = EconomyContext(
                    operationId = operationId,
                    transactionKey = "cost-refund:${charge.index}",
                    reason = "crate_open_refund:$crateId",
                    metadata = mapOf("crateId" to crateId, "keyType" to keyType.serialized()),
                ),
            )
            if (result is EconomyMutationResult.Success) {
                storageService.appendOpenOperationStep(operationId, OpenOperationState.CREATED, "currency_refund success index=${charge.index}")
            } else {
                storageService.appendOpenOperationStep(operationId, OpenOperationState.FAILED, "currency_refund failed index=${charge.index}")
            }
        }
    }

    private data class AppliedCurrencyCharge(
        val index: Int,
        val cost: CrateCost.CurrencyCost,
    )

    private fun KeyType.serialized(): String = when (this) {
        KeyType.Physical -> "PHYSICAL"
        KeyType.Virtual -> "VIRTUAL"
    }
}

sealed class OpenPreflightResult {
    data class Ready(
        val requiredKeys: Int,
        val availableKeys: Int,
        val currencyCosts: List<CrateCost.CurrencyCost>,
        val requestedAmount: Int = 1,
    ) : OpenPreflightResult()

    data class Blocked(
        val reason: FailureReason,
        val requiredKeys: Int? = null,
        val availableKeys: Int? = null,
        val cooldownRemainingMillis: Long = 0L,
    ) : OpenPreflightResult()
}

sealed class OpeningResult {
    data class Success(val prizeId: String) : OpeningResult()
    data class Failure(
        val reason: FailureReason,
        val requiredKeys: Int? = null,
        val availableKeys: Int? = null,
    ) : OpeningResult()
}

enum class FailureReason {
    NO_KEY,
    INSUFFICIENT_KEYS,
    ON_COOLDOWN,
    MAX_PULLS_REACHED,
    NOT_ALLOWED_WORLD,
    CRATE_DISABLED,
    CRATE_ARCHIVED,
    CRATE_OUT_OF_SEASON,
    MISSING_PERMISSION,
    REGION_RESTRICTED,
    WORLDGUARD_UNAVAILABLE,
    INSUFFICIENT_CURRENCY,
    CANCELLED_BY_EVENT,
    DELIVERY_FAILED,
}
