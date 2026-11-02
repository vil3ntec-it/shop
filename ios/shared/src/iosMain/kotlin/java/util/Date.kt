package java.util

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/** shimِ iOS ِ `java.util.Date` — فقط لحظه (میلی‌ثانیه از ۱۹۷۰) برای `SimpleDateFormat` */
class Date(private val millis: Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()) {
  fun getTime(): Long = millis
  val time: Long get() = millis
  override fun equals(other: Any?): Boolean = other is Date && other.millis == millis
  override fun hashCode(): Int = millis.hashCode()
}
