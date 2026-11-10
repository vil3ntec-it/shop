package ir.vil3ntec.tohid.core.perm

import kotlin.random.Random

/**
 *  STF-02 — «پرسیدن از مدیر»: همتای کلاینتِ `server/src/lib/approval-requests.js` (`/api/shop/approval-requests`).
 *
 *  برگهٔ امضاشده ([ApprovalSlip]) فقط وقتی کار می‌کند که مدیر کنارِ صندوق PIN بزند. این‌جا صندوق‌دار می‌پرسد و صاحب یا
 *  مدیر روی **دستگاهِ خودش** تأیید/رد می‌کند؛ تصمیم فقط در سرور است و این پرونده فقط قاعده‌های نمایش و شکلِ پیام را دارد:
 *
 *   • [offered]: دکمه فقط با پرچمِ روشنِ سرور (`/config` ⇒ `enabled`)، تصمیمِ «نیازمندِ تأیید» و کاری که سرور می‌پذیرد
 *     ([PERMS]؛ `sales.pay_fix` نه). پرچمِ خاموش یا بی اینترنت ⇒ همان امروز (فقط PIN ِ همین دستگاه).
 *   • [body]: بدنهٔ `POST /` — همان سند و همان سقفِ درصد/مبلغِ [ApprovalSlip.Need]؛ کلیدِ بی‌خطرِ تکرار ([newKey]).
 *   • [slipOf]: درخواستِ «تأییدشده» ⇒ [ApprovalSlip.Slip] با `key = [KEY]` تا همان راهِ برگه (`covers`، `Sale.approval`،
 *     `cancelApproval`، `ReturnFlow`) بی تغییر بماند؛ [ref] آن را به `{v:1, request:<id>}` ِ سرور برمی‌گرداند. امضا ندارد:
 *     سرور ارجاع را با ردیفِ خودش می‌سنجد (`acceptSlip` ⇒ `approval-requests.accept`) و یک بار مصرفش می‌کند.
 *   • [waitOf] و [pollDelayMs]: حالِ انتظار و پرسیدنِ مؤدبانه (۲ تا ۱۰ ثانیه، هرگز پس از پایانِ عمر).
 *   • [canDecide] و [inbox]: صندوقِ تأییدِ مدیر — فقط صاحب/مدیر، نه درخواستِ خودش، و فقط اگر **خودش** همان مقدار را
 *     «مجاز» دارد (همان سنجهٔ سرور؛ سرور باز هم می‌سنجد).
 *
 *  Kotlin خالص — بی android.*، بی کتابخانه.
 */
object RemoteApproval {

  const val FLAG = "shop.approval_requests"
  /** `Slip.key` ِ تأییدِ از راهِ دور — ارجاع است، نه امضا */
  const val KEY = "remote"
  /**
   *  کارهایی که سرور درخواستشان را می‌پذیرد (`approval-core.PERMS` بی `sales.pay_fix`، که سرور op اش را نمی‌سنجد).
   *  STF-03: قرض (`sales.credit`) و تغییرِ قیمتِ ردیف (`sales.price`) فقط وقتی پرسیده می‌شوند که صندوق پیش‌سنجی‌شان کرده
   *  (`SaleCaps`، با پرچمِ سرور)؛ سرور هر دو را با همان `create` ِ عمومی و همان سقف می‌پذیرد.
   */
  val PERMS: List<String> = listOf("sales.discount", "sales.cancel", "returns.create", "sales.credit", "sales.price", "sales.free_line")
  /** کارهایی که بی مقدار معنا ندارند — همان `NEEDS_VALUE` ِ سرور */
  val NEEDS_VALUE: Set<String> = setOf("sales.discount", "returns.create", "sales.credit", "sales.price", "sales.free_line")
  val APPROVER_ROLES: List<String> = listOf("owner", "manager")
  const val TTL_DEFAULT_MS: Long = 10 * 60 * 1000L
  const val POLL_MIN_MS: Long = 2_000L
  const val POLL_MAX_MS: Long = 10_000L
  /** پس از چند خطای شبکهٔ پیاپی «بی اینترنت» نشان داده شود */
  const val OFFLINE_AFTER = 2

  private val KEY_RE = Regex("^[A-Za-z0-9_-]{8,64}$")
  private val ID_RE = Regex("^apr_[0-9a-f]{24}$")
  private val DOC_RE = Regex("^[A-Za-z][A-Za-z0-9_]{0,40}/[A-Za-z0-9_.:-]{1,120}$")
  private const val KEY_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

