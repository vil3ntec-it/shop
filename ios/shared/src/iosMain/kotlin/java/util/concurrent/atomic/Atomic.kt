package java.util.concurrent.atomic

/** shimِ iOS ِ رده‌های اتمیِ JDK روی `kotlin.concurrent` ِ Kotlin/Native (همان معنا: خواندن/نوشتنِ اتمی، CAS) */
class AtomicReference<V>(initial: V) {
  private val a = kotlin.concurrent.AtomicReference(initial)
  fun get(): V = a.value
  fun set(value: V) { a.value = value }
  fun getAndSet(value: V): V = a.getAndSet(value)
  fun compareAndSet(expect: V, update: V): Boolean = a.compareAndSet(expect, update)
  override fun toString(): String = get().toString()
}

class AtomicBoolean(initial: Boolean = false) {
  private val a = kotlin.concurrent.AtomicInt(if (initial) 1 else 0)
  fun get(): Boolean = a.value != 0
  fun set(value: Boolean) { a.value = if (value) 1 else 0 }
  fun getAndSet(value: Boolean): Boolean = a.getAndSet(if (value) 1 else 0) != 0
  fun compareAndSet(expect: Boolean, update: Boolean): Boolean = a.compareAndSet(if (expect) 1 else 0, if (update) 1 else 0)
}

class AtomicInteger(initial: Int = 0) {
  private val a = kotlin.concurrent.AtomicInt(initial)
  fun get(): Int = a.value
  fun set(value: Int) { a.value = value }
  fun incrementAndGet(): Int = a.incrementAndGet()
  fun getAndIncrement(): Int = a.getAndIncrement()
  fun decrementAndGet(): Int = a.decrementAndGet()
  fun addAndGet(delta: Int): Int = a.addAndGet(delta)
  fun getAndSet(value: Int): Int = a.getAndSet(value)
  fun compareAndSet(expect: Int, update: Int): Boolean = a.compareAndSet(expect, update)
}

class AtomicLong(initial: Long = 0L) {
  private val a = kotlin.concurrent.AtomicLong(initial)
  fun get(): Long = a.value
  fun set(value: Long) { a.value = value }
  fun incrementAndGet(): Long = a.incrementAndGet()
  fun getAndIncrement(): Long = a.getAndIncrement()
  fun addAndGet(delta: Long): Long = a.addAndGet(delta)
  fun getAndSet(value: Long): Long = a.getAndSet(value)
  fun compareAndSet(expect: Long, update: Long): Boolean = a.compareAndSet(expect, update)
}
