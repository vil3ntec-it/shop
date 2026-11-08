package ir.vil3ntec.tohid.core.sync

/**
 *  صندوقِ سندهای ردشده — قراردادِ کلاینت با `server/src/routes/shop-conflict-inbox.js` (`lib/conflict-inbox.js`، مهاجرتِ ۳۱۵).
 *
 *  صاحب/مدیر (گوشیِ نسخهٔ ۳ و کامپیوتر، همان پوسته) فهرست را می‌بیند و یکی از سه کار را می‌کند؛ دستگاهِ سازنده روی همان
 *  فاکتور «سرور نپذیرفت: …» یا «صاحب دوباره ثبت کرد» می‌نویسد ([markers]). همه‌چیز این‌جا فقط خواندن و شکل دادن است:
 *  ⛔ سرور تنها داور است — «دوباره ثبت» را همان زنجیرهٔ همگام‌سازی با نقشِ تأییدکننده می‌سنجد و شناسهٔ opها از شناسهٔ
 *     همین ردیف است (یک بار، نه دو بار)؛ این‌جا هیچ چیزی «پذیرفته» فرض نمی‌شود.
 *  ⛔ مبلغ یک‌صدمِ افغانی (`Minor`) و مقدار هزارم (`Milli`)، همان شکلِ سرور؛ تبدیل به واحدِ دفتر فقط در [cartLines].
 *  ورودی‌ها درختِ سادهٔ JSON‌اند (Map/List/String/Number/Boolean) — همان `ApprovalRequestsRepository.plain`.
 */
object ConflictInbox {
  const val ROOT = "/shop/conflict-inbox"

  const val OPEN = "open"
  const val RESUBMITTED = "resubmitted"
  const val FIXED = "fixed"
  const val DISMISSED = "dismissed"

  /** همراهی که فقط چون سندش رد شد کنار رفت — دلیلِ خودِ سند جای آن می‌نشیند */
  const val TX_REJECTED = "tx_rejected"

  /** ردی که هیچ تأییدی درستش نمی‌کند (`NO_APPROVAL` ِ سرور) */
  val NO_APPROVAL: Set<String> = setOf("return_exceeds_sold")

  data class Who(val id: String, val name: String, val role: String = "")

  data class Line(
    val name: String,
    val productId: String = "",
    val qtyMilli: Long? = null,
    val unitPriceMinor: Long? = null,
    val totalMinor: Long? = null,
  )

  data class Invoice(
    val number: Long?,
    val createdAt: Long?,
    val paymentMethod: String,
    val debtorId: String,
    val debtorName: String,
    val seller: String,
    val totalMinor: Long?,
    val discountMinor: Long?,
    val finalMinor: Long?,
    val paidMinor: Long?,
    val remainingMinor: Long?,
    val debtMinor: Long?,
    val items: List<Line>,
    val returns: List<Line>,
    val truncated: Boolean,
  )

  data class Item(
    val id: String,
    val kind: String,
    val reason: String,
    val perm: String,
    val status: String,
    val at: Long,
    val docTable: String,
    val docRowId: String,
    val saleId: String,
    val number: Long?,
    val path: String,
    val staff: Who,
    val deviceId: String,
    val deviceName: String,
    val amountMinor: Long?,
    val itemCount: Int,
    val items: List<Line>,
    val resubmittable: Boolean,
    val fixable: Boolean,
    val busy: Boolean,
    val lastError: String?,
    val resolvedAt: Long?,
    val resolvedBy: Who?,
    val note: String,
    val fixedDoc: String,
    val invoice: Invoice? = null,
  ) {
    val open: Boolean get() = status == OPEN
  }

  data class Page(val items: List<Item>, val open: Int)

  /** ردیف‌های خودِ درخواست‌کننده (`GET /mine`) */
  data class Mine(
    val id: String,
    val table: String,
    val rowId: String,
    val opId: String,
    val device: String,
    val reason: String,
    val status: String,
    val at: Long,
    val resolvedAt: Long?,
    val note: String,
    val fixedDoc: String,
    /** فقط ردیفِ «کنار گذاشته» یا «اصلاح‌شده»: آنچه دستگاهِ سازنده برای هم‌سو شدن با سرور لازم دارد ([RejectedSettle]) */
    val settle: Settle? = null,
  )

  /**
   *  `settle` ِ `/mine` — بستهٔ همان سند از دیدِ سرور (`loser_value.ops`) و مقدارِ امروزِ سرور برای ردیف‌هایی که opِ
   *  غیرِ درجِ بسته به آن‌ها دست زد (`"<جدول>/<شناسه>"` ⇒ ردیف؛ نبودِ ردیف روی سرور ⇒ null).
   */
  data class Settle(
    val path: String,
    val ops: List<SettleOp>,
    val base: Map<String, Map<*, *>?>,
    val truncated: Boolean,
  )

  /** یک op ِ بسته: شناسه و جای آن (`type` همان `type` ِ v1 یا `kind` ِ v3) */
  data class SettleOp(val id: String, val table: String, val rowId: String, val type: String)

