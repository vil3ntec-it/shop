package ir.vil3ntec.tohid.core.backup

/**
 *  بازیابیِ پشتیبان روی این دستگاه از کدام راه برود — S143 (§۲۸.۸)، درمانِ باگِ #455: «بازیابیِ
 *  پشتیبانِ کهنه روی دستگاهِ دوم، فروش و دریافت‌هایی را که دستگاهِ اول پس از پشتیبان ثبت کرده بود
 *  از سرور و بعد از خودِ دستگاهِ اول هم پاک کرد».
 *
 *  ── چه خراب بود ─────────────────────────────────────────────────────
 *  بازیابی یک «ذخیره»ٔ عادی بود: دفترِ پشتیبان با سایهٔ همگام‌سازی تفاضل گرفته می‌شد ⇐ برای هر
 *  ردیفی که پس از پشتیبان آمده بود DELETE و برای هر ویرایشِ تازه‌تر «برگرداندن» به صف می‌رفت؛
 *  سرور برای صاحبِ دکان می‌پذیرفت و دستگاه‌های دیگر در pull ِ بعدی همان را پاک می‌کردند.
 *
 *  ── قاعده: بازیابی هرگز op نمی‌سازد؛ سرور منبعِ حقیقتِ دکانِ همگام است ──
 *   • [Route.FROM_SERVER] (این حساب روی این دستگاه با سرور هم‌تراز شده): صفِ این دستگاه اول تا ته
 *     فرستاده می‌شود؛ بعد ردیف‌ها **فقط** از Snapshot ِ سرور و از پشتیبان فقط آنچه همگام نمی‌شود
 *     (تنظیمات)؛ سایه و cursor با همان Snapshot ⇐ تفاضلی نمی‌ماند: نه DELETE، نه برگرداندن.
 *     ردیفی از پشتیبان که سرور ندارد افزوده نمی‌شود — یا پس از پشتیبان حذف شده یا هرگز نرفته، و
 *     دستگاه نمی‌داند کدام (سرور ردیفِ حذف‌شده را با INSERT زنده می‌کند) — فقط شمرده می‌شود
 *     ([notOnServer]) تا پیام بگوید. فایلِ پشتیبان و نسخهٔ ایمنیِ پیش از بازیابی دست نمی‌خورند.
 *   • [Route.LOCAL] (هرگز هم‌تراز نشده): پشتیبان جای دفتر و **خودش سایه** ⇐ باز هم هیچ op؛
 *     نخستین هم‌ترازی با سرور فقط ردیف‌هایی را درج می‌کند که سرور ندارد، هرگز حذف.
 *   • همگام ولی بی ورود ([Route.NEED_SIGN_IN]) یا بی شبکه ([Route.NEED_NET])، یا Sync v3 ِ
 *     راه‌افتاده ([Route.NOT_ON_V3]، هنوز راهِ «از سرور» ندارد): هیچ چیزی عوض نمی‌شود، با پیام.
 *
 *  Kotlin ِ خالص (commonMain). قرینهٔ JS ِ همین در هارنسِ سرتاسری: `NativeDevice.restore`
 *  (`server/test/e2e/lib/native.mjs`) — یکی را عوض کنی، هر دو را.
 */
object RestoreRoute {

  enum class Route { LOCAL, FROM_SERVER, NEED_SIGN_IN, NEED_NET, NOT_ON_V3 }

  /**
   *  @param synced این حساب روی این دستگاه با سرور هم‌تراز شده است (v1: `everSynced`؛ v3: راه‌افتاده)
   *  @param v3 همگام‌سازیِ این دستگاه نسخهٔ ۳ است
   *  @param signedIn نشستِ حساب هست
   *  @param online شبکه هست
   */
  fun decide(synced: Boolean, v3: Boolean, signedIn: Boolean, online: Boolean): Route = when {
    !synced -> Route.LOCAL
    v3 -> Route.NOT_ON_V3
    !signedIn -> Route.NEED_SIGN_IN
    !online -> Route.NEED_NET
    else -> Route.FROM_SERVER
  }

  /**
   *  چند ردیفِ پشتیبان در دفترِ ساخته‌شده از سرور نیست — همان‌هایی که [Route.FROM_SERVER] نمی‌افزاید.
   *  @param backup مجموعه ⇐ شناسهٔ ردیف‌های پشتیبان
   *  @param server مجموعه ⇐ شناسهٔ ردیف‌های دفترِ تازه (از Snapshot)
   */
  fun notOnServer(backup: Map<String, Collection<String>>, server: Map<String, Collection<String>>): Int =
    backup.entries.sumOf { (table, ids) ->
      val have = server[table].orEmpty().toHashSet()
      ids.toHashSet().count { it !in have }
    }
}
