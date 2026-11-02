package java.util.concurrent

import ir.vil3ntec.tohid.ios.jvm.synchronized

/** shimِ iOS ِ `ConcurrentHashMap` — هر کار زیرِ همان قفلِ سراسریِ `synchronized`؛ null کلید/مقدار ⇐ NPE مثلِ JDK */
class ConcurrentHashMap<K : Any, V : Any>(initialCapacity: Int = 16) : AbstractMutableMap<K, V>() {
  private val m = LinkedHashMap<K, V>(initialCapacity)

  override val size: Int get() = synchronized(this) { m.size }
  override fun get(key: K): V? = synchronized(this) { m[key] }
  override fun containsKey(key: K): Boolean = synchronized(this) { m.containsKey(key) }
  override fun put(key: K, value: V): V? = synchronized(this) { m.put(key, value) }
  override fun remove(key: K): V? = synchronized(this) { m.remove(key) }
  override fun clear() = synchronized(this) { m.clear() }

  fun putIfAbsent(key: K, value: V): V? = synchronized(this) { m[key] ?: run { m[key] = value; null } }
  fun computeIfAbsent(key: K, f: (K) -> V): V = synchronized(this) { m.getOrPut(key) { f(key) } }
  fun remove(key: K, value: V): Boolean = synchronized(this) { if (m[key] == value) { m.remove(key); true } else false }

  /** نمایه‌ای از همین لحظه (مثلِ تکرارگرِ «ضعیفاً سازگار» ِ JDK، بی ConcurrentModificationException) */
  override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
    get() = synchronized(this) { LinkedHashMap(m).entries }
}
