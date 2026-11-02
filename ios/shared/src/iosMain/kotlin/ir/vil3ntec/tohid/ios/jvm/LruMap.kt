package ir.vil3ntec.tohid.ios.jvm

/*
 *  `LinkedHashMap(cap, load, accessOrder = true)` با `removeEldestEntry` ِ JVM (کشِ LRU). LinkedHashMap ِ Kotlin/Native
 *  نهایی است و ترتیبِ دسترسی ندارد؛ ساختِ iOS زیرردهٔ بی‌نامِ `LinkedHashMap` را به این برمی‌گرداند
 *  (‎ios/shared/build.gradle.kts‎ `textRewrites`). همان قرارداد: هر `get` ِ موفق ورودی را تازه‌ترین می‌کند و پس از
 *  هر `put`، `removeEldestEntry(کهنه‌ترین)` تصمیم می‌گیرد.
 */
open class LinkedHashMapJvm<K, V>(initialCapacity: Int = 16, loadFactor: Float = 0.75f, private val accessOrder: Boolean = false) :
  AbstractMutableMap<K, V>() {
  private val m = LinkedHashMap<K, V>(initialCapacity)

  protected open fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = false

  override val size: Int get() = m.size
  override fun containsKey(key: K): Boolean = m.containsKey(key)

  override fun get(key: K): V? {
    if (!m.containsKey(key)) return null
    val v = m[key]
    if (accessOrder) { m.remove(key); @Suppress("UNCHECKED_CAST") m.put(key, v as V) }
    return v
  }

  override fun put(key: K, value: V): V? {
    val old = if (accessOrder) m.remove(key) else m[key]
    m[key] = value
    val eldest = m.entries.firstOrNull()
    if (eldest != null && removeEldestEntry(eldest)) m.remove(eldest.key)
    return old
  }

  override fun remove(key: K): V? = m.remove(key)
  override fun clear() = m.clear()
  override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = m.entries
}