  enum class Status(val code: String) {
    PENDING("pending"), APPROVED("approved"), DENIED("denied"), CANCELLED("cancelled"), USED("used"), EXPIRED("expired"),
    UNKNOWN("");

    companion object {
      fun of(code: String?): Status = entries.firstOrNull { it.code.isNotEmpty() && it.code == code } ?: UNKNOWN
    }
  }

  /** پاسخِ `GET /config`؛ [v3] = سرور Sync v3 دارد (سرورِ کهنهٔ بی این کلید ⇒ همان امروز، درست) */
  data class Config(val enabled: Boolean, val approver: Boolean, val ttlMs: Long = TTL_DEFAULT_MS, val v3: Boolean = true)

  data class Request(
    val id: String,
    val perm: String,
    val doc: String,
    val pctBp: Long?,
    val amount: Long?,
    val reason: String,
    val status: Status,
    val requesterId: String,
    val requesterName: String = "",
    val createdAt: Long,
    val expiresAt: Long,
    val decidedBy: String? = null,
    val deciderName: String = "",
    val deciderRole: String = "",
    val note: String = "",
  )

  /**
   *  تأییدِ این کار برای این نقش فقط از Sync v3 می‌نشیند؟ پیش‌فرضِ نقش برای این کار «ممنوع» است (تخفیف و لغوِ شاگرد و
   *  صندوق‌دار): v1 ِ مشترک با پمپ خودش نقش را می‌سنجد (`staff-approval.v1Guard` ⇒ `approval_needs_v3`) و سرورِ بی Sync v3
   *  همان دمِ پرسیدن ۴۰۹ `approval_needs_v3` می‌دهد. برگشت، قرض و تغییرِ قیمت (پیش‌فرضِ «مجاز») از v1 هم می‌نشینند.
   */
  fun needsV3(role: String, perm: String): Boolean = PermCore.defaultMode(role, perm) == "deny"

  /**
   *  «پرسیدن از مدیر» دیده شود؟ [v3Only] = [needsV3] برای همین نقش؛ آن‌گاه فقط وقتی همین دستگاه ([deviceV3]) و سرور
   *  ([Config.v3]) هر دو Sync v3 دارند — وگرنه مدیر بیهوده تأیید می‌کرد و فاکتور باز رد می‌شد (PIN ِ همین دستگاه همان امروز).
   */
  fun offered(cfg: Config?, decision: String, perm: String, v3Only: Boolean = false, deviceV3: Boolean = true): Boolean =
    cfg?.enabled == true && decision == "approve" && perm in PERMS && lands(cfg, v3Only, deviceV3)

  /** تأییدِ از راهِ دور این‌جا می‌نشیند؟ کارِ فقط-v3 فقط با دستگاه و سرورِ Sync v3؛ بقیه همیشه */
  fun lands(cfg: Config?, v3Only: Boolean, deviceV3: Boolean): Boolean = !v3Only || (deviceV3 && cfg?.v3 != false)

  /** کلیدِ بی‌خطرِ تکرار (۲۴ نویسه) — یک بار برای هر «پرسیدن»، همان کلید برای فرستادنِ دوباره */
  fun newKey(rnd: Random = Random.Default): String = "k" + (1..23).map { KEY_CHARS[rnd.nextInt(KEY_CHARS.length)] }.joinToString("")

  /** دلیلِ پذیرفتنی: نه خالی، نه بلندتر از سقفِ برگه، بی خطِ تازه */
  fun reasonOk(reason: String): Boolean {
    val r = reason.trim()
    return r.isNotEmpty() && r.length <= ApprovalSlip.MAX_REASON && '\n' !in r && '\r' !in r
  }

  /** بدنهٔ `POST /` — شکلِ نادرست ⇒ null (همان سنجه‌های `parseBody` ِ سرور) */
  fun body(key: String, need: ApprovalSlip.Need, reason: String, ttlMs: Long? = null): Map<String, Any?>? {
    if (!KEY_RE.matches(key) || need.perm !in PERMS || !DOC_RE.matches(need.doc) || !reasonOk(reason)) return null
    if (need.pctBp != null && need.pctBp !in 0..10_000) return null
    if (need.amount != null && need.amount < 0) return null
    if (need.perm in NEEDS_VALUE && need.pctBp == null && need.amount == null) return null
    return buildMap {
      put("key", key); put("perm", need.perm); put("doc", need.doc)
      need.pctBp?.let { put("pctBp", it) }
      need.amount?.let { put("amount", it) }
      put("reason", reason.trim())
      ttlMs?.let { put("ttlMs", it) }
    }
  }

