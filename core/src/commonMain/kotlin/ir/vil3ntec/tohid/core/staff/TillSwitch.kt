package ir.vil3ntec.tohid.core.staff

/**
 *  تعویضِ کاربر روی صندوق — S46، STF-04 (MASTER-SPEC بخشِ ۱۰.۱).
 *
 *  «Ctrl+L صندوق را قفل می‌کند. کاربرِ دوم با PIN وارد می‌شود. فاکتورِ نیمه‌کارهٔ کاربرِ اول نگه داشته می‌شود
 *  و سابقه‌ها به نامِ کاربرِ درست ثبت می‌شوند.»
 *
 *  دستگاه با یک حساب وارد شده («حسابِ دستگاه»، توکنِ همگام‌سازی). هر کاربرِ دیگری که روی همین دستگاه PIN ِ
 *  شخصی دارد (`ApproverVault.Record`: نمک و اثرِ PBKDF2، شمارِ خطا و قفل) می‌تواند صندوقِ قفل را باز کند و
 *  «کاربرِ صندوق» شود. این‌جا فقط قاعده‌هاست؛ PIN را همان `ApproverVault.verify` می‌سنجد.
 *
 *  ⛔ امنیت:
 *   • کاربرِ صندوق **هرگز** بیش از حسابِ دستگاه اختیار نمی‌گیرد ([effectiveRole]): سرور با توکنِ همان حساب
 *     می‌سنجد، پس بالا بردن فقط ردِ سرور می‌ساخت؛ پایین آوردن (شاگرد پشتِ صندوقِ صاحب) لازم است، وگرنه
 *     شاگرد با اختیارِ صاحب می‌فروخت.
 *   • قفل فقط وقتی با PIN های شخصی می‌افتد که حسابِ دستگاه راهِ برگشت دارد (PIN ِ خودش یا رمزِ دستگاه) —
 *     [canLock]؛ وگرنه صندوق پشتِ قفلی می‌ماند که صاحبش بازش نمی‌کند.
 *   • هر کاربر سبدِ خودش را دارد ([swap]): سبدِ کاربرِ پیشین کنار گذاشته و سبدِ کاربرِ تازه برگردانده می‌شود؛
 *     هیچ‌کدام سبدِ دیگری را نمی‌بیند.
 */
object TillSwitch {

  /** کسی که روی این دستگاه PIN دارد */
  data class User(val userId: String, val name: String, val role: String, val locked: Boolean = false)

  /** رتبهٔ اختیار برای «هرگز بالا نبر»: صاحب > مدیر > بقیه */
  fun rank(role: String?): Int = when (role?.trim()?.lowercase()) {
    "owner" -> 3
    "manager" -> 2
    else -> 1
  }

  /** نقشی که برنامه به‌کار می‌برد: نقشِ کاربرِ صندوق فقط اگر پایین‌تر از حسابِ دستگاه است */
  fun effectiveRole(account: String, active: String?): String {
    val a = active?.trim()?.lowercase().orEmpty()
    return if (a.isEmpty() || rank(a) >= rank(account)) account else a
  }

  /**
   *  قفلِ «هر کس با PIN ِ خودش» می‌افتد؟ تنظیمِ روشن، دست‌کم یک کاربرِ PIN‌دار، و راهِ برگشتِ حسابِ دستگاه.
   *  نه ⇒ همان قفلِ امروز (رمزِ دستگاه) یا راهنمای «رمز بگذارید».
   */
  fun canLock(on: Boolean, users: List<User>, deviceUserId: String, deviceCode: Boolean): Boolean =
    on && users.isNotEmpty() && (deviceCode || users.any { it.userId == deviceUserId && !it.locked })

  /** کاربرِ صندوق پس از باز کردن با PIN ِ [userId]؛ `null` = خودِ حسابِ دستگاه */
  fun activeAfter(deviceUserId: String, userId: String?): String? =
    userId?.trim()?.takeIf { it.isNotEmpty() && it != deviceUserId }

  /** خانهٔ سبدِ یک کاربر؛ «» = حسابِ دستگاه (همان سبدِ امروز) */
  fun slot(active: String?): String = active?.trim().orEmpty()

  /** جابه‌جاییِ سبد: سبدِ [stash] کنار، سبدِ [restore] به صندوق */
  data class Swap(val stash: String, val restore: String)

  /** `null` ⇒ همان کاربر؛ سبد دست نمی‌خورد */
  fun swap(fromActive: String?, toActive: String?): Swap? {
    val from = slot(fromActive)
    val to = slot(toActive)
    return if (from == to) null else Swap(from, to)
  }

  /** نامِ پروندهٔ سبدِ کنارگذاشته — فقط حروفِ امن (شناسه به هگز) */
  fun fileName(slot: String): String =
    if (slot.isEmpty()) "device.json"
    else "u-" + slot.encodeToByteArray().joinToString("") { b -> (b.toInt() and 0xff).toString(16).padStart(2, '0') } + ".json"

  /** نامِ فروشنده و نویسندهٔ سابقه: کاربرِ صندوق، وگرنه حسابِ دستگاه */
  fun seller(accountName: String, active: User?): String =
    active?.name?.trim()?.takeIf { it.isNotEmpty() } ?: accountName

  /** فهرستِ صفحهٔ قفل: کاربرانِ PIN‌دار، حسابِ دستگاه نخست، بعد به نام */
  fun lockList(users: List<User>, deviceUserId: String): List<User> =
    users.sortedWith(compareBy<User>({ it.userId != deviceUserId }, { it.name }))
}
