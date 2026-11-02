package java.util

/** shimِ iOS ِ `TreeMap` (همان «نقشهٔ مرتب» ِ `sortedMapOf`/`toSortedMap` ِ JVM): کلیدها به ترتیب */
class TreeMap<K, V>(private val comparator: Comparator<in K>? = null) : AbstractMutableMap<K, V>() {
  private val order = TreeSet<K>(comparator)
  private val vals = HashMap<K, V>()

  override val size: Int get() = order.size
  override fun containsKey(key: K): Boolean = order.contains(key)
  override fun get(key: K): V? = if (order.contains(key)) vals[key] else null
  override fun put(key: K, value: V): V? { order.add(key); return vals.put(key, value) }
  override fun remove(key: K): V? { if (!order.remove(key)) return null; return vals.remove(key) }
  override fun clear() { order.clear(); vals.clear() }

  fun firstKey(): K = order.first()
  fun lastKey(): K = order.last()

  override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
    get() = object : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
      override val size: Int get() = order.size
      override fun add(element: MutableMap.MutableEntry<K, V>): Boolean = throw UnsupportedOperationException()
      override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
        val it = order.toList().iterator()
        var last: K? = null
        return object : MutableIterator<MutableMap.MutableEntry<K, V>> {
          override fun hasNext() = it.hasNext()
          override fun next(): MutableMap.MutableEntry<K, V> {
            val k = it.next()
            last = k
            return object : MutableMap.MutableEntry<K, V> {
              override val key: K get() = k
              @Suppress("UNCHECKED_CAST")
              override val value: V get() = vals[k] as V
              override fun setValue(newValue: V): V { @Suppress("UNCHECKED_CAST") val old = vals[k] as V; vals[k] = newValue; return old }
            }
          }
          override fun remove() { last?.let { this@TreeMap.remove(it) } }
        }
      }
    }
}
