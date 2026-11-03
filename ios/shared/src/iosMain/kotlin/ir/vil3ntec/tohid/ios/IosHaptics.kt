package ir.vil3ntec.tohid.ios

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import platform.Foundation.NSThread
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 *  بازخوردِ لمسیِ آیفون (گامِ ۱۰، docs/IOS-PLAN-fa.md §۰.۱۵) — تصمیمِ صاحب‌کار: راهنمای اپل (HIG)، فقط مولدهای
 *  خودِ UIKit (`UINotificationFeedbackGenerator`، `UIImpactFeedbackGenerator`، `UISelectionFeedbackGenerator`)،
 *  بی الگوی سفارشیِ Core Haptics.
 *
 *  همان رویدادهای معناییِ اندروید، نه الگوی `Vibrator` ِ آن:
 *  - **موفق** (`Haptics.success`: فروش/پرداخت/ذخیره ثبت شد؛ `HapticFeedbackType.Confirm`) ⇐ اعلانِ success
 *  - **خطا** (`Haptics.error`: رد، لرزشِ `v3Shake`؛ `Reject`) ⇐ اعلانِ error
 *  - **تأیید** (`Haptics.confirm`: یک ضربِ محکم؛ زدنِ دکمهٔ لرزش‌دارِ کیت `LongPress`) ⇐ ضربهٔ متوسط
 *  - **انتخاب** (یک پله در کاشیِ صندوق `TextHandleMove`؛ `SegmentTick`، کلید) ⇐ `selectionChanged`
 *  - **آستانه** (گذر از آستانهٔ کششِ کادرِ خانه `GestureThresholdActivate`) ⇐ ضربهٔ سخت (rigid)
 *
 *  تنظیمِ سیستم: مولدهای UIKit خودشان «لرزش‌های سیستم» (Settings ⇐ Sounds & Haptics ⇐ System Haptics) را رعایت
 *  می‌کنند — خاموش ⇐ هیچ؛ برنامه در پس‌زمینه، iPad و آیفونِ بی Taptic Engine هم هیچ. راهِ دیگری (Core Haptics،
 *  AudioServices) که این تنظیم را دور بزند به کار نمی‌رود.
 *
 *  فرآیندِ بی بستهٔ برنامه (آزمونِ شبیه‌ساز، بی UIApplication، `IosHost`) هیچ مولدی نمی‌سازد؛ رویداد فقط در
 *  [recent] می‌نشیند تا آزمون ببیند کدام معنا رسید (در برنامه هم، بی هزینه: ۱۶ ردیفِ آخر).
 */
internal object IosHaptics {
  enum class Event { SUCCESS, ERROR, CONFIRM, SELECTION, THRESHOLD }

  /** مولد و نوعِ UIKit ِ هر رویداد */
  sealed interface Feedback {
    data class Notify(val type: UINotificationFeedbackType) : Feedback
    data class Impact(val style: UIImpactFeedbackStyle) : Feedback
    data object Select : Feedback
  }

  fun feedbackOf(e: Event): Feedback = when (e) {
    Event.SUCCESS -> Feedback.Notify(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
    Event.ERROR -> Feedback.Notify(UINotificationFeedbackType.UINotificationFeedbackTypeError)
    Event.CONFIRM -> Feedback.Impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    Event.SELECTION -> Feedback.Select
    Event.THRESHOLD -> Feedback.Impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleRigid)
  }

  /** نوعِ Compose (`LocalHapticFeedback` ِ کدِ مشترک) ⇐ همان معنا؛ `GestureEnd` و ناشناخته ⇐ هیچ */
  fun eventOf(type: HapticFeedbackType): Event? = when (type) {
    HapticFeedbackType.Confirm -> Event.SUCCESS
    HapticFeedbackType.Reject -> Event.ERROR
    HapticFeedbackType.LongPress, HapticFeedbackType.ContextClick, HapticFeedbackType.VirtualKey,
    HapticFeedbackType.KeyboardTap, HapticFeedbackType.ToggleOn, HapticFeedbackType.ToggleOff -> Event.CONFIRM
    HapticFeedbackType.TextHandleMove, HapticFeedbackType.SegmentTick, HapticFeedbackType.SegmentFrequentTick -> Event.SELECTION
    HapticFeedbackType.GestureThresholdActivate -> Event.THRESHOLD
    else -> null
  }

  private const val RECENT_MAX = 16
  private val ring = ArrayDeque<Event>()

  /** رویدادهای آخر (فقط رشتهٔ اصلی) */
  val recent: List<Event> get() = ring.toList()

  fun clearRecent() = ring.clear()

  /** از هر رشته؛ UIKit فقط روی رشتهٔ اصلی */
  fun play(e: Event) {
    if (NSThread.isMainThread) fire(e) else dispatch_async(dispatch_get_main_queue()) { fire(e) }
  }

  private fun fire(e: Event) {
    ring.addLast(e)
    while (ring.size > RECENT_MAX) ring.removeFirst()
    if (!IosHost.inApp) return
    when (val f = feedbackOf(e)) {
      is Feedback.Notify -> notifier.notificationOccurred(f.type)
      is Feedback.Impact -> impact(f.style).impactOccurred()
      Feedback.Select -> selection.selectionChanged()
    }
  }

  private val notifier by lazy { UINotificationFeedbackGenerator() }
  private val selection by lazy { UISelectionFeedbackGenerator() }
  private val impacts = HashMap<UIImpactFeedbackStyle, UIImpactFeedbackGenerator>()

  private fun impact(style: UIImpactFeedbackStyle) = impacts.getOrPut(style) { UIImpactFeedbackGenerator(style = style) }
}

/** `LocalHapticFeedback` ِ ریشهٔ آیفون (`IosRoot`): همان [IosHaptics]، تا رویدادهای Compose و `Haptics` یک نقشه داشته باشند */
internal object IosHapticFeedback : HapticFeedback {
  override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
    IosHaptics.eventOf(hapticFeedbackType)?.let(IosHaptics::play)
  }
}
