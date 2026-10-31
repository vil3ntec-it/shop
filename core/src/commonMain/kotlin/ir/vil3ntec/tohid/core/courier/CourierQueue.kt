package ir.vil3ntec.tohid.core.courier

/*
 *  صفِ رویدادهای پیک — DLV-10، v3 §۳.۱۱.
 *
 *  ── قراردادِ سرور (`POST /api/courier/events`) ───────────────────────
 *  `{deviceId, events:[{eventId, assignmentId, type, deviceTime, deviceSeq,
 *     from?, reason?, note?, cashCollectedMinor?, cashReason?, code?}]}`
 *  پاسخ برای هر رویداد: applied | duplicate | unchanged | rejected(code).
 *    • eventId یکتا و پایدار است ⇒ فرستادنِ دوباره بی‌خطر است (duplicate).
 *    • deviceSeq ترتیبِ خودِ دستگاه است؛ سرور یک بسته را با آن مرتب می‌کند.
 *      ⛔ هرگز دوباره خرج نمی‌شود — حتی وقتی رویدادی از صف بیرون می‌رود.
 *    • deviceTime فقط ثبت می‌شود؛ مرجعِ تسویه زمانِ سرور است (ساعتِ گوشی
 *      را باور نمی‌کنیم، فقط می‌فرستیم).
 *    • حداکثر ۱۰۰ رویداد در هر بار (`MAX_BATCH` سرور).
 *
 *  ── قاعدهٔ صف ───────────────────────────────────────────────────────
 *    • applied / duplicate / unchanged ⇒ از صف بیرون (کار انجام شده).
 *    • rejected ⇒ از صف بیرون و در «ردشده‌ها» برای دیدنِ پیک — دوباره
 *      فرستادنش همان رد را می‌گیرد؛ پنهان کردنش دروغ است.
 *    • بی پاسخ (شبکه، ۵xx، ۴۰۱، ۴۰۳، ۴۰۴) ⇒ در صف می‌ماند؛ هیچ‌چیز گم نمی‌شود.
 *
 *  منطقِ خالص، بی android.* و بی JSON: اندروید و کامپیوتر همین را می‌گیرند
 *  و آزمونش (`CourierQueueTest`) بی دستگاه اجرا می‌شود.
 */

/** همان ماشینِ `server/src/lib/delivery-states.js` — فقط آن‌چه پیک می‌بیند. */
object CourierFlow {

  val STATUSES: List<String> = listOf(
    "requested", "assigned", "picked_up", "moving", "arrived", "failed",
    "delivered", "rejected", "withdrawn", "returned",
  )

  private val TRANSITIONS: Map<String, Set<String>> = mapOf(
    "requested" to setOf("assigned", "rejected", "withdrawn"),
    "assigned" to setOf("picked_up", "withdrawn", "delivered"),
    "picked_up" to setOf("moving", "arrived", "delivered", "failed", "returned"),
    "moving" to setOf("arrived", "delivered", "failed", "returned"),
    "arrived" to setOf("delivered", "failed", "returned"),
    "failed" to setOf("moving", "returned"),
    "delivered" to emptySet(),
    "rejected" to emptySet(),
    "withdrawn" to emptySet(),
    "returned" to emptySet(),
  )

  /** کارهایی که پیک خودش ثبت می‌کند («برگشت» و «پس‌گرفتن» با دکان است) */
  val COURIER_TARGETS: Set<String> = setOf("assigned", "rejected", "picked_up", "moving", "arrived", "delivered", "failed")

  val LIVE: Set<String> = setOf("requested", "assigned", "picked_up", "moving", "arrived", "failed")

  /** دلیل‌های سرور (`REASONS`) */
  val REJECT_REASONS: List<String> = listOf("busy", "too_far", "cash_limit", "other")
  val FAIL_REASONS: List<String> = listOf("no_answer", "wrong_address", "refused", "recalled", "other")
  val CASH_REASONS: List<String> = listOf("partial_payment", "refused_amount", "damaged_or_short", "other")

  fun isLive(status: String): Boolean = status in LIVE
  fun isTerminal(status: String): Boolean = status in STATUSES && status !in LIVE

  /**
   *  گذارهای جدول که فقط برای «دریافتِ حضوری» است، نه پیک: بی «گرفتنِ بسته»
   *  تحویل نیست (`check()`ِ سرور: invalid_transition). پیک فقط در روشِ
   *  shop_courier کار دارد، پس این‌ها هرگز از پیک نیستند.
   */
  private val PICKUP_ONLY: Set<Pair<String, String>> = setOf("assigned" to "delivered")

