package ir.vil3ntec.tohid.core.chat

import ir.vil3ntec.tohid.core.chat.VoiceFlow.Action
import ir.vil3ntec.tohid.core.chat.VoiceFlow.Effect
import ir.vil3ntec.tohid.core.chat.VoiceFlow.Input
import ir.vil3ntec.tohid.core.chat.VoiceFlow.Phase
import ir.vil3ntec.tohid.core.chat.VoiceFlow.Say
import ir.vil3ntec.tohid.core.chat.VoiceFlow.Step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ضبطِ پیامِ صوتی: نگه‌داشتن همان پیشین، زدن (صفحه‌خوان) تا «پایان و فرستادن» یا «لغو» */
class VoiceFlowTest {

  private val limit = 1_000_000L

  /** نگه‌داشتن: فشار ⇐ ضبط، رها کردن ⇐ ردیفِ پیش از فرستادن، «فرستادنِ صدا» ⇐ رفت */
  @Test
  fun holdRecordsUntilReleaseThenWaitsForSend() {
    assertEquals(Step(Phase.HOLD, Effect.START, Say.RECORDING), VoiceFlow.next(Phase.IDLE, Input.PRESS))
    assertEquals(Step(Phase.READY, Effect.STOP), VoiceFlow.next(Phase.HOLD, Input.RELEASE))
    assertEquals(Step(Phase.READY, Effect.NONE, Say.READY), VoiceFlow.settle(send = false, size = 40_000, limit = limit, canWrite = true))
    assertEquals(Step(Phase.IDLE, Effect.SEND, Say.SENT), VoiceFlow.next(Phase.READY, Input.SEND))
  }

  /** زدنِ کوتاه به‌جای نگه‌داشتن: چیزی ضبط نشد ⇒ بیکار، و صفحه‌خوان همین را می‌گوید */
  @Test
  fun shortPressRecordsNothing() {
    assertEquals(Step(Phase.IDLE, Effect.NONE, Say.EMPTY), VoiceFlow.settle(send = false, size = null, limit = limit, canWrite = true))
    assertEquals(Step(Phase.IDLE, Effect.NONE, Say.EMPTY), VoiceFlow.settle(send = true, size = null, limit = limit, canWrite = true))
  }

  /** زدن: رها کردن و فشارِ دوباره ضبط را نمی‌بندند؛ «پایانِ ضبط و فرستادن» یک‌جا می‌فرستد */
  @Test
  fun tapRecordsUntilFinishAndSends() {
    assertEquals(Step(Phase.TAP, Effect.START, Say.RECORDING), VoiceFlow.next(Phase.IDLE, Input.START))
    assertEquals(Step(Phase.TAP, Effect.NONE), VoiceFlow.next(Phase.TAP, Input.RELEASE))
    assertEquals(Step(Phase.TAP, Effect.NONE), VoiceFlow.next(Phase.TAP, Input.PRESS))
    assertEquals(Step(Phase.TAP, Effect.NONE), VoiceFlow.next(Phase.TAP, Input.START))
    assertEquals(Step(Phase.READY, Effect.STOP_SEND), VoiceFlow.next(Phase.TAP, Input.FINISH))
    assertEquals(Step(Phase.IDLE, Effect.SEND, Say.SENT), VoiceFlow.settle(send = true, size = 40_000, limit = limit, canWrite = true))
  }

  /** «پایان و فرستادن» با صدای بزرگ‌تر از سقف یا نوشتنِ بسته نمی‌فرستد؛ ردیفِ پیش از فرستادن می‌ماند */
  @Test
  fun finishDoesNotSendOverLimitOrWhenClosed() {
    assertEquals(Step(Phase.READY, Effect.NONE, Say.TOO_BIG), VoiceFlow.settle(send = true, size = limit + 1, limit = limit, canWrite = true))
    assertEquals(Step(Phase.READY, Effect.NONE, Say.READY), VoiceFlow.settle(send = true, size = 40_000, limit = limit, canWrite = false))
    assertEquals(Step(Phase.IDLE, Effect.SEND, Say.SENT), VoiceFlow.settle(send = true, size = limit, limit = limit, canWrite = true))
    assertTrue(VoiceFlow.tooBig(limit + 1, limit))
    assertFalse(VoiceFlow.tooBig(limit, limit))
    assertFalse(VoiceFlow.tooBig(Long.MAX_VALUE, 0), "سقفِ صفر یعنی سقفی نیست")
  }

