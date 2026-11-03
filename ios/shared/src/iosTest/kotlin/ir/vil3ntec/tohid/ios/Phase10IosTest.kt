package ir.vil3ntec.tohid.ios

import android.content.IosContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import ir.vil3ntec.tohid.ios.IosHaptics.Event
import ir.vil3ntec.tohid.ios.IosHaptics.Feedback
import ir.vil3ntec.tohid.ui.screens.Haptics
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 *  گامِ ۱۰ ِ آیفون روی شبیه‌ساز (کارِ `ios-app`؛ روی لینوکس فقط کامپایل می‌شود): بازخوردِ لمسیِ UIKit با همان
 *  رویدادهای معناییِ اندروید (`IosHaptics`، docs/IOS-PLAN-fa.md §۰.۱۵) — نقشهٔ رویداد ⇐ مولد، نوعِ Compose ⇐ رویداد،
 *  و `Haptics` / `LocalHapticFeedback` در فرآیندِ بی بسته بی هیچ مولدی (فقط [IosHaptics.recent]).
 */
class Phase10IosTest {
  @Test
  fun harRooydadMoladeKhodashRaDarad() {
    assertEquals(Feedback.Notify(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess), IosHaptics.feedbackOf(Event.SUCCESS))
    assertEquals(Feedback.Notify(UINotificationFeedbackType.UINotificationFeedbackTypeError), IosHaptics.feedbackOf(Event.ERROR))
    assertEquals(Feedback.Impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium), IosHaptics.feedbackOf(Event.CONFIRM))
    assertEquals(Feedback.Select, IosHaptics.feedbackOf(Event.SELECTION))
    assertEquals(Feedback.Impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleRigid), IosHaptics.feedbackOf(Event.THRESHOLD))
  }

  @Test
  fun noeComposeHamanManayeAndroid() {
    //  همان نوع‌هایی که کدِ مشترک می‌زند: پلهٔ کاشیِ صندوق، آستانهٔ کششِ خانه، دکمهٔ لرزش‌دارِ کیت
    assertEquals(Event.SELECTION, IosHaptics.eventOf(HapticFeedbackType.TextHandleMove))
    assertEquals(Event.THRESHOLD, IosHaptics.eventOf(HapticFeedbackType.GestureThresholdActivate))
    assertEquals(Event.CONFIRM, IosHaptics.eventOf(HapticFeedbackType.LongPress))
    assertEquals(Event.SUCCESS, IosHaptics.eventOf(HapticFeedbackType.Confirm))
    assertEquals(Event.ERROR, IosHaptics.eventOf(HapticFeedbackType.Reject))
    assertEquals(Event.SELECTION, IosHaptics.eventOf(HapticFeedbackType.SegmentTick))
    assertNull(IosHaptics.eventOf(HapticFeedbackType.GestureEnd))
  }

  @Test
  fun biroonAzBarnameBiMoladFaghatSabt() {
    assertFalse(IosHost.inApp, "آزمون درونِ .app است")
    IosHaptics.clearRecent()
    Haptics.success(IosContext)
    Haptics.confirm(IosContext)
    Haptics.error(IosContext)
    IosHapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    IosHapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    IosHapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
    assertEquals(listOf(Event.SUCCESS, Event.CONFIRM, Event.ERROR, Event.SELECTION, Event.THRESHOLD), IosHaptics.recent)
    IosHaptics.clearRecent()
  }
}
