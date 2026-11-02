package java.util

import kotlin.native.identityHashCode

/*
 *  shimِ iOS ِ `IdentityHashMap` و `Collections.newSetFromMap` (`LedgerMerge`: «همین شیء را دیده‌ام؟»).
 *  هویت = `===` و `identityHashCode`، نه `equals` — همان JDK.
 */
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
private class IdKey(val ref: Any?) {
  override fun equals(other: Any?): Boolean = other is IdKey && other.ref === ref
  override fun hashCode(): Int = ref?.identityHashCode() ?: 0
}

class IdentityHashMap<K, V>(expectedMaxSize: Int = 21) : AbstractMutableMap<K, V>() {
  private val m = LinkedHashMap<IdKey, Pair<K, V>>(expectedMaxSize)

  override fun put(key: K, value: V): V? = m.put(IdKey(key), key to value)?.second
  override fun get(key: K): V? = m[IdKey(key)]?.second
  override fun containsKey(key: K): Boolean = m.containsKey(IdKey(key))
  override fun remove(key: K): V? = m.remove(IdKey(key))?.second
  override val size: Int get() = m.size
  override fun clear() = m.clear()

  override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
    get() = object : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
      override val size: Int get() = m.size
      override fun add(element: MutableMap.MutableEntry<K, V>): Boolean = throw UnsupportedOperationException()
      override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
        val it = m.entries.iterator()
        return object : MutableIterator<MutableMap.MutableEntry<K, V>> {
          override fun hasNext() = it.hasNext()
          override fun next(): MutableMap.MutableEntry<K, V> {
            val e = it.next()
            return object : MutableMap.MutableEntry<K, V> {
              override val key: K get() = e.value.first
              override val value: V get() = e.value.second
              override fun setValue(newValue: V): V { val old = e.value.second; e.setValue(e.value.first to newValue); return old }
            }
          }
          override fun remove() = it.remove()
        }
      }
    }
}

object Collections {
  fun <E> newSetFromMap(map: MutableMap<E, Boolean>): MutableSet<E> {
    require(map.isEmpty()) { "Map is non-empty" }
    return object : AbstractMutableSet<E>() {
      override val size: Int get() = map.size
      override fun add(element: E): Boolean = map.put(element, true) == null
      override fun remove(element: E): Boolean = map.remove(element) != null
      override fun contains(element: E): Boolean = map.containsKey(element)
      override fun iterator(): MutableIterator<E> = map.keys.iterator()
    }
  }

  fun <T> unmodifiableList(list: List<T>): List<T> = list.toList()
  fun <T> unmodifiableSet(set: Set<T>): Set<T> = set.toSet()
  fun <K, V> unmodifiableMap(map: Map<K, V>): Map<K, V> = map.toMap()
  fun <T> emptyList(): List<T> = kotlin.collections.emptyList()
}
