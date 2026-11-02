package ir.vil3ntec.tohid.core.orders

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 *  RSK-08 — پروندهٔ مدرکِ اختلافِ سفارش برای کارمندِ دکان (`server/src/routes/order-evidence.js`، `lib/order-evidence.js`).
 *  منطقِ خالصِ صفحه، commonMain؛ هر تصمیم با سرور. پرچمِ خاموشِ `shop.orders.dispute_file` ⇒ ۴۰۳ `dispute_file_disabled`
 *  ⇒ هیچ‌چیز دیده نمی‌شود؛ فقط صاحب و `orders.manage` (سرور می‌سنجد).
 *  ⛔ هیچ‌چیز «روشن» فرض نمی‌شود: دکمه فقط وقتی پیداست که سرور به پرسشِ بی‌اثر ([PROBE_ID]) «این سفارش نیست» گفته باشد —
 *  یعنی در باز است و همین کاربر اجازه دارد؛ پرسش هیچ ردیفِ سابقه‌ای نمی‌نویسد (سرور دیدن را پس از یافتنِ سفارش ثبت می‌کند).
 *  ⛔ اختلاف فقط افزودنی است (باز کردن، یادداشت، سپردن به مسئول با مهلت، بستن با نتیجه)؛ هر نوشتن کلیدِ یکتا دارد و ارسالِ
 *  دوباره همان کلید را می‌برد. مسئول فقط از فهرستِ `assignees` ِ سرور (صاحب و کارمندانِ دارای `orders.manage`)؛ «مهلت گذشته»
 *  همان `overdue` ِ سرور است (فقط نما، بی هیچ کارِ خودکار).
 */
object OrderEvidence {

  const val ROOT = "/shop/orders/evidence"
  const val OFF = "dispute_file_disabled"

  /** شناسه‌ای که هیچ سفارشی ندارد: روشن و مجاز ⇒ ۴۰۴ [NOT_FOUND]؛ خاموش یا بی اجازه ⇒ ۴۰۳ */
  const val PROBE_ID = "probe"
  const val NOT_FOUND = "order_not_found"

  /** همان `KINDS` و `OUTCOMES` ِ `lib/order-evidence.js` */
  val KINDS: List<String> = listOf("not_delivered", "missing_item", "wrong_item", "damaged", "cash", "late", "other")
  val OUTCOMES: List<String> = listOf("customer_favor", "shop_favor", "settled", "withdrawn")

  /** همان `TEXT` ِ سرور (۳ تا ۱۰۰۰ نویسه) */
  const val TEXT_MIN = 3
  const val TEXT_MAX = 1000

  /** همان `CLOCK_WARN_MS` ِ سرور: اختلافِ ساعتِ دستگاهِ پیک بیش از ۵ دقیقه */
  const val CLOCK_WARN_MS = 5 * 60 * 1000L

  private val KEY = Regex("^[A-Za-z0-9_.:-]{8,64}$")
  private val DISPUTE = Regex("^aud_[0-9a-f]{24}$")
  private val ORDER = Regex("^[A-Za-z0-9_-]{1,80}$")

  fun validOrderId(id: String): Boolean = ORDER.matches(id)
  fun validKey(key: String): Boolean = KEY.matches(key)
  fun validDisputeId(id: String): Boolean = DISPUTE.matches(id)

  /** شناسهٔ نادرست ⇒ null (هرگز نشانیِ ساخته از دادهٔ بد) */
  fun filePath(orderId: String): String? = if (validOrderId(orderId)) "$ROOT/$orderId" else null
  fun openPath(orderId: String): String? = filePath(orderId)?.let { "$it/disputes" }
  fun notePath(orderId: String): String? = filePath(orderId)?.let { "$it/disputes/notes" }
  fun resolvePath(orderId: String): String? = filePath(orderId)?.let { "$it/disputes/resolve" }
  fun assignPath(orderId: String): String? = filePath(orderId)?.let { "$it/disputes/assign" }

