package ir.vil3ntec.tohid.scan

/**
 *  چراغِ خودکار — و مهم‌تر از روشن کردنش، **ندانستهْ روشن نکردنش**.
 *
 *  ── چه چیزی نبود ───────────────────────────────────────────────────
 *  تا امروز چراغ فقط یک دکمه بود. یعنی در دکانِ کم‌نور، فروشنده باید
 *  می‌فهمید که مشکل از نور است، دکمه را پیدا می‌کرد، می‌زد، و بعد از
 *  اسکن یادش می‌ماند خاموشش کند. عملاً یا روشن نمی‌شد یا روشن می‌ماند.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ── چرا این‌قدر محتاط ─────────────────────────────────────────────
 *  چراغ بی‌ضرر نیست. روی بستهٔ پلاستیکیِ براق — که در دکان کم نیست —
 *  نور صاف برمی‌گردد توی لنز، ناحیهٔ بارکد **می‌سوزد** و خط‌ها گم
 *  می‌شوند. یعنی چراغ می‌تواند بارکدی را که بی‌چراغ خوانده می‌شد،
 *  ناخوانا کند.
 *
 *  پس قاعدهٔ اول این است: **اگر بی‌چراغ خوانده می‌شود، چراغ روشن نشود.**
 *  تاریکیِ کلِ صحنه به تنهایی دلیل نیست؛ ممکن است دکان تاریک باشد و
 *  بارکد زیرِ لامپِ پیشخوان نورِ کافی داشته باشد. تصمیم با روشناییِ
 *  **ناحیهٔ بارکد** گرفته می‌شود، نه کلِ کادر.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ── چهار حالت، و راهِ بینشان ───────────────────────────────────────
 *
 *  ```
 *   OFF ──(ناحیه تاریک و تخت، و چند صدم ثانیه است چیزی خوانده نشده)──▶ TRYING
 *   TRYING ──(کنتراست بهتر شد)──▶ ON
 *   TRYING ──(سوخت / کنتراست بدتر شد)──▶ COOLDOWN ──(زمان یا صحنهٔ نو)──▶ OFF
 *   ON ──(بازتاب پیدا شد)──▶ COOLDOWN
 *   هر حالت ──(اسکن موفق / بسته شدن)──▶ OFF
 *  ```
 *
 *  **`TRYING` تازگیِ کار است.** روشن کردنِ چراغ یک **آزمایش** است، نه یک
 *  تصمیم. پیش از روشن کردن، حالِ ناحیه نگه داشته می‌شود (`baseline`)؛
 *  بعد از آنکه نوردهی فرصتِ نشستن پیدا کرد، همان ناحیه دوباره سنجیده و
 *  با پیش از چراغ مقایسه می‌شود. اگر کنتراست بهتر شده، چراغ می‌ماند؛
 *  اگر سوخته یا کنتراست افتاده، همان‌جا خاموش.
 *
 *  **`COOLDOWN` چرخهٔ روشن‑خاموش را می‌بندد.** بی آن، این حلقه پیش
 *  می‌آمد: تاریک → روشن → بازتاب → خاموش → تاریک → روشن… . بعد از
 *  بازتاب، تا وقتی **هم** زمان نگذشته **و هم** صحنه به اندازهٔ معناداری
 *  عوض نشده، چراغ دوباره روشن نمی‌شود. عوض شدنِ صحنه یعنی فروشنده کالای
 *  دیگری آورده — و آن کالا شاید براق نباشد.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ── و دستِ آدم ─────────────────────────────────────────────────────
 *  دکمهٔ چراغ سرِ جایش می‌ماند، ولی دیگر «چراغ» نیست؛ **پا پس کشیدنِ
 *  خودکار** است. هر بار زده شود، مثلِ دو انگشتِ بزرگ‌نمایی، مدتی تصمیمِ
 *  خودکار کنار می‌رود. کسی که می‌داند چه می‌کند نباید با برنامه کشتی
 *  بگیرد.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  چیزی از اندروید اینجا نیست: چراغ یک لامبدا است و ساعت یک عدد. پس
 *  کلِ این تصمیم‌ها روی JVM سنجیده می‌شوند — `LightPilotTest`.
 */