  // ---------- خواندن ----------

  private fun long(v: Any?): Long? = (v as? Number)?.let { n -> if (n.toDouble() % 1.0 != 0.0) null else n.toLong() }
    ?: (v as? String)?.toLongOrNull()
  private fun str(v: Any?): String = v as? String ?: ""
  private fun map(v: Any?): Map<*, *>? = v as? Map<*, *>
  private fun list(v: Any?): List<*> = v as? List<*> ?: emptyList<Any>()
  private val ID_RE = Regex("^[A-Za-z0-9_.-]{1,80}$")

  private fun who(m: Map<*, *>?): Who? = m?.let { Who(str(it["id"]), str(it["name"]), str(it["role"])) }?.takeIf { it.id.isNotEmpty() }

  private fun line(m: Map<*, *>?): Line? = m?.let {
    Line(str(it["name"]), str(it["productId"]), long(it["qtyMilli"]), long(it["unitPriceMinor"]), long(it["totalMinor"] ?: it["amountMinor"]))
  }

  private fun invoice(m: Map<*, *>?): Invoice? = m?.let {
    val debtor = map(it["debtor"])
    Invoice(
      number = long(it["number"]), createdAt = long(it["createdAt"]), paymentMethod = str(it["paymentMethod"]),
      debtorId = str(debtor?.get("id")), debtorName = str(debtor?.get("name")), seller = str(it["seller"]),
      totalMinor = long(it["totalMinor"]), discountMinor = long(it["discountMinor"]), finalMinor = long(it["finalMinor"]),
      paidMinor = long(it["paidMinor"]), remainingMinor = long(it["remainingMinor"]), debtMinor = long(it["debtMinor"]),
      items = list(it["items"]).mapNotNull { x -> line(map(x)) }, returns = list(it["returns"]).mapNotNull { x -> line(map(x)) },
      truncated = it["truncated"] == true,
    )
  }

  /** `{ conflict }` یا خودِ ردیف ⇒ [Item]؛ شکلِ نادرست ⇒ null */
  fun parseItem(m: Map<*, *>?): Item? {
    val o = map(m?.get("conflict")) ?: m ?: return null
    val id = str(o["id"]).takeIf { ID_RE.matches(it) } ?: return null
    val doc = map(o["doc"])
    val device = map(o["device"])
    return Item(
      id = id, kind = str(o["kind"]), reason = str(o["reason"]).ifEmpty { "rejected" }, perm = str(o["perm"]),
      status = str(o["status"]).ifEmpty { OPEN }, at = long(o["at"]) ?: 0L,
      docTable = str(doc?.get("table")), docRowId = str(doc?.get("rowId")), saleId = str(doc?.get("saleId")),
      number = long(o["number"]), path = str(o["path"]),
      staff = who(map(o["staff"])) ?: Who("", ""), deviceId = str(device?.get("id")), deviceName = str(device?.get("name")),
      amountMinor = long(o["amountMinor"]), itemCount = long(o["itemCount"])?.toInt() ?: 0,
      items = list(o["items"]).mapNotNull { line(map(it)) },
      resubmittable = o["resubmittable"] == true, fixable = o["fixable"] == true, busy = o["busy"] == true,
      lastError = str(map(o["lastError"])?.get("reason")).ifEmpty { null },
      resolvedAt = long(o["resolvedAt"]), resolvedBy = who(map(o["resolvedBy"])),
      note = str(o["note"]), fixedDoc = str(o["fixedDoc"]), invoice = invoice(map(o["invoice"])),
    )
  }

  fun parsePage(m: Map<*, *>?): Page {
    val items = list(m?.get("conflicts")).mapNotNull { parseItem(map(it)) }
    return Page(items, long(m?.get("open"))?.toInt() ?: items.count { it.open })
  }

  fun parseMine(m: Map<*, *>?): List<Mine> = list(m?.get("conflicts")).mapNotNull { x ->
    val o = map(x) ?: return@mapNotNull null
    val id = str(o["id"]).takeIf { ID_RE.matches(it) } ?: return@mapNotNull null
    Mine(
      id = id, table = str(o["table"]), rowId = str(o["rowId"]), opId = str(o["opId"]), device = str(o["device"]),
      reason = str(o["reason"]).ifEmpty { "rejected" }, status = str(o["status"]).ifEmpty { OPEN }, at = long(o["at"]) ?: 0L,
      resolvedAt = long(o["resolvedAt"]), note = str(o["note"]), fixedDoc = str(o["fixedDoc"]),
      settle = settle(map(o["settle"])),
    )
  }

  private fun settle(m: Map<*, *>?): Settle? {
    m ?: return null
    val ops = list(m["ops"]).mapNotNull { x ->
      val o = map(x) ?: return@mapNotNull null
      val id = str(o["id"]).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
      SettleOp(id, str(o["table"]), str(o["rowId"]), str(o["type"]))
    }
    val base = LinkedHashMap<String, Map<*, *>?>()
    map(m["base"])?.forEach { (k, v) -> if (k is String) base[k] = map(v) }
    return Settle(str(m["path"]), ops, base, m["truncated"] == true)
  }