  fun canSend(from: String, to: String): Boolean =
    to in COURIER_TARGETS && to in (TRANSITIONS[from] ?: emptySet()) && (from to to) !in PICKUP_ONLY

  /**
   *  دکمه‌های کارتِ پیک برای هر وضعیت، به ترتیبِ نمایش. «تحویل شد» فقط پس
   *  از «رسیدم» پیش‌نهاد می‌شود (کد/نقد/عکس جلوی مشتری)، هرچند ماشینِ سرور
   *  میان‌بر را هم می‌پذیرد.
   */
  fun actions(status: String): List<String> = when (status) {
    "requested" -> listOf("assigned", "rejected")
    "assigned" -> listOf("picked_up")
    "picked_up" -> listOf("moving", "arrived", "failed")
    "moving" -> listOf("arrived", "failed")
    "arrived" -> listOf("delivered", "failed")
    "failed" -> listOf("moving")
    else -> emptyList()
  }

  fun reasonsFor(type: String): List<String> = when (type) {
    "rejected" -> REJECT_REASONS
    "failed" -> FAIL_REASONS
    else -> emptyList()
  }
}

/** یک رویدادِ صف — درست همان کلیدهای بدنهٔ سرور. */
data class CourierEvent(
  val eventId: String,
  val assignmentId: String,
  val type: String,
  val deviceTime: Long,
  val deviceSeq: Long,
  val from: String = "",
  val reason: String = "",
  val note: String = "",
  val cashCollectedMinor: Long? = null,
  val cashReason: String = "",
  val code: String = "",
)

/** پاسخِ سرور برای یک رویداد */
data class EventResult(
  val eventId: String,
  val status: String,
  val code: String = "",
  val message: String = "",
)

/** رویدادی که سرور رد کرد — برای دیدنِ پیک، تا خودش پاکش کند */
data class RejectedEvent(
  val event: CourierEvent,
  val code: String,
  val message: String,
)

data class QueueState(
  /** ترتیبِ بعدیِ دستگاه — فقط بالا می‌رود */
  val nextSeq: Long = 1,
  val pending: List<CourierEvent> = emptyList(),
  val rejected: List<RejectedEvent> = emptyList(),
)

/** ورودیِ نادرست — پیش از رفتن به صف رد می‌شود، نه بعد از رسیدن به سرور */
class CourierInputError(val code: String, message: String) : IllegalArgumentException(message)

object CourierQueue {

  /** همان `KEY_RE` سرور */
  private val EVENT_ID = Regex("^[A-Za-z0-9_.:-]{8,128}$")
  private val REASON = Regex("^[a-z_]{1,40}$")
  const val MAX_BATCH = 100
  const val MAX_NOTE = 300
  /** همان سقفِ سرور (یک میلیارد افغانی به واحدِ کوچک) */
  const val MAX_MONEY_MINOR = 100_000_000_000L
  /** صف بی‌پایان رشد نمی‌کند؛ پیکی که روزها آفلاین است هم بیش از این ندارد */
  const val MAX_PENDING = 2_000
  const val MAX_REJECTED = 50