class LightPilot(private val torch: (Boolean) -> Unit) {

  enum class State { OFF, TRYING, ON, COOLDOWN }

  @Volatile var state: State = State.OFF
    private set

  /** آخرین باری که رمزگشایی موفق بود — چراغ به کارِ درست‌کار دست نمی‌زند */
  private var decodedAt = 0L

  /** حالِ ناحیه، درست پیش از روشن کردنِ چراغ */
  private var baseline: Look = Look.NOTHING

  private var triedAt = 0L
  private var coolUntil = 0L

  /** روشناییِ ناحیه در لحظه‌ای که بازتاب دیدیم — معیارِ «صحنه عوض شد» */
  private var coolLuma = 0

  /** تا این لحظه، چراغ دستِ کاربر است */
  private var manualUntil = 0L

  /**
   *  هر فریم، یک بار. هیچ‌چیز را نگه نمی‌دارد و منتظرِ هیچ‌چیز نمی‌ماند —
   *  رمزگشایی در همان لحظه سرِ کارِ خودش است.
   */
  fun onFrame(look: Look, now: Long) {
    if (now < manualUntil) return
    if (!look.usable) return

    when (state) {
      State.OFF -> maybeStart(look, now)
      State.TRYING -> judge(look, now)
      State.ON -> watch(look, now)
      State.COOLDOWN -> maybeArm(look, now)
    }
  }

  /** بارکدی رمزگشایی شد — یعنی نور هرچه هست، کافی است */
  fun sawCode(now: Long = System.currentTimeMillis()) {
    decodedAt = now
  }

  /** کالا رفت توی سبد؛ کارِ چراغ تمام است */
  fun done(now: Long = System.currentTimeMillis()) {
    decodedAt = now
    if (state == State.OFF) return
    off(State.OFF)
  }

  /** دکمهٔ کاربر — و کنار رفتنِ خودکار تا مدتی */
  fun byHand(on: Boolean, now: Long = System.currentTimeMillis()) {
    manualUntil = now + MANUAL_HOLD_MS
    //  مبنای مقایسه پاک می‌شود: چراغی که آدم روشن کرده، مبنایی از
    //  «پیش از چراغ» ندارد. وقتی مهلتِ دستی سر آمد، تنها چیزی که
    //  می‌تواند خاموشش کند سوختنِ آشکارِ ناحیه است، نه مقایسه با
    //  عددی که هرگز گرفته نشد.
    baseline = Look.NOTHING
    state = if (on) State.ON else State.OFF
    torch(on)
  }

  /** آیا همین حالا چراغ روشن است — برای نشانِ روی صفحه */
  val lit: Boolean get() = state == State.TRYING || state == State.ON

  fun close() {
    manualUntil = 0L
    if (state != State.OFF) off(State.OFF) else torch(false)
  }

  // ── تصمیم‌ها ────────────────────────────────────────────────────

  private fun maybeStart(look: Look, now: Long) {
    //  ۱) چیزی که خوانده می‌شود، دست‌نخورده می‌ماند
    if (now - decodedAt < DECODE_FRESH_MS) return
    //  ۲) تصمیم با ناحیهٔ بارکد است، نه کلِ کادر
    if (look.roiLuma > DARK_ROI) return
    //  ۳) تاریکِ **پرکنتراست** هم مشکلی ندارد؛ آنچه رمزگشا را می‌کُشد
    //     تاریکیِ تخت است
    if (look.roiSpread > FLAT_SPREAD) return

    baseline = look
    triedAt = now
    state = State.TRYING
    torch(true)
  }

