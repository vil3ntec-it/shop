package java.util

/*
 *  shimِ iOS ِ `java.util.Locale` — کدِ مشترک فقط برای «رقمِ لاتین و نقطهٔ اعشار» صدایش می‌زند
 *  (`String.format(Locale.ROOT, …)`، `Locale.US`). قالب‌بندیِ ‎ios/jvm/Format.kt‎ همیشه همان
 *  ROOT را می‌نویسد؛ نامِ زبان فقط برای چاپ و مقایسه نگه داشته شده.
 */
class Locale(private val language: String, private val country: String = "") {
  fun getLanguage(): String = language
  fun getCountry(): String = country
  fun toLanguageTag(): String = if (country.isEmpty()) language.ifEmpty { "und" } else "$language-$country"
  override fun toString(): String = if (country.isEmpty()) language else "${language}_$country"
  override fun equals(other: Any?): Boolean = other is Locale && other.language == language && other.country == country
  override fun hashCode(): Int = language.hashCode() * 31 + country.hashCode()

  companion object {
    val ROOT: Locale = Locale("")
    val US: Locale = Locale("en", "US")
    val ENGLISH: Locale = Locale("en")

    /** زبانِ دستگاه در iOS به قالبِ عدد نمی‌رسد (‎Format.kt‎)؛ همان ROOT */
    fun getDefault(): Locale = ROOT
  }
}
