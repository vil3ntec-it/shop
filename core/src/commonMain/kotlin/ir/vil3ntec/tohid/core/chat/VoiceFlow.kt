package ir.vil3ntec.tohid.core.chat

/**
 *  ماشینِ حالتِ ضبطِ پیامِ صوتیِ گفت‌وگو (دسترس‌پذیری، WCAG 2.1 AA ‏2.1.1 و 2.5.1) — منطقِ خالص، بی Compose و بی
 *  اندروید، برای اندروید، کامپیوتر و آیفون.
 *
 *  دو راهِ ضبط، یک ماشین:
 *   • **نگه‌داشتن** (همان رفتارِ پیشین برای چشم): فشار ⇐ ضبط، رها کردن ⇐ پایان و ردیفِ «فرستادن/لغو» پیش از فرستادن؛
 *   • **زدن** (صفحه‌خوان، کلید یا فرمانِ صوتی): «شروعِ ضبط» ⇐ ضبط تا «پایانِ ضبط و فرستادن» یا «لغو». رها کردن یا
 *     فشارِ دوباره ضبطِ زدنی را نمی‌بندد.
 *  رسیدن به سقف (۵ دقیقه) هر دو را مثلِ رها کردن می‌بندد. هر گذر می‌گوید با ضبط‌کننده چه باید کرد (`Effect`) و
 *  صفحه‌خوان چه بشنود (`Say`)؛ ناحیهٔ زنده فقط با همین عوض می‌شود، نه با تیکِ زمان. بیرون رفتن از ضبط همیشه
 *  ضبط‌کننده را می‌بندد یا لغو می‌کند (میکروفونِ روشنِ رهاشده نه).
 */
object VoiceFlow {

  enum class Phase {
    IDLE,

    /** ضبط با نگه‌داشتن؛ رها کردن پایانش است */
    HOLD,

    /** ضبط با زدن یا کارِ صفحه‌خوان؛ تا «پایان» یا «لغو» */
    TAP,

    /** ضبط ایستاده و پیش از فرستادن دیده می‌شود (مدت، حجم، «فرستادن/لغو») */
    READY,
    ;

    val recording: Boolean get() = this == HOLD || this == TAP
  }

  enum class Input { PRESS, RELEASE, START, FINISH, SEND, CANCEL, LIMIT }

  /** کارِ ضبط‌کننده: `STOP_SEND` = ایستادن و، اگر جا شد، فرستادن (`settle`) */
  enum class Effect { NONE, START, STOP, STOP_SEND, SEND, CANCEL, DISCARD }

  /** آنچه ناحیهٔ زندهٔ صفحه‌خوان می‌گوید */
  enum class Say { RECORDING, READY, SENT, CANCELLED, TOO_BIG, EMPTY }

  /** کارهای صفحه‌خوان روی دکمهٔ صدا؛ نخستین همان «دوبار بزنید» است */
  enum class Action { START, FINISH, CANCEL }

  data class Step(val phase: Phase, val effect: Effect, val say: Say? = null)

  fun next(p: Phase, i: Input): Step = when (i) {
    Input.PRESS -> if (p.recording) Step(p, Effect.NONE) else Step(Phase.HOLD, Effect.START, Say.RECORDING)
    Input.START -> if (p.recording) Step(p, Effect.NONE) else Step(Phase.TAP, Effect.START, Say.RECORDING)
    Input.RELEASE -> if (p == Phase.HOLD) Step(Phase.READY, Effect.STOP) else Step(p, Effect.NONE)
    Input.LIMIT -> if (p.recording) Step(Phase.READY, Effect.STOP) else Step(p, Effect.NONE)
    Input.FINISH -> if (p.recording) Step(Phase.READY, Effect.STOP_SEND) else Step(p, Effect.NONE)
    Input.SEND -> if (p == Phase.READY) Step(Phase.IDLE, Effect.SEND, Say.SENT) else Step(p, Effect.NONE)
    Input.CANCEL -> when (p) {
      Phase.HOLD, Phase.TAP -> Step(Phase.IDLE, Effect.CANCEL, Say.CANCELLED)
      Phase.READY -> Step(Phase.IDLE, Effect.DISCARD, Say.CANCELLED)
      Phase.IDLE -> Step(p, Effect.NONE)
    }
  }

  /**
   *  پس از `STOP`/`STOP_SEND`: `size` حجمِ ضبط‌شده (`null` = چیزی ضبط نشد). «پایان و فرستادن» فقط وقتی می‌فرستد که
   *  نوشتن باز است و از سقف بزرگ‌تر نیست؛ وگرنه همان ردیفِ پیش از فرستادن، با دلیلش برای صفحه‌خوان.
   */
  fun settle(send: Boolean, size: Long?, limit: Long, canWrite: Boolean): Step = when {
    size == null -> Step(Phase.IDLE, Effect.NONE, Say.EMPTY)
    send && tooBig(size, limit) -> Step(Phase.READY, Effect.NONE, Say.TOO_BIG)
    send && canWrite -> Step(Phase.IDLE, Effect.SEND, Say.SENT)
    else -> Step(Phase.READY, Effect.NONE, Say.READY)
  }

  /** سقفِ صفر یعنی سقفی گفته نشده */
  fun tooBig(size: Long, limit: Long): Boolean = limit > 0 && size > limit

  fun actions(p: Phase): List<Action> = if (p.recording) listOf(Action.FINISH, Action.CANCEL) else listOf(Action.START)

  fun input(a: Action): Input = when (a) {
    Action.START -> Input.START
    Action.FINISH -> Input.FINISH
    Action.CANCEL -> Input.CANCEL
  }
}