  private fun judge(look: Look, now: Long) {
    //  به نوردهی فرصت بده بنشیند، وگرنه فریمِ نیم‌سوختهٔ گذار را
    //  «بازتاب» می‌خوانیم
    if (now - triedAt < SETTLE_MS) return

    if (burnt(look)) {
      reflect(look, now)
      return
    }
    //  چراغ کارش را کرد: یا کنتراست بهتر شد یا ناحیه از تاریکی درآمد
    val better = look.roiSpread > baseline.roiSpread + SPREAD_GAIN ||
      look.roiLuma > baseline.roiLuma + LUMA_GAIN
    if (better) {
      state = State.ON
      return
    }
    //  نه بهتر شد نه سوخت — پس بی‌فایده بود؛ باتری نخوریم
    reflect(look, now)
  }

  private fun watch(look: Look, now: Long) {
    //  بازتاب همیشه در همان لحظهٔ اول پیدا نمی‌شود: کالا که کمی
    //  می‌چرخد، نور صاف برمی‌گردد
    if (burnt(look)) reflect(look, now)
  }

  private fun maybeArm(look: Look, now: Long) {
    if (now < coolUntil) return
    //  زمان که گذشت هم، تا صحنه به اندازهٔ معناداری عوض نشده دوباره
    //  همان تصمیم گرفته نمی‌شود — همان چرخه‌ای که ممنوع است
    if (kotlin.math.abs(look.roiLuma - coolLuma) < SCENE_CHANGE) return
    state = State.OFF
  }

  /** سوختن یا مردنِ کنتراست — هر دو یعنی چراغ دارد بد می‌کند */
  private fun burnt(look: Look): Boolean =
    look.roiClipped > baseline.roiClipped + CLIP_JUMP ||
      look.roiClipped > CLIP_HARD ||
      look.roiSpread < baseline.roiSpread - SPREAD_LOSS

  private fun reflect(look: Look, now: Long) {
    /*
     *  معیارِ «صحنه عوض شد» باید روشناییِ **پیش از چراغ** باشد، نه آنچه
     *  زیرِ چراغِ سوزان دیدیم.
     *
     *  اولین بار همین را اشتباه نوشتم و سنجه‌اش گرفت: با معیارِ ۲۳۰
     *  (ناحیهٔ سوخته)، لحظه‌ای که چراغ خاموش می‌شد صحنه برمی‌گشت به همان
     *  ۳۰ و برنامه می‌گفت «عوض شد» و دوباره روشن می‌کرد. یعنی دقیقاً
     *  همان حلقه‌ای که قرار بود بسته شود.
     */
    coolLuma = if (baseline.usable) baseline.roiLuma else look.roiLuma
    coolUntil = now + COOLDOWN_MS
    off(State.COOLDOWN)
  }

  private fun off(to: State) {
    state = to
    torch(false)
  }

  private companion object {
    /** از این تاریک‌تر، ناحیهٔ بارکد کم‌نور است (۰ تا ۲۵۵) */
    const val DARK_ROI = 62

    /** کنتراستِ کمتر از این یعنی خط‌های بارکد از هم جدا نیستند */
    const val FLAT_SPREAD = 45

    /** تا این‌قدر پس از آخرین رمزگشایی، چراغ کاری ندارد */
    const val DECODE_FRESH_MS = 900L

    /** فرصتِ نشستنِ نوردهی پس از روشن شدنِ چراغ */
    const val SETTLE_MS = 420L

    /** برای «بهتر شد» چه‌قدر بهتر */
    const val SPREAD_GAIN = 8
    const val LUMA_GAIN = 22

    /** برای «بدتر شد» چه‌قدر بدتر */
    const val SPREAD_LOSS = 10

    /** جهشِ سوختگی نسبت به پیش از چراغ، و سقفِ مطلقش */
    const val CLIP_JUMP = 0.10f
    const val CLIP_HARD = 0.22f

    /** پس از بازتاب، این‌قدر خبری از چراغ نیست */
    const val COOLDOWN_MS = 7_000L

    /** «صحنه عوض شد» یعنی روشناییِ ناحیه این‌قدر فرق کرده باشد */
    const val SCENE_CHANGE = 30

    /** پس از دستِ کاربر، این‌قدر خودکار کنار می‌رود */
    const val MANUAL_HOLD_MS = 20_000L
  }
}
