package ir.vil3ntec.tohid.core.sync

/**
 *  هشدارِ قرمزِ ساعتِ دستگاه — S28b، MASTER-SPEC بخشِ ۳.۸.۱ بندِ ۸ (SYN-05).
 *
 *  «هر پاسخِ سرور زمانِ سرور را دارد. دستگاه اختلافِ ساعتش را نگه می‌دارد» (`V3Status.clockOffsetMs` =
 *  زمانِ سرور − ساعتِ دستگاه) و «اگر ساعتِ دستگاه از بالاترین زمانِ دیده‌شده عقب‌تر باشد، هشدارِ قرمز
 *  نمایش داده می‌شود». مرز همان نگهبانِ سرور است (`hlc.SKEW_MS`، `skew_limit_ms`): ۵ دقیقه. سند با همان
 *  ساعتِ دستگاه ثبت می‌شود و جابه‌جا نمی‌شود؛ «تاریخِ تصحیح‌شده» را سرور کنارش می‌گذارد (`Sale.correctedAt`).
 *
 *  خالص: ساعت از صدازننده می‌آید.
 */
object ClockSkew {

  /** همان `SKEW_MS` ِ سرور و موتورِ v3 */
  const val SKEW_MS: Long = 5L * 60 * 1000

  enum class Level { OK, BEHIND, AHEAD }

  /**
   *  پاسخِ سرور ([offsetMs]) اگر هست، فقط همان ملاک است (ساعتی که پیش‌تر جلو بود و حالا درست شده، با
   *  «بالاترین زمانِ دیده‌شده» ِ کهنه هشدارِ دروغ نمی‌گیرد)؛ بی پاسخِ سرور، «بالاترین زمانِ دیده‌شده».
   *
   *  @param offsetMs زمانِ سرور − ساعتِ دستگاه از آخرین پاسخ؛ `null` ⇒ هنوز پاسخی نیامده
   *  @param rawNow ساعتِ خامِ دستگاه؛ ۰ ⇒ سنجشِ «بالاترین زمانِ دیده‌شده» نه
   *  @param seenMax بالاترین ساعتی که همین دستگاه دیده (`SyncStore.clockSeen`)؛ ۰ ⇒ نامعلوم
   */
  fun level(offsetMs: Long?, rawNow: Long = 0, seenMax: Long = 0): Level = when {
    offsetMs != null -> when {
      offsetMs > SKEW_MS -> Level.BEHIND
      offsetMs < -SKEW_MS -> Level.AHEAD
      else -> Level.OK
    }
    rawNow > 0 && seenMax > 0 && rawNow < seenMax - SKEW_MS -> Level.BEHIND
    else -> Level.OK
  }

  /** نوارِ قرمز نشان داده شود؟ */
  fun red(level: Level): Boolean = level != Level.OK

  /** زمانِ تصحیح‌شده: ساعتِ دستگاه + اختلاف */
  fun corrected(rawNow: Long, offsetMs: Long): Long = rawNow + offsetMs

  /** اندازهٔ اختلاف برای متن: (روز، ساعت، دقیقه)؛ همیشه نامنفی */
  fun gap(offsetMs: Long): Triple<Long, Long, Long> {
    val m = (if (offsetMs < 0) -offsetMs else offsetMs) / 60_000
    return Triple(m / 1440, (m % 1440) / 60, m % 60)
  }
}
