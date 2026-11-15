package ir.vil3ntec.tohid.core.rate

import ir.vil3ntec.tohid.core.platform.StoreChannel

/*
 *  RATE-01 — «به برنامه ستاره بدهید» (صاحب‌کار، ۱۴۰۵/۰۸/۲۴): برنامه گاهی، با یک برگهٔ کوچک، از کاربر می‌خواهد در فروشگاهِ
 *  همان ساخت (Google Play، کافه‌بازار، App Store) به برنامه ستاره بدهد؛ چند بار، و «دیگر نپرس» برای همیشه می‌بندد.
 *
 *  ⛔ قاعدهٔ فروشگاه‌ها (`docs/RULES-fa.md` «ستاره دادن»): هیچ پرسشِ «برنامه را دوست دارید؟» پیش از برگه نیست و هر کس
 *  «ستاره می‌دهم» بزند به فروشگاه می‌رود (بی جداکردنِ راضی و ناراضی)؛ شمارِ ستاره خواسته نمی‌شود؛ هیچ جایزه‌ای نیست؛ هیچ
 *  سنجه‌ای از اینکه چه کسی ستاره داد فرستاده نمی‌شود. حال فقط روی همین دستگاه می‌ماند (تنظیمِ رابط، نه دفتر و نه همگام).
 *
 *  منطقِ خالص (بی سکو، برای اندروید، کامپیوتر و آیفون): آزمونِ `RatePromptTest`.
 */

/** کجا ستاره داده می‌شود — از راهِ پخشِ همین ساخت (`BuildConfig.STORE`، `StoreChannel`) */
enum class RateTarget {
  /** APK ِ سایت/گیت‌هاب، نسخهٔ پیش‌نمایش و کامپیوتر: فروشگاهی نیست ⇒ هرگز پرسیده نمی‌شود */
  NONE,
  /** Google Play: صفحهٔ برنامه در Play (دکمهٔ برگه API ِ نظرِ درون‌برنامه را صدا نمی‌زند — راهنمای Google) */
  PLAY,
  /** کافه‌بازار: درِ رسمیِ نظر و امتیاز (`ACTION_EDIT`)، و اگر نشد صفحهٔ برنامه */
  BAZAAR,
  /** App Store: `SKStoreReviewController` ِ میزبانِ آیفون */
  APP_STORE;

  /**
   *  برگهٔ خودِ برنامه با سه دکمه (Play، بازار). App Store نه: قاعدهٔ ۵.۶.۱ ِ اپل «درخواستِ نظرِ سفارشی» را نمی‌پذیرد ⇒ همان
   *  لحظه فقط کارتِ خودِ سیستم (`SKStoreReviewController`) خواسته می‌شود؛ سیستم خودش سالی سه بار و کلیدِ خاموشِ «تنظیمات» دارد.
   */
  val ownSheet: Boolean get() = this == PLAY || this == BAZAAR

  companion object {
    /** همان مقدارِ `BuildConfig.STORE` ِ ساختِ آیفون (`ios/shared/build.gradle.kts`) */
    const val APP_STORE_CHANNEL = "appstore"

    fun of(store: String, preview: Boolean = false): RateTarget = when {
      preview -> NONE
      store == StoreChannel.PLAY -> PLAY
      store == StoreChannel.BAZAAR -> BAZAAR
      store == APP_STORE_CHANNEL -> APP_STORE
      else -> NONE
    }
  }
}

/** چرا دیگر هرگز پرسیده نمی‌شود */
enum class RateStop {
  /** «دیگر نپرس» */
  NEVER,
  /** «ستاره می‌دهم» ⇒ فروشگاه باز شد (چه ستاره داد، چه نه — دانسته نمی‌شود و پرسیده نمی‌شود) */
  STORE,
}

/** سه دکمهٔ برگه؛ بستنِ برگه با کشیدن یا «برگشت» همان [LATER] است */
enum class RateChoice { RATE, LATER, NEVER }

/**
 *  حالِ همین دستگاه. [firstSeenAt]: نخستین باری که این ساخت با فروشگاه روی همین دستگاه باز شد (هرگز زودتر از نصب؛ برای
 *  گوشیِ از پیش نصب‌شده، روزِ به‌روزرسانی). [asks]: چند بار برگه دیده شد. [lastAskAt]: آخرین دیده شدن یا «بعداً».
 */
