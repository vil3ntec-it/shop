package java.util.concurrent

import ir.vil3ntec.tohid.ios.jvm.Runnable
import ir.vil3ntec.tohid.ios.jvm.Thread
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait

/*
 *  shimِ iOS ِ کمترینِ `java.util.concurrent` که کدِ مشترک دارد (پیش‌نویسِ سبد، `CartStore`):
 *  `Executors.newSingleThreadExecutor` ⇐ صفِ سریالِ GCD (همان «یک کار در یک زمان، به ترتیب»).
 *  کارخانهٔ رشته (`ThreadFactory`) صدا زده نمی‌شود: GCD رشته‌اش را خودش دارد.
 */
fun interface Callable<V> {
  fun call(): V
}

fun interface Executor {
  fun execute(command: Runnable)
}

fun interface ThreadFactory {
  fun newThread(r: Runnable): Thread
}

interface Future<V> {
  fun get(): V
  fun isDone(): Boolean
}

interface ExecutorService : Executor {
  fun <T> submit(task: Callable<T>): Future<T>
  fun shutdown() {}
}

private class SerialQueue(label: String) : ExecutorService {
  private val queue = dispatch_queue_create(label, null)

  override fun execute(command: Runnable) {
    dispatch_async(queue) { command.run() }
  }

  override fun <T> submit(task: Callable<T>): Future<T> {
    val done = dispatch_semaphore_create(0)
    var result: Result<T>? = null
    dispatch_async(queue) {
      result = runCatching { task.call() }
      dispatch_semaphore_signal(done)
    }
    return object : Future<T> {
      override fun get(): T {
        if (result == null) {
          dispatch_semaphore_wait(done, DISPATCH_TIME_FOREVER)
          dispatch_semaphore_signal(done)
        }
        return result!!.getOrElse { throw ExecutionException(it) }
      }

      override fun isDone(): Boolean = result != null
    }
  }
}

class ExecutionException(cause: Throwable) : Exception(cause)

object Executors {
  fun newSingleThreadExecutor(): ExecutorService = SerialQueue("tohid.serial")
  fun newSingleThreadExecutor(factory: ThreadFactory): ExecutorService = SerialQueue("tohid.serial")
}
