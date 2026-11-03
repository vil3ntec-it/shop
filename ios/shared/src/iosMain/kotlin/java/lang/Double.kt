package java.lang

/*
 *  shimِ iOS ِ دو تابعِ ایستای `java.lang.Double` که کدِ مشترک با نامِ کامل صدا می‌زند (`data/DeviceLocation.kt`:
 *  پهنا و درازای ذخیره‌شده در تنظیم‌ها، بیت‌به‌بیت). در این بسته فایلِ دیگری نیست، پس نامِ `Double` جز همین‌جا
 *  هیچ‌جا `kotlin.Double` را نمی‌پوشاند.
 */
object Double {
  fun longBitsToDouble(bits: Long): kotlin.Double = kotlin.Double.fromBits(bits)
  fun doubleToRawLongBits(value: kotlin.Double): Long = value.toRawBits()
  fun doubleToLongBits(value: kotlin.Double): Long = value.toBits()
}
