package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.i18n.StrEvidence
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.i18n.StrOrderFeedback
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.orders.OrderEvidence
import ir.vil3ntec.tohid.data.repo.OrderEvidenceSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 *  حالِ «پروندهٔ مدرک» (RSK-08) — یک حالت برای هر دو رابط (`OrderEvidencePanel` ِ رابطِ امروز و `OrderEvidenceV3`):
 *  هر دو فقط می‌کشند و بار و نوشتنِ دوم ندارند. منطقِ خالص در `core/orders/OrderEvidence`؛ هر تصمیم با سرور.
 */

/**
 *  «دیده شود؟» ِ یک درِ پشتِ پرچمِ سرور، از پرسشِ بی‌اثر: تا پاسخِ روشن پنهان؛ «نه» ِ سرور (۴۰۳/۴۰۴) پنهان؛
 *  خطای گذرا همان حالِ پیشین. هر [ttlMs] یک بار از نو پرسیده می‌شود (پرچم ممکن است روشن یا خاموش شود).
 */
@Stable
class ProbeGate(private val ttlMs: Long) {
  var visible by mutableStateOf(false)
    private set
  private var at = 0L

  fun due(now: Long): Boolean = at == 0L || now - at >= ttlMs

  /** [says] null ⇒ نامعلوم (همان حالِ پیشین، و پرسشِ بعدی زود) */
  fun record(says: Boolean?, now: Long) {
    if (says == null) return
    visible = says
    at = now
  }

  /** سرور میانِ کار «نه» گفت (پرچم خاموش شد، اجازه رفت) */
  fun hide(now: Long) = record(false, now)

  fun reset() { visible = false; at = 0L }
}

/** «سرور گفت نه» (پرچم، اجازه یا درِ ناموجود) — نه خطای گذرای شبکه */
internal fun serverRefused(f: ApiFailure): Boolean = f is ApiFailure.Forbidden || f is ApiFailure.NotFound

/** دکمهٔ «پروندهٔ مدرک»: فقط پس از پاسخِ روشنِ سرور به پرسشِ بی‌اثر (`OrderEvidence.PROBE_ID`) */
object OrderEvidenceLive {
  const val TTL_MS = 10 * 60_000L
  val gate = ProbeGate(TTL_MS)

  suspend fun ensure(src: OrderEvidenceSource, now: Long = platformNow()) {
    if (!gate.due(now)) return
    src.probe()
      .onSuccess { gate.record(false, now) }   // درِ کهنه‌ای که «probe» را سفارش گرفت — پنهان
      .onFailure { f -> gate.record(OrderEvidence.probeSays(f.code, serverRefused(f)), now) }
  }
}

internal fun evidenceKind(kind: String): String = when (kind) {
  "not_delivered" -> StrEvidence.kNotDelivered
  "missing_item" -> StrEvidence.kMissing
  "wrong_item" -> StrEvidence.kWrong
  "damaged" -> StrEvidence.kDamaged
  "cash" -> StrEvidence.kCash
  "late" -> StrEvidence.kLate
  else -> StrEvidence.kOther
}

internal fun evidenceOutcome(outcome: String): String = when (outcome) {
  "customer_favor" -> StrEvidence.oCustomer
  "shop_favor" -> StrEvidence.oShop
  "withdrawn" -> StrEvidence.oWithdrawn
  else -> StrEvidence.oSettled
}

/** خطِ سرِ یک اختلاف و خط‌های زیرش (باز شد، یادداشت‌ها، نتیجه) — هر دو رابط و چاپ */
internal fun disputeLines(f: OrderEvidence.File, d: OrderEvidence.Dispute, stamp: (Long) -> String): Pair<String, List<String>> {
  val head = StrEvidence.head(evidenceKind(d.kind), if (d.open) StrEvidence.stOpen else StrEvidence.stResolved)
  val lines = buildList {
    add(StrEvidence.openedBy(f.who(d.openedBy.userId), stamp(d.openedAt)))
    add(d.reason)
    //  §۱۹.۹ بندِ ۵: مسئول و مهلت (آخرین سپردن)؛ «مهلت گذشته» فقط برای اختلافِ باز
    d.assigneeId?.let { who ->
      add(StrEvidence.ownerLine(f.who(who), d.dueAt?.let(stamp) ?: "—") + if (d.overdue) " · ${StrEvidence.overdue}" else "")
    }
    d.notes.forEach { n -> add(StrEvidence.noteLine(f.who(n.by.userId), stamp(n.at), n.text)) }
    d.resolution?.let { r -> add(StrEvidence.resolution(evidenceOutcome(r.outcome), f.who(r.by.userId), r.text)) }
  }
  return head to lines
}

/**
 *  بخش‌های فقط‌خواندنیِ پرونده: (سرخط، خط‌ها) — همان چیزی که صفحه نشان می‌دهد و چاپ می‌شود.
 *  [money] مبلغِ «کوچک‌ترین واحد» ⇒ متن.
 */
