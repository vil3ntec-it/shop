package java.lang

/*
 *  shimِ iOS ِ دو تابعِ ایستای `java.lang.Double` که کدِ مشترک با نامِ کامل صدا می‌زند (`data/DeviceLocation.kt`:
 *  پهنا و درازای ذخیره‌شده در تنظیم‌ها، بیت‌به‌بیت). در این بسته فایلِ دیگری نیست، پس نامِ `Double` جز همین‌جا
 *  هیچ‌جا `kotlin.Double` را نمی‌پوشاند.
 *
 *  `internal`: چارچوبِ ObjC هر ردهٔ عمومیِ سطحِ بالا را `TohidShared<نام>` می‌نامد و Kotlin/Native جعبهٔ عددِ
 *  `kotlin.Double` را هم `TohidSharedDouble` (‎`Number`/`Long`/`Boolean`/… همین‌طور) — نامگذار این‌ها را رزرو
 *  نمی‌کند ⇒ پیوندِ چارچوب با «OBJC_CLASS_$_TohidSharedDouble: symbol multiply defined» می‌افتد. این shim فقط برای
 *  کامپایلِ کدِ همین ماژول است، نه API ِ Swift (نگهبان: `ObjcNamesJvmTest`).
 */
internal object Double {
  fun longBitsToDouble(bits: Long): kotlin.Double = kotlin.Double.fromBits(bits)
  fun doubleToRawLongBits(value: kotlin.Double): Long = value.toRawBits()
  fun doubleToLongBits(value: kotlin.Double): Long = value.toBits()
}