  /** «لغو»: در ضبط ضبط‌کننده لغو می‌شود، در ردیفِ آماده فقط صدا کنار می‌رود */
  @Test
  fun cancelStopsOrDiscards() {
    assertEquals(Step(Phase.IDLE, Effect.CANCEL, Say.CANCELLED), VoiceFlow.next(Phase.TAP, Input.CANCEL))
    assertEquals(Step(Phase.IDLE, Effect.CANCEL, Say.CANCELLED), VoiceFlow.next(Phase.HOLD, Input.CANCEL))
    assertEquals(Step(Phase.IDLE, Effect.DISCARD, Say.CANCELLED), VoiceFlow.next(Phase.READY, Input.CANCEL))
    assertEquals(Step(Phase.IDLE, Effect.NONE), VoiceFlow.next(Phase.IDLE, Input.CANCEL))
  }

  /** سقفِ ۵ دقیقه هر دو راه را مثلِ رها کردن می‌بندد؛ رها کردنِ پس از سقف کاری نمی‌کند */
  @Test
  fun limitStopsBothModes() {
    assertEquals(Step(Phase.READY, Effect.STOP), VoiceFlow.next(Phase.HOLD, Input.LIMIT))
    assertEquals(Step(Phase.READY, Effect.STOP), VoiceFlow.next(Phase.TAP, Input.LIMIT))
    assertEquals(Step(Phase.READY, Effect.NONE), VoiceFlow.next(Phase.READY, Input.RELEASE))
    assertEquals(Step(Phase.IDLE, Effect.NONE), VoiceFlow.next(Phase.IDLE, Input.LIMIT))
  }

  /** ضبطِ تازه از ردیفِ آماده صدای پیشین را کنار می‌گذارد (همان رفتارِ پیشین) */
  @Test
  fun recordingAgainFromReady() {
    assertEquals(Step(Phase.HOLD, Effect.START, Say.RECORDING), VoiceFlow.next(Phase.READY, Input.PRESS))
    assertEquals(Step(Phase.TAP, Effect.START, Say.RECORDING), VoiceFlow.next(Phase.READY, Input.START))
    assertEquals(Step(Phase.IDLE, Effect.NONE), VoiceFlow.next(Phase.IDLE, Input.SEND))
    assertEquals(Step(Phase.TAP, Effect.NONE), VoiceFlow.next(Phase.TAP, Input.SEND))
  }

  /** کارهای صفحه‌خوان: بیکار/آماده ⇐ «شروعِ ضبط»؛ در ضبط ⇐ «پایان و فرستادن» و «لغو» */
  @Test
  fun screenReaderActionsFollowPhase() {
    assertEquals(listOf(Action.START), VoiceFlow.actions(Phase.IDLE))
    assertEquals(listOf(Action.START), VoiceFlow.actions(Phase.READY))
    assertEquals(listOf(Action.FINISH, Action.CANCEL), VoiceFlow.actions(Phase.TAP))
    assertEquals(listOf(Action.FINISH, Action.CANCEL), VoiceFlow.actions(Phase.HOLD))
    //  هر کارِ پیشنهادی در همان حالت واقعاً کاری می‌کند (کارِ بی‌اثر نه)
    for (p in Phase.entries) for (a in VoiceFlow.actions(p)) {
      assertTrue(VoiceFlow.next(p, VoiceFlow.input(a)).effect != Effect.NONE, "$p/$a")
    }
  }

  /** هیچ گذری میکروفونِ روشن را رها نمی‌کند و هیچ ضبطی بی `START` آغاز نمی‌شود */
  @Test
  fun recorderIsNeverLeftRunning() {
    for (p in Phase.entries) for (i in Input.entries) {
      val s = VoiceFlow.next(p, i)
      if (p.recording && !s.phase.recording) assertTrue(s.effect in setOf(Effect.STOP, Effect.STOP_SEND, Effect.CANCEL), "$p/$i ⇒ ${s.effect}")
      if (!p.recording && s.phase.recording) assertEquals(Effect.START, s.effect, "$p/$i")
      if (s.effect == Effect.START) assertTrue(!p.recording && s.phase.recording, "$p/$i")
    }
  }
}