  private fun long(v: Any?): Long? = (v as? Number)?.let { n -> if (n.toDouble() % 1.0 != 0.0) null else n.toLong() }
    ?: (v as? String)?.toLongOrNull()

  /** درختِ JSON ِ `{ request }` یا خودِ درخواست ⇒ [Request]؛ شکلِ نادرست ⇒ null */
  fun parse(m: Map<*, *>?): Request? {
    val o = (m?.get("request") as? Map<*, *>) ?: m ?: return null
    fun s(k: String) = o[k] as? String
    val id = s("id")?.takeIf { ID_RE.matches(it) } ?: return null
    val perm = s("perm") ?: return null
    val doc = s("doc") ?: return null
    return Request(
      id = id, perm = perm, doc = doc, pctBp = long(o["pctBp"]), amount = long(o["amount"]), reason = s("reason").orEmpty(),
      status = Status.of(s("status")), requesterId = s("requesterId").orEmpty(), requesterName = s("requesterName").orEmpty(),
      createdAt = long(o["createdAt"]) ?: 0L, expiresAt = long(o["expiresAt"]) ?: 0L,
      decidedBy = s("decidedBy")?.takeIf { it.isNotBlank() }, deciderName = s("deciderName").orEmpty(),
      deciderRole = s("deciderRole").orEmpty(), note = s("note").orEmpty(),
    )
  }

  fun parseList(m: Map<*, *>?): List<Request> = (m?.get("requests") as? List<*>).orEmpty().mapNotNull { parse(it as? Map<*, *>) }

  fun parseConfig(m: Map<*, *>?): Config = Config(
    enabled = m?.get("enabled") == true,
    approver = m?.get("approver") == true,
    ttlMs = long(m?.get("ttlMs"))?.takeIf { it > 0 } ?: TTL_DEFAULT_MS,
    v3 = m?.get("v3") != false,
  )

  /** حالِ دیده‌شده با ساعتِ دستگاه (فقط نمایش؛ مرجع سرور است) */
  fun stateAt(r: Request, now: Long): Status =
    if ((r.status == Status.PENDING || r.status == Status.APPROVED) && r.expiresAt > 0 && now > r.expiresAt) Status.EXPIRED else r.status

  /** ثانیه‌های مانده تا پایانِ عمر (نه کمتر از صفر) */
  fun secondsLeft(r: Request, now: Long): Long = ((r.expiresAt - now).coerceAtLeast(0L) + 999L) / 1000L

  /** درخواستِ «تأییدشده» ⇒ برگهٔ ارجاع (برای همان راهِ برگه)؛ هر حالِ دیگر ⇒ null */
  fun slipOf(shop: String, r: Request): ApprovalSlip.Slip? {
    if (r.status != Status.APPROVED || r.decidedBy.isNullOrBlank()) return null
    return ApprovalSlip.Slip(
      shop = shop, doc = r.doc, perm = r.perm, pctBp = r.pctBp, amount = r.amount, by = r.decidedBy, key = KEY,
      iat = r.createdAt, exp = r.expiresAt, nonce = r.id, reason = r.reason, sig = "",
    )
  }

  /** این برگه ارجاع به درخواستِ از راهِ دور است؟ */
  fun isRef(s: ApprovalSlip.Slip): Boolean = s.key == KEY && ID_RE.matches(s.nonce)

  /** برگهٔ ارجاع ⇒ همان `approval` که سرور می‌خواند (`{v:1, request}`) */
  fun ref(s: ApprovalSlip.Slip): Map<String, Any?> = linkedMapOf("v" to 1, "request" to s.nonce)

  /** حالِ انتظارِ صندوق */
  enum class Wait { SENDING, WAITING, APPROVED, DENIED, EXPIRED, CANCELLED, OFFLINE, FAILED }

