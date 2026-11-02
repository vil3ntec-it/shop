package java.util.concurrent.locks

import platform.Foundation.NSRecursiveLock
import platform.Foundation.dateWithTimeIntervalSinceNow

/** shimِ iOS ِ `ReentrantLock` روی `NSRecursiveLock` (قفلِ همین شیء، بازگشتی — همان JDK) */
interface Lock {
  fun lock()
  fun unlock()
  fun tryLock(): Boolean
}

class ReentrantLock(fair: Boolean = false) : Lock {
  private val ns = NSRecursiveLock()
  override fun lock() = ns.lock()
  override fun unlock() = ns.unlock()
  override fun tryLock(): Boolean = ns.tryLock()
  fun tryLock(time: Long, unit: java.util.concurrent.TimeUnit): Boolean =
    ns.lockBeforeDate(platform.Foundation.NSDate.dateWithTimeIntervalSinceNow(unit.toMillis(time) / 1000.0))
}