  /** مهلت‌های آمادهٔ «سپردن» (ساعت از اکنون) — سرور تا ۹۰ روز می‌پذیرد */
  val DUE_HOURS: List<Int> = listOf(2, 24, 72)
  fun dueAt(now: Long, hours: Int): Long = now + hours * 3_600_000L
  /** همان [dueAt] از ساعتِ همین دستگاه (هسته، بی وابستگیِ پلتفرمی در صفحه) */
  fun dueIn(hours: Int): Long = dueAt(ir.vil3ntec.tohid.core.platform.currentTimeMillis(), hours)
  private val USER = Regex("^[A-Za-z0-9_-]{1,80}$")
  fun validUserId(id: String): Boolean = USER.matches(id)
  val probePath: String get() = "$ROOT/$PROBE_ID"

  /**
   *  پاسخِ پرسشِ بی‌اثر: true = دکمه پیدا، false = پنهان (پرچم، اجازه یا سرورِ کهنه)، null = نامعلوم (خطای گذرا ⇒ همان حالِ پیشین).
   *  [code] کدِ خطای سرور؛ [refused] یعنی ۴۰۳/۴۰۴ (نه شبکه).
   */
  fun probeSays(code: String, refused: Boolean): Boolean? = when {
    code == NOT_FOUND -> true
    refused -> false
    else -> null
  }

  // ── مدل ──

  data class By(val userId: String, val role: String)
  data class Note(val id: String, val text: String, val by: By, val at: Long)
  data class Resolution(val outcome: String, val text: String, val by: By, val at: Long)

  data class Dispute(
    val id: String,
    val kind: String,
    val reason: String,
    val status: String,
    val openedBy: By,
    val openedAt: Long,
    val notes: List<Note>,
    val resolution: Resolution?,
    /** §۱۹.۹ بندِ ۵: مسئول و مهلتِ اختلاف (آخرین سپردن)؛ null = هنوز سپرده نشده */
    val assigneeId: String? = null,
    val dueAt: Long? = null,
    /** اختلافِ باز و مهلتِ گذشته — همان `overdue` ِ سرور */
    val overdue: Boolean = false,
  ) {
    val open: Boolean get() = status == "open"
  }

  /** کسی که اختلاف را می‌شود به او سپرد (`assignees` ِ پرونده) */
  data class Assignee(val userId: String, val name: String, val role: String)

  data class OrderInfo(
    val id: String,
    val orderNo: Long,
    val status: String,
    val isTest: Boolean,
    val fulfillment: String,
    val paymentMethod: String,
    val paymentStatus: String,
    val codStatus: String,
    val totalMinor: Long,
    val itemsCount: Int,
    val createdAt: Long,
    val customerName: String,
    val phoneMasked: String,
    val area: String,
    val landmark: String,
    val hasPin: Boolean,
    val lines: Int,
  )

  data class Step(val kind: String, val toStatus: String, val actorType: String, val actorId: String, val reason: String, val at: Long)

  data class Assignment(
    val id: String,
    val attemptNo: Int,
    val mode: String,
    val courierId: String,
    val externalName: String,
    val status: String,
    val expectedCollectMinor: Long?,
    val cashCollectedMinor: Long?,
    val hasCode: Boolean,
    val codeVerified: Boolean,
    val manualConfirm: Boolean,
    /** null = این کاربر اجازهٔ دیدنِ عکسِ تحویل (`delivery.photo`) را ندارد */
    val photos: Int?,
    val events: Int,
    val deliveredAt: Long?,
  )

  data class Distance(val assignmentId: String, val toStatus: String, val meters: Long, val at: Long)

  data class Clock(val samples: Int, val maxAbsSkewMs: Long, val warnings: Int, val note: String)

  data class Scans(
    val pickStatus: String?,
    val bags: Int?,
    val shelfSlot: String,
    val pickEvents: Int,
    val notFound: Int,
    val shelfScans: Int,
    val bagsAtPickup: List<Int>,
  )

  data class IssueBrief(val kind: String, val status: String, val photos: Int, val at: Long)

  data class Contacts(val courierCalls: Int, val conversations: Int, val fromCustomer: Int, val fromStaff: Int, val none: Boolean, val note: String)

