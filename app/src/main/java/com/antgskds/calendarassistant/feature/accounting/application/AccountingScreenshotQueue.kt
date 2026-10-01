package com.antgskds.calendarassistant.feature.accounting.application

import kotlinx.coroutines.*

/** 调用方在同一调度线程提交/取消；每项只持有自己的已确认截图，不再读取页面状态。 */
class AccountingScreenshotQueue<T>(
    private val scope: CoroutineScope,
    private val process: suspend (String, T) -> Unit,
    private val finish: suspend (String, T, Throwable?) -> Unit,
) {
    private class Item<T>(val id: String, val value: T) {
        var cancelled = false
        var job: Job? = null
        var disposed = false
    }
    private val items = LinkedHashMap<String, Item<T>>()
    private var worker: Job? = null
    val size get() = items.size

    fun submit(id: String, value: T) {
        check(scope.isActive && id !in items)
        items[id] = Item(id, value)
        if (worker?.isActive == true) return
        worker = scope.launch {
            try {
                while (items.isNotEmpty()) {
                    val item = items.values.first()
                    supervisorScope {
                        val job = launch(start = CoroutineStart.LAZY) {
                            var failure: Throwable? = null
                            try {
                                if (item.cancelled) throw CancellationException("task cancelled")
                                process(item.id, item.value)
                            } catch (e: Exception) { failure = e }
                            finally { dispose(item, failure) }
                        }
                        item.job = job
                        job.start()
                        job.join()
                        // 取消可能发生在协程首次执行前，此时也要释放资源。
                        dispose(item, CancellationException("task cancelled"))
                    }
                    items.remove(item.id)
                }
            } finally {
                items.values.toList().forEach { dispose(it, CancellationException("queue stopped")) }
                items.clear()
            }
        }
    }

    private suspend fun dispose(item: Item<T>, failure: Throwable?) {
        if (item.disposed) return
        item.disposed = true
        withContext(NonCancellable) {
            // 单项通知失败不能中止队列；调用方必须在 finish 的 finally 中释放资源。
            try { finish(item.id, item.value, failure) } catch (_: Exception) { }
        }
    }

    fun cancel(id: String) {
        val item = items[id] ?: return
        item.cancelled = true
        item.job?.cancel()
        if (item.job == null) {
            items.remove(id)
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                dispose(item, CancellationException("task cancelled"))
            }
        }
    }

    fun cancelAll() { items.keys.toList().forEach(::cancel) }
}
