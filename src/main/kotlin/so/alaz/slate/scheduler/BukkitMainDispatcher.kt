package so.alaz.slate.scheduler

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

class BukkitMainDispatcher(private val scheduler: PlatformScheduler) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        scheduler.runGlobal { block.run() }
    }
}