internal fun evidenceSections(f: OrderEvidence.File, stamp: (Long) -> String, money: (Long) -> String): List<Pair<String, List<String>>> {
  val o = f.order
  val dash = "—"
  val out = ArrayList<Pair<String, List<String>>>()
  out += StrEvidence.sOrder to buildList {
    add(StrEvidence.orderLine(StrOnline.orderStatus(o.status), money(o.totalMinor), o.itemsCount.toString()))
    add(StrEvidence.customer(o.customerName.ifBlank { dash }, o.phoneMasked.ifBlank { dash }))
    if (o.area.isNotBlank() || o.landmark.isNotBlank()) add(StrEvidence.address(o.area.ifBlank { dash }, o.landmark.ifBlank { dash }))
    add(if (o.hasPin) StrEvidence.pinYes else StrEvidence.pinNo)
    if (o.isTest) add(StrEvidence.testOrder)
  }
  out += StrEvidence.sTimeline(f.timeline.size) to f.timeline.map { s ->
    val what = when {
      s.kind == "created" -> StrEvidence.created
      s.toStatus.isNotBlank() -> StrOnline.orderStatus(s.toStatus)
      s.kind == "note" -> StrEvidence.noteEvent
      else -> s.kind
    }
    val who = if (s.actorType == "user" && s.actorId.isNotBlank()) " · " + StrEvidence.by(f.who(s.actorId)) else ""
    StrEvidence.step(stamp(s.at), what) + who
  }
  out += StrEvidence.sDelivery(f.assignments.size) to buildList {
    if (f.assignments.isEmpty()) add(StrEvidence.noDelivery)
    for (a in f.assignments) {
      add(StrEvidence.attempt(a.attemptNo.toString(), f.courier(a), StrOnline.missionStatus(a.status)))
      if (a.expectedCollectMinor != null || a.cashCollectedMinor != null) {
        add(StrEvidence.cash(a.expectedCollectMinor?.let(money) ?: dash, a.cashCollectedMinor?.let(money) ?: dash))
      }
      add(if (a.codeVerified) StrEvidence.confCode else if (a.manualConfirm) StrEvidence.confManual else StrEvidence.confNone)
      add(a.photos?.let { StrEvidence.photos(it) } ?: StrEvidence.photosHidden)
      add(StrEvidence.courierEvents(a.events))
    }
    if (f.assignments.isNotEmpty()) add(StrEvidence.noSignature)
  }
  out += StrEvidence.sLocation to buildList {
    if (f.distances.isEmpty()) add(StrEvidence.noDistance)
    f.distances.forEach { d -> add(StrEvidence.distance(d.meters.toString(), stamp(d.at))) }
    if (f.locationNote.isNotBlank()) add(f.locationNote)
  }
  if (f.clock.samples > 0) {
    out += StrEvidence.sClock to listOfNotNull(
      StrEvidence.clock(f.clock.samples.toString(), OrderEvidence.skewMinutes(f.clock).toString(), f.clock.warnings.toString()),
      f.clock.note.takeIf { it.isNotBlank() },
    )
  }
  out += StrEvidence.sScans to buildList {
    val s = f.scans
    val status = s.pickStatus
    if (status == null) add(StrEvidence.noPick)
    else add(StrEvidence.pick(StrOnline.orderStatus(status), s.bags?.toString() ?: dash, s.shelfSlot.ifBlank { dash }))
    if (s.pickEvents > 0) add(StrEvidence.pickEvents(s.pickEvents.toString(), s.notFound.toString()))
    if (s.shelfScans > 0) add(StrEvidence.shelfScans(s.shelfScans))
    if (s.bagsAtPickup.isNotEmpty()) add(StrEvidence.bagsAtPickup(s.bagsAtPickup.joinToString("، ")))
    add(StrEvidence.pickPhotos(f.pickPhotos))
    f.photoConsent?.let { add(if (it) StrEvidence.consentYes else StrEvidence.consentNo) }
  }
  out += StrEvidence.sIssues(f.issues.size) to f.issues.map { i ->
    StrEvidence.issue(feedbackKind(i.kind), if (i.status == "resolved") StrOrderFeedback.stResolved else StrOrderFeedback.stOpen, i.photos.toString())
  }
  out += StrEvidence.sContacts to listOfNotNull(
    StrEvidence.contacts(
      f.contacts.courierCalls.toString(), f.contacts.conversations.toString(),
      f.contacts.fromCustomer.toString(), f.contacts.fromStaff.toString(),
    ),
    f.contacts.note.takeIf { it.isNotBlank() },
  )
  if (f.truncated.isNotEmpty()) out += StrEvidence.readOnlyNote to listOf(StrEvidence.truncated(f.truncated.joinToString("، ")))
  return out
}