  /**
   *  رویدادِ تازه با ترتیبِ بعدیِ دستگاه. `from` وضعیتی است که پیک روی کارت
   *  می‌دید (CASِ اختیاری §۲۸.۵): اگر در سرور عوض شده باشد رد می‌شود، نه
   *  این‌که روی وضعیتِ دیگری بنشیند.
   */
  fun enqueue(
    state: QueueState,
    eventId: String,
    assignmentId: String,
    type: String,
    deviceTime: Long,
    from: String = "",
    reason: String = "",
    note: String = "",
    cashCollectedMinor: Long? = null,
    cashReason: String = "",
    code: String = "",
  ): Pair<QueueState, CourierEvent> {
    if (!EVENT_ID.matches(eventId)) throw CourierInputError("bad_event_id", "شناسهٔ رویداد نامعتبر است")
    if (assignmentId.isBlank()) throw CourierInputError("assignment_not_found", "مأموریت پیدا نشد")
    if (type !in CourierFlow.COURIER_TARGETS) throw CourierInputError("bad_event_type", "این کار با پیک نیست")
    if (from.isNotEmpty() && !CourierFlow.canSend(from, type)) {
      throw CourierInputError("invalid_transition", "این تغییرِ وضعیت مجاز نیست")
    }
    if (state.pending.size >= MAX_PENDING) throw CourierInputError("queue_full", "صفِ رویدادها پر است؛ اول به اینترنت وصل شوید")
    val cleanReason = reason.trim()
    val cleanNote = note.trim().take(MAX_NOTE)
    if (type in setOf("rejected", "failed")) {
      if (cleanReason.isEmpty()) throw CourierInputError("reason_required", "دلیل لازم است")
      if (cleanReason !in CourierFlow.reasonsFor(type)) throw CourierInputError("bad_reason", "دلیل معتبر نیست")
      if (cleanReason == "other" && cleanNote.isEmpty()) throw CourierInputError("note_required", "توضیح لازم است")
    } else if (cleanReason.isNotEmpty() && !REASON.matches(cleanReason)) {
      throw CourierInputError("bad_reason", "دلیل معتبر نیست")
    }
    if (cashCollectedMinor != null && (cashCollectedMinor < 0 || cashCollectedMinor > MAX_MONEY_MINOR)) {
      throw CourierInputError("bad_cash", "مبلغِ گرفته‌شده معتبر نیست")
    }
    val cleanCashReason = cashReason.trim()
    if (cleanCashReason.isNotEmpty() && cleanCashReason !in CourierFlow.CASH_REASONS) {
      throw CourierInputError("bad_reason", "دلیلِ اختلافِ نقد معتبر نیست")
    }
    if (state.pending.any { it.eventId == eventId }) throw CourierInputError("event_key_reused", "این شناسه در صف هست")
    val ev = CourierEvent(
      eventId = eventId,
      assignmentId = assignmentId,
      type = type,
      deviceTime = deviceTime.coerceAtLeast(0),
      deviceSeq = state.nextSeq,
      from = from,
      reason = cleanReason,
      note = cleanNote,
      cashCollectedMinor = if (type == "delivered") cashCollectedMinor else null,
      cashReason = if (type == "delivered") cleanCashReason else "",
      code = if (type == "delivered") latinDigits(code).take(12) else "",
    )
    return state.copy(nextSeq = state.nextSeq + 1, pending = state.pending + ev) to ev
  }

  /** کدِ تحویل با رقم‌های فارسی/عربی هم زده می‌شود؛ سرور رقمِ لاتین می‌خواهد */
  fun latinDigits(raw: String): String = buildString {
    for (ch in raw) when (ch) {
      in '0'..'9' -> append(ch)
      in '۰'..'۹' -> append('0' + (ch - '۰'))
      in '٠'..'٩' -> append('0' + (ch - '٠'))
    }
  }

  /** بستهٔ بعدی برای فرستادن — به ترتیبِ دستگاه، حداکثر ۱۰۰ */
  fun batch(state: QueueState, max: Int = MAX_BATCH): List<CourierEvent> =
    state.pending.sortedBy { it.deviceSeq }.take(max.coerceIn(1, MAX_BATCH))

  /**
   *  پاسخِ سرور را روی صف می‌نشاند. پاسخ با eventId جفت می‌شود (و اگر سرور
   *  شناسه را پس نداد، با جایِ رویداد در همان بسته). رویدادِ بی‌پاسخ می‌ماند.
   */
  fun applyResults(state: QueueState, sent: List<CourierEvent>, results: List<EventResult>): QueueState {
    val byId = results.filter { it.eventId.isNotEmpty() }.associateBy { it.eventId }
    val done = HashSet<String>()
    val newlyRejected = ArrayList<RejectedEvent>()
    sent.forEachIndexed { i, ev ->
      val r = byId[ev.eventId] ?: results.getOrNull(i)?.takeIf { it.eventId.isEmpty() || it.eventId == ev.eventId }
        ?: return@forEachIndexed
      when (r.status) {
        "applied", "duplicate", "unchanged" -> done += ev.eventId
        "rejected" -> {
          done += ev.eventId
          newlyRejected += RejectedEvent(ev.copy(code = ""), r.code.ifBlank { "rejected" }, r.message)
        }
      }
    }
    if (done.isEmpty()) return state
    return state.copy(
      pending = state.pending.filterNot { it.eventId in done },
      rejected = (state.rejected + newlyRejected).takeLast(MAX_REJECTED),
    )
  }

  fun dismissRejected(state: QueueState, eventId: String): QueueState =
    state.copy(rejected = state.rejected.filterNot { it.event.eventId == eventId })

  fun pendingFor(state: QueueState, assignmentId: String): List<CourierEvent> =
    state.pending.filter { it.assignmentId == assignmentId }.sortedBy { it.deviceSeq }

  /**
   *  وضعیتی که پیک می‌بیند: وضعیتِ سرور + رویدادهای هنوز نرفته (خوش‌بینانه،
   *  فقط گذارهای مجاز). کارت با همین جلو می‌رود، حتی آفلاین.
   */
  fun projectedStatus(serverStatus: String, assignmentId: String, state: QueueState): String {
    var s = serverStatus
    for (ev in pendingFor(state, assignmentId)) if (CourierFlow.canSend(s, ev.type)) s = ev.type
    return s
  }
}