  data class File(
    val generatedAt: Long,
    val order: OrderInfo,
    val timeline: List<Step>,
    val photoConsent: Boolean?,
    val photosVisible: Boolean,
    val assignments: List<Assignment>,
    val distances: List<Distance>,
    val locationNote: String,
    val clock: Clock,
    val scans: Scans,
    val pickPhotos: Int,
    val issues: List<IssueBrief>,
    val contacts: Contacts,
    val disputes: List<Dispute>,
    val users: Map<String, String>,
    val couriers: Map<String, String>,
    val truncated: List<String>,
    val assignees: List<Assignee> = emptyList(),
  ) {
    val current: Dispute? get() = disputes.firstOrNull { it.open }
    val past: List<Dispute> get() = disputes.filterNot { it.open }

    /** نامِ کارمند یا پیک، وگرنه همان شناسهٔ کوتاه (دادهٔ سرور) */
    fun who(userId: String): String = users[userId]?.takeIf { it.isNotBlank() } ?: userId.take(10)
    fun courier(a: Assignment): String =
      couriers[a.courierId]?.takeIf { it.isNotBlank() } ?: a.externalName.ifBlank { a.courierId.take(10) }
  }

  // ── خواندنِ پاسخ ──

  private fun str(o: JsonObject?, k: String): String? = (o?.get(k) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
  private fun long(o: JsonObject?, k: String): Long? =
    (o?.get(k) as? JsonPrimitive)?.takeIf { !it.isString }?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
  private fun bool(o: JsonObject?, k: String): Boolean? = (o?.get(k) as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
  private fun obj(o: JsonObject?, k: String): JsonObject? = o?.get(k) as? JsonObject
  private fun arr(o: JsonObject?, k: String): List<JsonObject> = (o?.get(k) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
  private fun names(o: JsonObject?): Map<String, String> =
    o.orEmpty().mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.let { k to it } }.toMap()

  private fun by(o: JsonObject?): By = By(str(o, "userId").orEmpty(), str(o, "role").orEmpty())

  /** یک اختلاف؛ شناسه یا وضعیتِ نادرست ⇒ null */
  fun dispute(o: JsonObject?): Dispute? {
    o ?: return null
    val id = str(o, "id")?.takeIf(::validDisputeId) ?: return null
    val status = str(o, "status")?.takeIf { it == "open" || it == "resolved" } ?: return null
    val res = obj(o, "resolution")?.let { r ->
      Resolution(str(r, "outcome")?.takeIf { it in OUTCOMES } ?: "settled", str(r, "text").orEmpty(), by(obj(r, "by")), long(r, "at") ?: 0)
    }
    return Dispute(
      id = id, kind = str(o, "kind")?.takeIf { it in KINDS } ?: "other", reason = str(o, "reason").orEmpty(), status = status,
      openedBy = by(obj(o, "openedBy")), openedAt = long(o, "openedAt") ?: 0,
      notes = arr(o, "notes").map { n -> Note(str(n, "id").orEmpty(), str(n, "text").orEmpty(), by(obj(n, "by")), long(n, "at") ?: 0) },
      resolution = if (status == "resolved") res else null,
      assigneeId = str(o, "assigneeId")?.takeIf(::validUserId), dueAt = long(o, "dueAt"),
      overdue = status == "open" && bool(o, "overdue") == true,
    )
  }

  /** پاسخِ `POST …/disputes*`: `{dispute, replayed}` */
  fun written(o: JsonObject): Dispute? = dispute(obj(o, "dispute"))

  /** پاسخِ `GET /:id`؛ سفارشِ بی شناسه ⇒ null */
  fun file(o: JsonObject): File? {
    val ord = obj(o, "order") ?: return null
    val id = str(ord, "id")?.takeIf { it.isNotBlank() } ?: return null
    val cust = obj(ord, "customer")
    val addr = obj(ord, "address")
    val order = OrderInfo(
      id = id, orderNo = long(ord, "orderNo") ?: 0, status = str(ord, "status").orEmpty(), isTest = bool(ord, "isTest") == true,
      fulfillment = str(ord, "fulfillment").orEmpty(), paymentMethod = str(ord, "paymentMethod").orEmpty(),
      paymentStatus = str(ord, "paymentStatus").orEmpty(), codStatus = str(ord, "codStatus").orEmpty(),
      totalMinor = long(ord, "totalMinor") ?: 0, itemsCount = (long(ord, "itemsCount") ?: 0).toInt(),
      createdAt = long(ord, "createdAt") ?: 0, customerName = str(cust, "name").orEmpty(), phoneMasked = str(cust, "phoneMasked").orEmpty(),
      area = str(addr, "area").orEmpty(), landmark = str(addr, "landmark").orEmpty(), hasPin = bool(addr, "hasPin") == true,
      lines = arr(ord, "lines").size,
    )
    val timeline = arr(o, "timeline").map { e ->
      Step(str(e, "kind").orEmpty(), str(e, "toStatus").orEmpty(), str(e, "actorType").orEmpty(), str(e, "actorId").orEmpty(),
        str(e, "reason").orEmpty(), long(e, "at") ?: 0)
    }
    val dlv = obj(o, "delivery")
    val assignments = arr(dlv, "assignments").map { a ->
      val conf = obj(a, "confirmation")
      Assignment(
        id = str(a, "id").orEmpty(), attemptNo = (long(a, "attemptNo") ?: 1).toInt(), mode = str(a, "mode").orEmpty(),
        courierId = str(a, "courierId").orEmpty(), externalName = str(a, "externalName").orEmpty(), status = str(a, "status").orEmpty(),
        expectedCollectMinor = long(a, "expectedCollectMinor"), cashCollectedMinor = long(a, "cashCollectedMinor"),
        hasCode = bool(conf, "hasCode") == true, codeVerified = bool(conf, "codeVerified") == true,
        manualConfirm = bool(conf, "manualConfirm") == true,
        photos = (a["photos"] as? JsonArray)?.size, events = arr(a, "events").size, deliveredAt = long(a, "deliveredAt"),
      )
    }
    val loc = obj(o, "location")
    val clock = obj(o, "clock")
    val scans = obj(o, "scans")
    val pick = obj(scans, "pick")
    val pickEvents = arr(scans, "pickEvents")
    val contacts = obj(o, "contacts")
    val conversations = arr(contacts, "conversations")
    val people = obj(o, "people")
    return File(
      generatedAt = long(o, "generatedAt") ?: 0,
      order = order,
      timeline = timeline,
      photoConsent = bool(dlv, "photoConsent"),
      photosVisible = bool(dlv, "photosVisible") == true,
      assignments = assignments,
      distances = arr(loc, "atDelivery").map { d ->
        Distance(str(d, "assignmentId").orEmpty(), str(d, "toStatus").orEmpty(), long(d, "distanceM") ?: 0, long(d, "serverTime") ?: 0)
      },
      locationNote = str(loc, "note").orEmpty(),
      clock = Clock(
        samples = (long(clock, "samples") ?: 0).toInt(), maxAbsSkewMs = long(clock, "maxAbsSkewMs") ?: 0,
        warnings = (long(clock, "warnings") ?: 0).toInt(), note = str(clock, "note").orEmpty(),
      ),
      scans = Scans(
        pickStatus = str(pick, "status"), bags = long(pick, "bags")?.toInt(), shelfSlot = str(pick, "shelfSlot").orEmpty(),
        pickEvents = pickEvents.size, notFound = pickEvents.count { str(it, "outcome") == "not_found" },
        shelfScans = arr(scans, "shelfScans").size,
        bagsAtPickup = arr(scans, "bagsAtPickup").mapNotNull { long(it, "bagsScanned")?.toInt() },
      ),
      pickPhotos = arr(o, "pickPhotos").size,
      issues = arr(o, "issues").map { i ->
        IssueBrief(str(i, "kind").orEmpty(), str(i, "status").orEmpty(), arr(i, "photos").size, long(i, "createdAt") ?: 0)
      },
      contacts = Contacts(
        courierCalls = arr(contacts, "courierCalls").size, conversations = conversations.size,
        fromCustomer = conversations.sumOf { (long(it, "fromCustomer") ?: 0).toInt() },
        fromStaff = conversations.sumOf { (long(it, "fromStaff") ?: 0).toInt() },
        none = bool(contacts, "none") == true, note = str(contacts, "note").orEmpty(),
      ),
      disputes = arr(o, "disputes").mapNotNull(::dispute),
      users = names(obj(people, "users")),
      couriers = names(obj(people, "couriers")),
      truncated = (o["truncated"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
      assignees = arr(o, "assignees").mapNotNull { a ->
        str(a, "userId")?.takeIf(::validUserId)?.let { Assignee(it, str(a, "name").orEmpty(), str(a, "role").orEmpty()) }
      },
    )
  }

  /** پس از نوشتنِ موفق: همان اختلاف جایگزین یا افزوده (بی خواندنِ دوبارهٔ پرونده و بی ردیفِ «دیدنِ» تازه) */
  fun withDispute(f: File, d: Dispute): File =
    if (f.disputes.any { it.id == d.id }) f.copy(disputes = f.disputes.map { if (it.id == d.id) d else it })
    else f.copy(disputes = f.disputes + d)

  /** بیشترین اختلافِ ساعت به دقیقه (گرد به بالا) */
  fun skewMinutes(c: Clock): Long = (c.maxAbsSkewMs + 59_999) / 60_000

  // ── نوشتن ──

  enum class Act { OPEN, NOTE, RESOLVE, ASSIGN }

  fun clean(raw: String): String = raw.trim()

  /** طول به نویسه، نه واحدِ UTF-16 (همان `[...s].length` ِ سرور) */
  fun textOk(raw: String): Boolean = clean(raw).count { !it.isLowSurrogate() } in TEXT_MIN..TEXT_MAX

  /**
   *  فرمِ یک نوشتن. [choice] نوعِ اختلاف (باز کردن)، نتیجه (بستن) یا شناسهٔ مسئول (سپردن)؛ یادداشت ندارد.
   *  `ready` فقط با متنِ درست، انتخابِ معتبر و — برای یادداشت، سپردن و بستن — اختلافِ باز. سپردن [dueAt] می‌خواهد و
   *  متنش اختیاری است (خالی ⇒ بی یادداشت).
   */
  data class Draft(val act: Act, val choice: String = "", val text: String = "", val dueAt: Long? = null, val dueHours: Int? = null) {
    fun ready(current: Dispute?): Boolean = when (act) {
      Act.OPEN -> textOk(text) && current == null && choice in KINDS
      Act.NOTE -> textOk(text) && current != null
      Act.RESOLVE -> textOk(text) && current != null && choice in OUTCOMES
      Act.ASSIGN -> current != null && validUserId(choice) && dueAt != null && (clean(text).isEmpty() || textOk(text))
    }
  }

  /** بدنهٔ درخواست؛ کلید یا شناسهٔ اختلافِ نادرست ⇒ null */
  fun body(d: Draft, key: String, disputeId: String?): JsonObject? {
    if (!validKey(key)) return null
    if (d.act != Act.OPEN && (disputeId == null || !validDisputeId(disputeId))) return null
    val due = d.dueAt
    if (d.act == Act.ASSIGN && (due == null || !validUserId(d.choice))) return null
    return buildJsonObject {
      put("requestKey", key)
      when (d.act) {
        Act.OPEN -> { put("kind", d.choice); put("reason", clean(d.text)) }
        Act.NOTE -> { put("text", clean(d.text)); put("disputeId", disputeId) }
        Act.RESOLVE -> { put("outcome", d.choice); put("text", clean(d.text)); put("disputeId", disputeId) }
        Act.ASSIGN -> {
          put("assigneeId", d.choice); put("dueAt", due)
          clean(d.text).takeIf { it.isNotEmpty() }?.let { put("text", it) }
          put("disputeId", disputeId)
        }
      }
    }
  }

  /** نوشتنِ در جریان: همان فرم و همان کلید تا پاسخِ موفق — ارسالِ دوباره پس از قطعِ نت همان ردیف را برمی‌گرداند */
  data class Pending(val draft: Draft, val key: String, val disputeId: String?)

  /** کلیدِ این ارسال: همان فرمِ ناموفقِ پیشین ⇒ همان کلید؛ فرمِ تازه ⇒ [fresh] */
  fun pending(prev: Pending?, d: Draft, disputeId: String?, fresh: () -> String): Pending {
    val now = d.copy(text = clean(d.text))
    return if (prev != null && prev.draft == now && prev.disputeId == disputeId) prev else Pending(now, fresh(), disputeId)
  }

  /** کدهای ۴۰۹ که یعنی «پرونده کهنه است» ⇒ خواندنِ دوباره */
  val STALE: Set<String> = setOf("dispute_open", "no_open_dispute", "dispute_changed")
}
