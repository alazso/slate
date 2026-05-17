package so.alaz.slate.scheduler

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class PlatformScheduler(private val plugin: Plugin) {
    private val folia = ServerPlatform.current == ServerPlatform.FOLIA

    fun cancel(task: CancellableTask) {
        task.cancel()
    }

    fun runGlobal(task: () -> Unit): CancellableTask {
        val scheduled = Bukkit.getGlobalRegionScheduler().run(plugin) { task() }
        return ScheduledTaskHandle(scheduled)
    }

    fun runGlobalDelayed(delay: Duration, task: () -> Unit): CancellableTask {
        val ticks = delay.toTicks()
        val scheduled = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { task() }, ticks)
        return ScheduledTaskHandle(scheduled)
    }

    fun runAtLocation(location: Location, task: () -> Unit): CancellableTask {
        if (!folia) {
            return runGlobal(task)
        }
        val scheduled = Bukkit.getRegionScheduler().run(plugin, location) { task() }
        return ScheduledTaskHandle(scheduled)
    }

    fun runDelayedAtLocation(location: Location, delayTicks: Long, task: () -> Unit): CancellableTask {
        if (!folia) {
            return runGlobalDelayed(delayTicks.toTickDuration(), task)
        }
        val scheduled = Bukkit.getRegionScheduler().runDelayed(plugin, location, { task() }, delayTicks.coerceAtLeast(1))
        return ScheduledTaskHandle(scheduled)
    }

    fun runRepeatingAtLocation(
        location: Location,
        initialDelayTicks: Long,
        periodTicks: Long,
        task: () -> Unit,
    ): CancellableTask {
        val initialDelay = initialDelayTicks.coerceAtLeast(1)
        val period = periodTicks.coerceAtLeast(1)
        val scheduled = if (folia) {
            Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, { task() }, initialDelay, period)
        } else {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, { task() }, initialDelay, period)
        }
        return ScheduledTaskHandle(scheduled)
    }

    fun runAtLocationDelayed(location: Location, delay: Duration, task: () -> Unit): CancellableTask {
        if (!folia) {
            return runGlobalDelayed(delay, task)
        }
        val scheduled = Bukkit.getRegionScheduler().runDelayed(plugin, location, { task() }, delay.toTicks())
        return ScheduledTaskHandle(scheduled)
    }

    fun runForEntity(entity: Entity, task: () -> Unit): CancellableTask {
        return runForEntity(entity, retiredCallback = {}, task = task)
    }

    fun runForEntity(entity: Entity, retiredCallback: () -> Unit, task: () -> Unit): CancellableTask {
        if (!folia) {
            return runGlobal(task)
        }
        val scheduled = entity.scheduler.run(plugin, { task() }, retiredCallback)
        return NullableScheduledTaskHandle(scheduled)
    }

    fun runForPlayerEntity(entity: Entity, task: () -> Unit): CancellableTask =
        runForEntity(entity, task)

    fun runDelayedForEntity(
        entity: Entity,
        delayTicks: Long,
        retiredCallback: () -> Unit,
        task: () -> Unit,
    ): CancellableTask {
        if (!folia) {
            return runGlobalDelayed(delayTicks.toTickDuration(), task)
        }
        val scheduled = entity.scheduler.runDelayed(plugin, { task() }, retiredCallback, delayTicks.coerceAtLeast(1))
        return NullableScheduledTaskHandle(scheduled)
    }

    fun runRepeatingForEntity(
        entity: Entity,
        initialDelayTicks: Long,
        periodTicks: Long,
        retiredCallback: () -> Unit,
        task: () -> Unit,
    ): CancellableTask {
        if (!folia) {
            return object : CancellableTask {
                private val delegate = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                    plugin,
                    { task() },
                    initialDelayTicks.coerceAtLeast(1),
                    periodTicks.coerceAtLeast(1),
                )

                override fun cancel() {
                    delegate.cancel()
                }

                override val isCancelled: Boolean
                    get() = delegate.isCancelled
            }
        }
        val scheduled = entity.scheduler.runAtFixedRate(
            plugin,
            { task() },
            retiredCallback,
            initialDelayTicks.coerceAtLeast(1),
            periodTicks.coerceAtLeast(1),
        )
        return NullableScheduledTaskHandle(scheduled)
    }

    fun runAsync(task: () -> Unit): CancellableTask {
        val scheduled = Bukkit.getAsyncScheduler().runNow(plugin) { task() }
        return ScheduledTaskHandle(scheduled)
    }

    fun runAsyncDelayed(delay: Duration, task: () -> Unit): CancellableTask {
        val scheduled = Bukkit.getAsyncScheduler().runDelayed(plugin, { task() }, delay.toMillis(), TimeUnit.MILLISECONDS)
        return ScheduledTaskHandle(scheduled)
    }

    private fun Duration.toTicks(): Long = ceil(toMillis() / 50.0).toLong().coerceAtLeast(0)
    private fun Long.toTickDuration(): Duration = Duration.ofMillis(this.coerceAtLeast(0) * 50)

    private class ScheduledTaskHandle(private val delegate: ScheduledTask) : CancellableTask {
        override fun cancel() {
            delegate.cancel()
        }
        override val isCancelled: Boolean
            get() = delegate.isCancelled
    }

    private class NullableScheduledTaskHandle(private val delegate: ScheduledTask?) : CancellableTask {
        override fun cancel() {
            delegate?.cancel()
        }

        override val isCancelled: Boolean
            get() = delegate?.isCancelled ?: true
    }
}