data class RateState(
  val firstSeenAt: Long = 0,
  val asks: Int = 0,
  val lastAskAt: Long = 0,
  val stop: RateStop? = null,
)

/**
 *  یک لحظهٔ ممکن برای پرسیدن. [usage]: فروش‌های کاملِ دکان یا سفارش‌های ثبت‌شدهٔ خریدار؛ [sessionStartedAt]: آغازِ همین
 *  بارِ باز بودنِ برنامه؛ [usedThisSession]: در همین بار دست‌کم یک فروشِ کامل/سفارشِ تازه شد (میزبان می‌سنجد — نه با
 *  شمارِ فهرستِ سقف‌دار)؛ [busy]: وسطِ فروش یا پرداخت (سبدِ پُر، صفحهٔ صندوق/پرداخت، برگهٔ دیگرِ باز)؛ [lastErrorAt]:
 *  آخرین خطای دیده‌شده (۰ ⇒ هیچ)؛ [training]: حالتِ آموزشی.
 */
data class RateMoment(
  val now: Long,
  val target: RateTarget,
  val usage: Int,
  val minUsage: Int,
  val sessionStartedAt: Long,
  val usedThisSession: Boolean,
  val busy: Boolean = false,
  val lastErrorAt: Long = 0,
  val training: Boolean = false,
)

/** نخستین قاعده‌ای که پرسیدن را می‌بندد (به ترتیبِ [RatePrompt.block]) */
enum class RateBlock { NO_STORE, STOPPED, TRAINING, BUSY, ERROR, SESSION_START, TOO_NEW, LOW_USAGE, MAX_ASKS, SPACING }

object RatePrompt {
  /** دست‌کم یک هفته کار با برنامه (از [RateState.firstSeenAt]) */
  const val MIN_DAYS = 7
  /** برنامهٔ دکان: دست‌کم ۲۰ فروشِ کامل — یعنی برنامه واقعاً به کارِ روزانه رسیده */
  const val SHOP_MIN_SALES = 20
  /** برنامهٔ خریدار: دست‌کم ۲ سفارشِ ثبت‌شده (سفارشِ دوم یعنی برگشته است) */
  const val SHOPPER_MIN_ORDERS = 2
  /** روی هر دستگاه روی‌هم حداکثر سه بار */
  const val MAX_ASKS = 3
  /** میانِ دو پرسیدن (یا پس از «بعداً») دست‌کم ۳۰ روز */
  const val SPACING_DAYS = 30
  /** پس از هر خطای دیده‌شده (دفتر ننشست، همگام‌سازی ناکام، فروش بسته شد…) نیم ساعت نه */
  const val ERROR_QUIET_MS = 30 * 60_000L
  /** همین بارِ باز بودن: نه در سه دقیقهٔ نخست، و نه پیش از دست‌کم یک فروش/سفارشِ کاملِ تازه */
  const val SESSION_WARMUP_MS = 3 * 60_000L
  /** خانه باید این‌قدر جلوی چشم بماند (نه چشمکِ میانِ دو صفحه) */
  const val SETTLE_MS = 1_500L
  const val DAY_MS = 86_400_000L

  /** `null` ⇒ همین حالا بپرس؛ وگرنه نخستین قاعدهٔ بسته. ساعتِ عقب‌رفته همیشه «هنوز نه» است (هرگز زودتر) */
  fun block(s: RateState, m: RateMoment): RateBlock? = when {
    m.target == RateTarget.NONE -> RateBlock.NO_STORE
    s.stop != null -> RateBlock.STOPPED
    m.training -> RateBlock.TRAINING
    m.busy -> RateBlock.BUSY
    m.lastErrorAt > 0 && m.now - m.lastErrorAt < ERROR_QUIET_MS -> RateBlock.ERROR
    m.now - m.sessionStartedAt < SESSION_WARMUP_MS || !m.usedThisSession -> RateBlock.SESSION_START
    s.firstSeenAt <= 0 || m.now - s.firstSeenAt < MIN_DAYS * DAY_MS -> RateBlock.TOO_NEW
    m.usage < m.minUsage -> RateBlock.LOW_USAGE
    s.asks >= MAX_ASKS -> RateBlock.MAX_ASKS
    s.lastAskAt > 0 && m.now - s.lastAskAt < SPACING_DAYS * DAY_MS -> RateBlock.SPACING
    else -> null
  }