/** کارتِ مأموریت — همان `courierView`ِ سرور. */
data class MissionCard(
  val id: String,
  val orderNo: Long,
  val status: String,
  val landmark: String = "",
  val addressText: String = "",
  val lat: Double? = null,
  val lng: Double? = null,
  val customerName: String = "",
  val phoneMasked: String = "",
  val callBeforeArrival: Boolean = false,
  val leaveWith: String = "",
  val itemsCount: Int = 0,
  val paymentMethod: String = "",
  val expectedCollectMinor: Long = 0,
  val cashCollectedMinor: Long? = null,
  val fareMinor: Long = 0,
  val requiresCode: Boolean = false,
  val photoAllowed: Boolean = false,
  val recallRequested: Boolean = false,
  val updatedAt: Long = 0,
  /** ناحیه (DLV-02، کارتِ «اول نشانه») */
  val area: String = "",
  /** رنگِ دروازه (DLV-02) */
  val gateColor: String = "",
  /** دکان پس از ناموفق نشانی را اصلاح کرد (DLV-09، زمانِ سرور) */
  val addressCorrectedAt: Long? = null,
)

object MissionView {

  /**
   *  کارت برای نمایش: وضعیتِ پیش‌بینی‌شده با صف، و پس از پایانِ مأموریت
   *  (حتی اگر پایان هنوز در صف باشد) نشانی، پین و مشتری پاک — همان قاعدهٔ
   *  سرور که نشانی را پس از پایان نمی‌دهد. شماره همیشه پوشیده.
   */
  fun forDisplay(card: MissionCard, state: QueueState): MissionCard {
    val status = CourierQueue.projectedStatus(card.status, card.id, state)
    val shown = card.copy(status = status, phoneMasked = maskPhone(card.phoneMasked))
    return if (CourierFlow.isLive(status)) shown else redact(shown)
  }

  /** نشانی و مشتری بیرون — برای نمایش و برای آن‌چه روی گوشی نگه داشته می‌شود */
  fun redact(card: MissionCard): MissionCard = card.copy(
    landmark = "", addressText = "", lat = null, lng = null,
    customerName = "", phoneMasked = "", leaveWith = "", callBeforeArrival = false,
    area = "", gateColor = "", addressCorrectedAt = null,
  )

  /**
   *  کارتِ «اول نشانه» (DLV-02، §۶.۱۸): خطِ اول نشانه (بی نشانه، نشانیِ متنی)،
   *  خطِ دوم «ناحیه · دروازهٔ رنگ»، خطِ سوم نشانیِ متنی وقتی نشانه هم هست.
   *  خطِ خالی نمی‌آید؛ پیک پیش از هر چیز چیزی را می‌بیند که از سرِ کوچه پیداست.
   */
  fun landmarkFirst(card: MissionCard): List<String> {
    val landmark = card.landmark.trim()
    val text = card.addressText.trim()
    val hint = listOf(
      card.area.trim().takeIf { it.isNotEmpty() }?.let { "ناحیه: $it" },
      card.gateColor.trim().takeIf { it.isNotEmpty() }?.let { "دروازهٔ $it" },
    ).filterNotNull().joinToString(" · ")
    return listOf(landmark.ifEmpty { text }, hint, if (landmark.isNotEmpty()) text else "").filter { it.isNotEmpty() }
  }

  /**
   *  سپرِ دوم: سرور شمارهٔ پوشیده می‌دهد (`orders.maskPhone`: چهار نویسهٔ اول،
   *  نقطه‌ها، سه نویسهٔ آخر). اگر روزی شمارهٔ بی‌پوشش رسید، همین‌جا با همان
   *  قالب پوشیده می‌شود و هرگز کامل روی صفحه یا گوشی نمی‌ماند.
   */
  fun maskPhone(raw: String): String {
    val s = raw.trim()
    if (s.isEmpty() || s.contains('•') || s.contains('*')) return s
    if (s.count { it.isDigit() } < 4) return s
    if (s.length <= 7) return "•".repeat(s.length)
    return s.take(4) + "•".repeat(s.length - 7) + s.takeLast(3)
  }

  /** کارت‌هایی که روی گوشی می‌مانند: پایان‌یافته‌ها بی نشانی */
  fun forStorage(cards: List<MissionCard>): List<MissionCard> =
    cards.map { if (CourierFlow.isLive(it.status)) it.copy(phoneMasked = maskPhone(it.phoneMasked)) else redact(it) }
}
