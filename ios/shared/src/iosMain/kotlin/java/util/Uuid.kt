package java.util

import platform.Foundation.NSUUID

/** shimِ iOS ِ `java.util.UUID` — تصادفی از NSUUID (نسخهٔ ۴)، متن با حرفِ کوچک مثلِ JDK */
class UUID private constructor(private val text: String) : Comparable<UUID> {
  override fun toString(): String = text
  override fun equals(other: Any?): Boolean = other is UUID && other.text == text
  override fun hashCode(): Int = text.hashCode()
  override fun compareTo(other: UUID): Int = text.compareTo(other.text)

  companion object {
    private val FORM = Regex("^[0-9a-fA-F]{1,8}-[0-9a-fA-F]{1,4}-[0-9a-fA-F]{1,4}-[0-9a-fA-F]{1,4}-[0-9a-fA-F]{1,12}$")

    fun randomUUID(): UUID = UUID(NSUUID().UUIDString.lowercase())

    /** همان JDK: بخش‌های کوتاه با صفر پر می‌شوند؛ شکلِ دیگر ⇐ IllegalArgumentException */
    fun fromString(name: String): UUID {
      if (!FORM.matches(name)) throw IllegalArgumentException("Invalid UUID string: $name")
      val widths = intArrayOf(8, 4, 4, 4, 12)
      return UUID(name.split('-').mapIndexed { i, p -> p.lowercase().padStart(widths[i], '0') }.joinToString("-"))
    }
  }
}
