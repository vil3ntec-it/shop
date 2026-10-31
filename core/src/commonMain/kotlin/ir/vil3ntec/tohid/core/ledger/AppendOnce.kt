package ir.vil3ntec.tohid.core.ledger

/**
 *  ثبتِ «افزودنی» در دفتر — رسیدِ قرض و هر ردیفِ تازهٔ دیگری که نباید از تصویرِ کهنهٔ صفحه نوشته شود.
 *
 *  ⛔ چرا: صفحه دفتری را که نشان می‌دهد در دست دارد و پیش‌تر `save(همان دفتر + ردیفِ تازه)` می‌کرد. اگر در
 *  این فاصله نوشتنِ دیگری رسیده بود (Pull ِ همگام‌سازی از دستگاهِ دیگر، نوشتنِ پس‌زمینه)، کلِ دفترِ کهنه روی
 *  آن می‌نشست: ردیفِ تازهٔ دیگران گم می‌شد و تفاضلِ همگام‌سازی برایش DELETE به سرور می‌فرستاد (lost update).
 *
 *  قاعده‌ها:
 *    • ردیف فقط از راهِ درِ نوشتنِ خودِ دفتر ساخته می‌شود ([append] ⇐ `update`: خواندن ⇐ تبدیل ⇐ نوشتن، **زیرِ
 *      همان قفلی** که هر نوشتنِ دیگری هم زیرِ آن است) و روی دفترِ همین لحظه ([decide])، نه نسخهٔ صفحه.
 *    • هر ثبت یک کلید دارد که **پیش از** ثبت ساخته می‌شود و همان شناسهٔ ردیفِ تازه است. کلیدی که در دفتر هست
 *      دوباره نوشته نمی‌شود ⇒ [Outcome.Already]: دو ضربهٔ پشتِ‌سرِهم یا تکرار پس از قطع، یک ردیف.
 *    • دفترِ همین لحظه خوانده نشد ⇒ [Outcome.Unread] و هیچ نوشتنی — بهتر «ثبت نشد» تا رونویسیِ دفترِ واقعی.
 *    • تبدیل چیزی برای نوشتن ندارد (`null`، مثلاً مبلغِ نادرست) ⇒ [Outcome.Skipped]؛ دلیل را خودش نگه می‌دارد.
 *
 *  بی نخ و بی فایل: قفل و دیسک مالِ `update` ِ فراخوان است (`DebtEntry` ⇐ `ShopStore.update`).
 */
object AppendOnce {

  sealed interface Outcome<out S> {
    /** نوشته شد ([append])، یا از [decide]: «همین [data] را بنویس» */
    data class Wrote<out S>(val data: S) : Outcome<S>

    /** همین کلید پیش‌تر ثبت شده بود — هیچ نوشته نشد */
    data object Already : Outcome<Nothing>

    /** تبدیل چیزی برای نوشتن نداشت — هیچ نوشته نشد */
    data object Skipped : Outcome<Nothing>

    /** دفترِ همین لحظه خوانده نشد — هیچ نوشته نشد */
    data object Unread : Outcome<Nothing>

    /** نوشتن رد شد یا به دیسک نرسید (`update` ⇒ `false`) */
    data object Unsaved : Outcome<Nothing>
  }

  /**
   *  تصمیمِ خالص روی دفترِ همین لحظه [cur]؛ فراخوان قفلِ نوشتن را دارد.
   *
   *  @param key شناسهٔ ردیفِ تازه که پیش از ثبت ساخته شد؛ خالی پذیرفته نیست
   *  @param has آیا ردیفی با این کلید در دفتر هست
   *  @param change ردیفِ تازه با شناسهٔ [key] روی [cur]؛ سبک و خالص، چون زیرِ قفل می‌دود
   */
  fun <S : Any> decide(cur: S?, key: String, has: (S, String) -> Boolean, change: (S, String) -> S?): Outcome<S> {
    require(key.isNotBlank()) { "کلیدِ ثبت خالی است" }
    if (cur == null) return Outcome.Unread
    if (has(cur, key)) return Outcome.Already
    return change(cur, key)?.let { Outcome.Wrote(it) } ?: Outcome.Skipped
  }

  /**
   *  یک ثبت از راهِ [update] ِ دفتر: [update] تبدیلِ داده‌شده را زیرِ قفلِ نوشتن روی دفترِ همین لحظه (`null` =
   *  خوانده نشد) می‌دواند و اگر چیزی برگرداند می‌نویسد — همان قراردادِ `ShopStore.update`: `null` هیچ، `true`
   *  نوشته شد، `false` قفلِ نرم یا دیسک. `inline` تا [update] بتواند تابعِ تعلیقی (`ShopStore.update`) صدا بزند.
   */
  inline fun <S : Any> append(
    key: String,
    noinline has: (S, String) -> Boolean,
    noinline change: (S, String) -> S?,
    update: (transform: (S?) -> S?) -> Boolean?,
  ): Outcome<S> {
    require(key.isNotBlank()) { "کلیدِ ثبت خالی است" }
    //  `update` پیش از تبدیل برگشت (دفتر خوانده نشد، دفترِ حسابِ دیگر) ⇒ هیچ نوشتنی
    var out: Outcome<S> = Outcome.Unsaved
    val wrote = update { cur -> decide(cur, key, has, change).also { out = it }.let { (it as? Outcome.Wrote)?.data } }
    val o = out
    return if (o is Outcome.Wrote && wrote != true) Outcome.Unsaved else o
  }
}