  /**
   *  @param r آخرین درخواستِ دیده‌شده (null ⇒ هنوز ساخته نشده)
   *  @param netErrors خطاهای شبکهٔ پیاپی (پاسخِ سرور ⇒ صفر)
   */
  fun waitOf(r: Request?, now: Long, netErrors: Int = 0): Wait {
    if (r == null) return if (netErrors >= OFFLINE_AFTER) Wait.OFFLINE else Wait.SENDING
    return when (stateAt(r, now)) {
      Status.APPROVED -> Wait.APPROVED
      Status.DENIED -> Wait.DENIED
      Status.EXPIRED -> Wait.EXPIRED
      Status.CANCELLED -> Wait.CANCELLED
      //  «مصرف‌شده» یعنی op ِ دیگری با آن نشست — برای این سبد دیگر به کار نمی‌آید
      Status.USED, Status.UNKNOWN -> Wait.FAILED
      Status.PENDING -> if (netErrors >= OFFLINE_AFTER) Wait.OFFLINE else Wait.WAITING
    }
  }

  /** پایانی است (پرسیدن بس)؟ */
  fun done(w: Wait): Boolean = w == Wait.APPROVED || w == Wait.DENIED || w == Wait.EXPIRED || w == Wait.CANCELLED || w == Wait.FAILED

  /** کدِ خطای سرور هنگامِ پرسیدن/ساختن ⇒ حالِ پایانی (null ⇒ خطای گذرا، دوباره بپرس) */
  fun waitOfError(code: String): Wait? = when (code) {
    "request_expired", "approval_expired" -> Wait.EXPIRED
    "request_not_found", "approval_requests_disabled", "approval_not_needed", "bad_perm", "request_invalid", "bad_value",
    "bad_reason", "bad_time", "request_key_reused", "too_many_pending", "request_closed", "discount_not_allowed", "approval_needs_v3",
    "cancel_not_allowed", "return_not_allowed", "credit_not_allowed", "price_not_allowed", "free_line_not_allowed", "perm_denied" -> Wait.FAILED
    else -> null
  }

  /**
   *  فاصلهٔ پرسیدنِ بعدی: ۲، ۲، ۳، ۳، ۵، ۵، ۸… تا سقفِ ۱۰ ثانیه؛ هرگز دیرتر از پایانِ عمر (+نیم ثانیه، تا «منقضی»
   *  همان دم دیده شود). سوکت یا long-poll برای این در نیست، پس همین پرسیدنِ آرام فقط تا وقتی برگه باز است.
   */
  fun pollDelayMs(attempt: Int, now: Long, expiresAt: Long): Long {
    val steps = longArrayOf(2_000, 2_000, 3_000, 3_000, 5_000, 5_000, 8_000)
    val base = if (attempt < 0) POLL_MIN_MS else steps.getOrElse(attempt) { POLL_MAX_MS }
    val left = expiresAt - now + 500L
    return if (expiresAt <= 0L) base else base.coerceAtMost(left.coerceAtLeast(500L)).coerceIn(500L, POLL_MAX_MS)
  }

  /** درخواستِ عددی برای `PermCore.evaluate` ِ تأییدکننده — همان `evalReq` ِ سرور */
  fun evalReq(r: Request): Map<String, Double> = buildMap {
    r.pctBp?.let { put("pct", it / 100.0) }
    r.amount?.let { put("amount", it.toDouble()) }
  }

  /**
   *  این بیننده تأیید/رد را ببیند؟ صاحب/مدیر، نه درخواستِ خودش، در انتظار و نگذشته، و **خودش** همین مقدار را «مجاز»
   *  دارد ([myDecision] = `PermCore.evaluate(myRole, myLayers, r.perm, evalReq(r)).decision`).
   */
  fun canDecide(meId: String, myRole: String, myDecision: String, r: Request, now: Long): Boolean =
    myRole in APPROVER_ROLES && meId.isNotBlank() && r.requesterId != meId &&
      stateAt(r, now) == Status.PENDING && myDecision == "allow"

  /** صندوقِ تأیید: در انتظار و نگذشته، نه خودِ بیننده؛ تازه‌ترین بالا. [decisionOf] = تصمیمِ بیننده برای همان درخواست */
  fun inbox(rows: List<Request>, meId: String, myRole: String, now: Long, decisionOf: (Request) -> String): List<Request> =
    rows.filter { canDecide(meId, myRole, decisionOf(it), it, now) }.sortedByDescending { it.createdAt }

  /** `sales/01J…` ⇒ (`sales`, `01J…`) */
  fun docParts(doc: String): Pair<String, String> =
    doc.substringBefore('/', "") to doc.substringAfter('/', doc)
}
