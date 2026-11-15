package ir.vil3ntec.tohid.ui.v3

import androidx.compose.ui.semantics.SemanticsPropertyKey
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** فقط آزمون: گرهِ جشنِ روی پیام (تزئینی؛ صفحه‌خوان متنِ خودِ پیام را می‌خواند) */
val V3CelebrationKey = SemanticsPropertyKey<Boolean>("V3Celebration")

/**
 *  جشنِ موفقیت از راهِ پیام‌های مشترک (دستهٔ ۶۳، e-rows-63i): راهِ مشترکِ هر ثبتِ مهم یک بار [fire] می‌زند — ثبتِ فروش
 *  (`CartStore.post` ⇐ همهٔ صندوق‌ها: گوشی، فروشِ سریع، کامپیوتر)، و پرداخت، رسید، قرض و خرید (`MoneyWriter`). پیامِ
 *  «موفق» ِ بعدی (`V3SnackbarHost`) تا [WINDOW] پس از آن، به‌جای نقطهٔ رنگی، تیکِ کشیده‌شونده با چند ذرهٔ رنگی
 *  ([ir.vil3ntec.tohid.ui.v3.art.V3CheckBurst]) نشان می‌دهد — یک بار، روی پیامی که خودِ ثبت داد. هیچ صفحه‌ای جدا چیزی
 *  نمی‌سازد و هیچ لمسی گرفته نمی‌شود: صندوق‌دار همان لحظه اسکنِ بعدی را می‌زند.
 *
 *  صندوقی که پیام نمی‌دهد (رسیدِ تسویهٔ گوشی) جشنِ خودش را در `V3SuccessCheck` دارد؛ [fire] ِ بی پیام پس از [WINDOW]
 *  خودش کهنه می‌شود و به پیامِ دیگری نمی‌چسبد.
 */
object V3Celebrate {

  /** پیامِ «موفق» تا چند ثانیه پس از ثبت جشن دارد (پیامِ پیشینِ صفِ پیام‌ها کوتاه است: ۴ ثانیه) */
  val WINDOW = 5.seconds

  /** جشنی که پیامی برداشت: لرزشِ سبکِ `HapticFeedbackType.Confirm` اگر راهِ ثبت خودش نلرزانده بود */
  class Claim(val haptic: Boolean)

  @Volatile private var mark: TimeSource.Monotonic.ValueTimeMark? = null
  @Volatile private var haptic = false

  /** شمارِ [fire] از آغازِ برنامه — فقط آزمون */
  @Volatile var fired: Int = 0
    private set

  /**
   *  ثبتِ مهم همین حالا انجام شد.
   *  @param haptic false وقتی راهِ ثبت خودش لرزشِ «ثبت شد» (`Haptics.success`) را زده است
   */
  fun fire(haptic: Boolean = true) {
    this.haptic = haptic
    mark = TimeSource.Monotonic.markNow()
    fired++
  }

  /** پیامِ «موفق» ِ تازه جشن را برمی‌دارد (یک بار)؛ کهنه یا نبوده ⇒ null */
  fun claim(): Claim? {
    val m = mark ?: return null
    mark = null
    return if (m.elapsedNow() <= WINDOW) Claim(haptic) else null
  }

  /** فقط آزمون: جشنِ مانده پاک */
  internal fun reset() { mark = null }
}
