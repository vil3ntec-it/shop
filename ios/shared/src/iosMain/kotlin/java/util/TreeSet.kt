package java.util

/** shimِ iOS ِ `TreeSet` (همان «مجموعهٔ مرتب» ِ `toSortedSet()` ِ JVM): ترتیبِ طبیعی یا مقایسه‌گر، بی تکرار */
class TreeSet<T>(private val comparator: Comparator<in T>? = null) : AbstractMutableSet<T>() {
  private val items = ArrayList<T>()

  @Suppress("UNCHECKED_CAST")
  private fun cmp(a: T, b: T): Int = comparator?.compare(a, b) ?: (a as Comparable<T>).compareTo(b)

  private fun find(e: T): Int {
    var lo = 0
    var hi = items.size - 1
    while (lo <= hi) {
      val mid = (lo + hi) ushr 1
      val c = cmp(items[mid], e)
      if (c < 0) lo = mid + 1 else if (c > 0) hi = mid - 1 else return mid
    }
    return -(lo + 1)
  }

  override val size: Int get() = items.size
  override fun contains(element: T): Boolean = find(element) >= 0
  override fun add(element: T): Boolean {
    val i = find(element)
    if (i >= 0) return false
    items.add(-(i + 1), element)
    return true
  }
  override fun remove(element: T): Boolean {
    val i = find(element)
    if (i < 0) return false
    items.removeAt(i)
    return true
  }
  override fun iterator(): MutableIterator<T> = items.iterator()
  fun first(): T = items.firstOrNull() ?: throw NoSuchElementException()
  fun last(): T = items.lastOrNull() ?: throw NoSuchElementException()
}