  // ---------- کار ----------

  /** بدنهٔ «کنار بگذار» یا «اصلاح و ثبت»؛ دلیلِ خالی ⇒ null (سرور هم ۴۰۰ می‌دهد) */
  fun dismissBody(reason: String, fixedSaleId: String? = null): Map<String, Any?>? {
    val r = reason.trim().replace(Regex("\\s+"), " ")
    if (r.length < 2) return null
    return mapOf("reason" to r.take(MAX_NOTE), "fixedDoc" to fixedSaleId?.takeIf { ID_RE.matches(it) }?.let { "sales/$it" })
  }

  const val MAX_NOTE = 200

  /** «اصلاح و ثبت» فقط فاکتورِ فروشی که قلم دارد؛ «دوباره ثبت» آنچه سرور گفت */
  fun actions(i: Item): Set<String> = buildSet {
    if (!i.open || i.busy) return@buildSet
    if (i.resubmittable && i.reason !in NO_APPROVAL) add("resubmit")
    if (i.fixable) add("fix")
    add("dismiss")
  }

  /** یک ردیفِ سبد: مقدار و قیمت به واحدِ دفتر (`SaleItem.quantity`، افغانی) */
  data class CartLine(val productId: String, val name: String, val quantity: Double, val unitPrice: Double)

  /** «اصلاح و ثبت»: قلم‌های فاکتورِ ردشده با همان قیمت ⇒ سبد؛ بی کالا یا بی مقدار ⇒ کنار */
  fun cartLines(inv: Invoice): List<CartLine> = inv.items.mapNotNull { l ->
    val q = l.qtyMilli?.takeIf { it > 0 } ?: return@mapNotNull null
    val price = l.unitPriceMinor ?: l.totalMinor?.let { t -> t * 1000 / q } ?: return@mapNotNull null
    CartLine(l.productId, l.name, q / 1000.0, price / 100.0)
  }

  // ---------- نشانِ روی فاکتورِ دستگاهِ سازنده ----------

  /** یک opِ ردشدهٔ همین دستگاه (دفترِ کنارِ صفِ v1 یا `rejected` ِ v3) */
  data class Rejected(val opId: String, val table: String, val rowId: String, val saleId: String, val reason: String, val at: Long)

  /** نشانِ یک فاکتور: دلیلِ رد، و اگر صاحب/مدیر بست، حالِ سرور */
  data class Marker(val reason: String, val status: String = OPEN, val note: String = "", val at: Long = 0, val returnDoc: Boolean = false)

  /**
   *  ردشده‌های همین دستگاه + ردیف‌های خودِ کاربر روی سرور ⇒ نشانِ هر فاکتور (کلید = شناسهٔ فاکتورِ فروش).
   *  دلیل: دلیلِ خودِ سند (فاکتور یا برگشت)، نه «همراه کنار رفت»؛ حال: ردیفی از [mine] با همان op یا همان سند.
   *  بی ردِ محلی نشانی نیست — دستگاهی که چیزی رد نشده نمی‌پرسد ([needsMine]).
   */
  fun markers(rejected: List<Rejected>, mine: List<Mine>): Map<String, Marker> {
    val out = LinkedHashMap<String, Marker>()
    for ((sale, rows) in rejected.filter { it.saleId.isNotEmpty() }.groupBy { it.saleId }) {
      val head = rows.firstOrNull { it.table == "sales" && it.rowId == sale } ?: rows.firstOrNull { it.table == "returns" }
      val why = (head?.reason?.takeIf { it != TX_REJECTED } ?: rows.firstOrNull { it.reason != TX_REJECTED }?.reason ?: head?.reason ?: TX_REJECTED)
      val ids = rows.mapTo(HashSet()) { it.opId }
      val server = mine.firstOrNull { it.opId in ids } ?: mine.firstOrNull { it.table == "sales" && it.rowId == sale }
      out[sale] = Marker(
        reason = server?.reason?.takeIf { it.isNotEmpty() && it != "rejected" } ?: why,
        status = server?.status ?: OPEN, note = server?.note.orEmpty(), at = rows.maxOf { it.at },
        returnDoc = head?.table == "returns",
      )
    }
    return out
  }

  /** آیا پرسیدنِ `/mine` معنا دارد (دستگاهی که چیزِ ردشده‌ای ندارد نمی‌پرسد) */
  fun needsMine(rejected: List<Rejected>): Boolean = rejected.any { it.saleId.isNotEmpty() }

  /** شناسهٔ فاکتورِ یک opِ ردشده: خودِ فاکتور، قلم/برگشت (`saleId`)، تراکنش (`refId`) */
  fun saleOf(table: String, rowId: String, fields: Map<*, *>?): String = when (table) {
    "sales" -> rowId
    "saleItems", "returns" -> str(fields?.get("saleId"))
    "transactions" -> str(fields?.get("refId"))
    else -> ""
  }
}