  fun shouldAsk(s: RateState, m: RateMoment): Boolean = block(s, m) == null

  /** نخستین بار روی همین دستگاه ⇒ آغازِ شمارشِ هفت روز */
  fun seen(s: RateState, now: Long): RateState = if (s.firstSeenAt > 0) s else s.copy(firstSeenAt = now)

  /** برگه همین حالا باز شد — پیش از هر دکمه شمرده می‌شود، پس بستنِ برنامه هم یک بار است (هرگز بیش از سه) */
  fun shown(s: RateState, now: Long): RateState = s.copy(asks = s.asks + 1, lastAskAt = now)

  fun chose(s: RateState, choice: RateChoice, now: Long): RateState = when (choice) {
    RateChoice.RATE -> s.copy(stop = RateStop.STORE)
    RateChoice.NEVER -> s.copy(stop = RateStop.NEVER)
    RateChoice.LATER -> s.copy(lastAskAt = maxOf(s.lastAskAt, now))
  }

  /** یک رشته در تنظیمِ همین دستگاه: `1|firstSeenAt|asks|lastAskAt|stop` */
  fun encode(s: RateState): String =
    listOf("1", s.firstSeenAt.toString(), s.asks.toString(), s.lastAskAt.toString(), s.stop?.name?.lowercase().orEmpty()).joinToString("|")

  /** ناخوانا ⇒ از نو؛ «ایستِ» ناشناخته ⇒ «دیگر نپرس» (هرگز بیشتر از آنچه کاربر خواست نپرسد) */
  fun decode(raw: String?): RateState {
    val p = raw?.split('|') ?: return RateState()
    if (p.size != 5 || p[0] != "1") return RateState()
    val stop = when (p[4]) {
      "" -> null
      "store" -> RateStop.STORE
      else -> RateStop.NEVER
    }
    return RateState(
      firstSeenAt = p[1].toLongOrNull()?.coerceAtLeast(0) ?: 0,
      asks = p[2].toIntOrNull()?.coerceAtLeast(0) ?: 0,
      lastAskAt = p[3].toLongOrNull()?.coerceAtLeast(0) ?: 0,
      stop = stop,
    )
  }
}

/** کارِ یک «در»: [VIEW] دیدنِ صفحه، [EDIT] «نظر و امتیاز» ِ بازار (`Intent.ACTION_VIEW`/`ACTION_EDIT` در مرزِ اندروید) */
enum class RateAction { VIEW, EDIT }

/** یک «در» برای باز کردنِ فروشگاه (همان Intent ِ اندروید، به‌شکلِ داده تا آزموده شود) */
data class RateLink(val action: RateAction, val uri: String, val pkg: String? = null)

/** درهای فروشگاه به ترتیب؛ نخستینِ بازشدنی برنده است */
object RateLinks {
  const val BAZAAR_PACKAGE = "com.farsitel.bazaar"
  const val PLAY_PACKAGE = "com.android.vending"

  /** کافه‌بازار: درِ رسمیِ «نظر و امتیاز» (`ACTION_EDIT` روی `bazaar://details`)، صفحهٔ برنامه، و وبِ بازار */
  fun bazaar(appId: String): List<RateLink> = listOf(
    RateLink(RateAction.EDIT, "bazaar://details?id=$appId", BAZAAR_PACKAGE),
    RateLink(RateAction.VIEW, "bazaar://details?id=$appId", BAZAAR_PACKAGE),
    RateLink(RateAction.VIEW, "https://cafebazaar.ir/app/$appId"),
  )

  /** صفحهٔ برنامه در Play: برنامهٔ Play، و بی آن وبِ Play */
  fun playListing(appId: String): List<RateLink> = listOf(
    RateLink(RateAction.VIEW, "market://details?id=$appId", PLAY_PACKAGE),
    RateLink(RateAction.VIEW, "https://play.google.com/store/apps/details?id=$appId"),
  )
}
