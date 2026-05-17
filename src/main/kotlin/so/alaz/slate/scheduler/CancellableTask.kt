package so.alaz.slate.scheduler

interface CancellableTask {
    fun cancel()
    val isCancelled: Boolean
}