/** جدولِ چاپ (سرخطِ نخست + ردیف‌ها): بخش و شرح — همان بخش‌های صفحه و همهٔ اختلاف‌ها */
internal fun evidencePrintRows(f: OrderEvidence.File, stamp: (Long) -> String, money: (Long) -> String): List<List<String>> = buildList {
  add(listOf(StrEvidence.colSection, StrEvidence.colDetail))
  add(listOf(StrEvidence.title(f.order.orderNo.toString()), StrEvidence.generated(stamp(f.generatedAt))))
  for (d in f.disputes) {
    val (head, lines) = disputeLines(f, d, stamp)
    lines.forEachIndexed { i, l -> add(listOf(if (i == 0) StrEvidence.sDisputes + " — " + head else "", l)) }
  }
  if (f.disputes.isEmpty()) add(listOf(StrEvidence.sDisputes, StrEvidence.noDispute))
  for ((title, lines) in evidenceSections(f, stamp, money)) {
    lines.forEachIndexed { i, l -> add(listOf(if (i == 0) title else "", l)) }
  }
}

/**
 *  حالِ برگهٔ پروندهٔ یک سفارش. هر نوشتن یک کلیدِ یکتا دارد که تا پاسخِ موفق برای همان فرم می‌ماند
 *  (`OrderEvidence.pending`) — «ثبت» ِ دوباره پس از قطعِ نت همان ردیف است، نه ردیفِ دوم.
 */
@Stable
internal class OrderEvidenceController(
  private val src: OrderEvidenceSource,
  val orderId: String,
  private val scope: CoroutineScope,
  private val newKey: () -> String = ::evidenceRequestKey,
  private val clock: () -> Long = ::platformNow,
) {
  var file by mutableStateOf<OrderEvidence.File?>(null)
    private set
  var loading by mutableStateOf(false)
    private set

  /** خطای گذرای خواندن (نشان داده می‌شود، با «تازه کردن») */
  var error by mutableStateOf<String?>(null)
    private set

  /** سرور «نه» گفت (پرچم خاموش شد یا اجازه نیست) ⇒ برگه فقط همین را می‌گوید و دکمه پنهان می‌شود */
  var refused by mutableStateOf(false)
    private set

  /** فرمِ باز (باز کردن، یادداشت یا بستن) — یکی در هر لحظه */
  var draft by mutableStateOf<OrderEvidence.Draft?>(null)
    private set
  var busy by mutableStateOf(false)
    private set

  /** پیامِ آخرین نوشتن: (موفق؟، متن) */
  var note by mutableStateOf<Pair<Boolean, String>?>(null)
    private set

  private var pending: OrderEvidence.Pending? = null

  suspend fun load() {
    loading = true
    try {
      src.file(orderId)
        .onSuccess { file = it; error = null; refused = false }
        .onFailure { f -> refuse(f) ?: run { error = f.userMessage } }
    } finally {
      loading = false
    }
  }

  fun reload(): Job = scope.launch { load() }

  /** ۴۰۳ (پرچم یا اجازه) ⇒ پنهان؛ ۴۰۴ ِ سفارش فقط همین برگه را می‌بندد */
  private fun refuse(f: ApiFailure): Unit? {
    if (f !is ApiFailure.Forbidden && !(f is ApiFailure.NotFound && f.code != OrderEvidence.NOT_FOUND)) return null
    refused = true
    file = null
    draft = null
    OrderEvidenceLive.gate.hide(clock())
    return Unit
  }

  fun begin(act: OrderEvidence.Act) {
    val f = file ?: return
    val ok = when (act) {
      OrderEvidence.Act.OPEN -> f.current == null
      else -> f.current != null
    }
    if (!ok || busy) return
    draft = OrderEvidence.Draft(act)
    note = null
  }

  fun edit(d: OrderEvidence.Draft) {
    if (draft?.act == d.act) draft = d.copy(text = d.text.take(OrderEvidence.TEXT_MAX + 50))
  }

  fun cancel() {
    if (!busy) { draft = null; pending = null }
  }

  /** «ثبت»: فقط فرمِ آماده؛ پاسخِ موفق همان اختلاف را در پرونده می‌نشاند (بی خواندنِ دوباره) */
  fun submit(): Job? {
    val f = file ?: return null
    val d = draft ?: return null
    if (busy || !d.ready(f.current)) return null
    val p = OrderEvidence.pending(pending, d, if (d.act == OrderEvidence.Act.OPEN) null else f.current?.id, newKey)
    pending = p
    busy = true
    note = null
    return scope.launch {
      try {
        src.write(orderId, p)
          .onSuccess { dispute ->
            file = file?.let { OrderEvidence.withDispute(it, dispute) }
            draft = null
            pending = null
            note = true to when (p.draft.act) {
              OrderEvidence.Act.OPEN -> StrEvidence.openedOk
              OrderEvidence.Act.NOTE -> StrEvidence.notedOk
              OrderEvidence.Act.RESOLVE -> StrEvidence.resolvedOk
              OrderEvidence.Act.ASSIGN -> StrEvidence.assignedOk
            }
          }
          .onFailure { e ->
            note = false to e.userMessage
            when {
              e is ApiFailure.Forbidden && e.code == OrderEvidence.OFF -> refuse(e)
              e.code in OrderEvidence.STALE -> { pending = null; draft = null; load() }
            }
          }
      } finally {
        busy = false
      }
    }
  }
}
