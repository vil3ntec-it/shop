package ir.vil3ntec.tohid.core.perm

import ir.vil3ntec.tohid.core.text.JsText

/**
 *  «برگهٔ امضاشده»ی تأیید — STF-02 (بخشِ ۱۰.۱ِ مشخصات).
 *
 *  همتای دقیقِ `license/approval-core.js` (وب) و `server/src/lib/approval-core.js` (سرور).
 *  هر سه با یک فایلِ مرجع سنجیده می‌شوند: `core/test-vectors/approval-v1.json`
 *  (آزمونش: `android-native/app/src/test/.../core/perm/ApprovalSlipTest.kt`).
 *  ⛔ رفتارِ یکی را بی دیگری و بی فایلِ مرجع عوض نکنید.
 *
 *  مدیر یا صاحبِ دکان روی همان دستگاه PIN می‌زند، PIN کلیدِ خصوصیِ او را باز
 *  می‌کند (`ApproverVault`) و `payload` با ECDSA P-256/SHA-256 امضا می‌شود.
 *  برگه همراهِ سند (`approval`) می‌رود و سرور آن را با کلیدِ عمومیِ ثبت‌شده،
 *  اختیارِ خودِ تأییدکننده و nonceِ یک‌بارمصرف می‌سنجد.
 *
 *  Kotlin خالص است — بی android.*، بی کتابخانه.
 */
object ApprovalSlip {

  const val VERSION = 1
  const val HEADER = "tohid-approval/1"
  //  `sales.credit` (STF-03): فروشِ قرضی بیش از سقفِ کارمند — سرور با پرچمِ `shop.credit_cap` می‌سنجد
  //  `sales.pay_fix` (POS-40): برگه فقط روی همان دستگاه، برای «نیازمند تأیید» ِ اصلاحِ روشِ پرداخت؛ سرور هنوز opی با آن نمی‌سنجد
  //  `sales.price` (STF-03): تغییرِ قیمتِ ردیف بیش از سقفِ کارمند — سرور با پرچمِ `shop.price_cap` می‌سنجد (`SaleCaps`)
  //  `sales.free_line` (POS-10، تصمیمِ ۱-۱۱): قلمِ آزاد بیش از سقفِ قیمتِ واحد — همان پرچمِ `shop.price_cap`
  val PERMS: List<String> = listOf("returns.create", "sales.cancel", "sales.credit", "sales.discount", "sales.free_line", "sales.pay_fix", "sales.price")

  /** بیشترین عمرِ برگه: از PINِ مدیر تا بستنِ فاکتور */
  const val TTL_MAX_MS: Long = 15 * 60 * 1000L

  /** اختلافِ ساعتِ پذیرفتنی میانِ برگه و op */
  const val SKEW_MS: Long = 5 * 60 * 1000L
  const val MAX_REASON = 200

  private val NONCE_RE = Regex("^[A-Za-z0-9_-]{16,64}$")

  /** همان `isInt` ِ JS: هر عدد بیرون از ±(۲^۵۳−۱) نادرست است (فازِ تفاضلی: iat/exp/amount ِ ۲^۶۰ را Kotlin می‌پذیرفت) */
  private const val MAX_SAFE = 9_007_199_254_740_991L
  private fun safe(v: Long) = v in -MAX_SAFE..MAX_SAFE

  data class Slip(
    val shop: String,
    /** `<جدول>/<شناسهٔ ردیف>` — همان سندی که op رویش کار می‌کند */
    val doc: String,
    val perm: String,
    /** سقفِ درصدِ تأییدشده به صدمِ درصد (۱۰٪ = ۱۰۰۰) */
    val pctBp: Long?,
    /** سقفِ مبلغِ تأییدشده (Minor) */
    val amount: Long?,
    val by: String,
    val key: String,
    val iat: Long,
    val exp: Long,
    val nonce: String,
    val reason: String,
    val sig: String = "",
    val v: Int = VERSION,
  )

  /** کاری که op می‌کند؛ `at` زمانِ op (میلی‌ثانیه) */
  data class Need(
    val perm: String,
    val doc: String,
    val pctBp: Long? = null,
    val amount: Long? = null,
    val at: Long? = null,
  )

  private fun opt(v: Long?): String = v?.toString() ?: ""

  /** متنِ دقیقی که امضا می‌شود (UTF-8، خط‌ها با \n، بی خطِ خالیِ آخر) */
  fun payload(s: Slip): String = listOf(
    HEADER,
    "shop=${s.shop}",
    "doc=${s.doc}",
    "perm=${s.perm}",
    "pct_bp=${opt(s.pctBp)}",
    "amount=${opt(s.amount)}",
    "by=${s.by}",
    "key=${s.key}",
    "iat=${s.iat}",
    "exp=${s.exp}",
    "nonce=${s.nonce}",
    "reason=${s.reason}",
  ).joinToString("\n")

  private fun isText(v: String, max: Int) = v.isNotEmpty() && v.length <= max && '\n' !in v && '\r' !in v

  /** شکلِ برگه — null یعنی درست است، وگرنه کدِ خطا (همان کدهای JS) */
  fun check(s: Slip): String? {
    if (s.v != VERSION) return "bad_version"
    if (!isText(s.shop, 80) || !isText(s.doc, 200) || !isText(s.by, 80) || !isText(s.key, 80)) return "bad_slip"
    if (s.perm !in PERMS) return "bad_perm"
    if (s.pctBp != null && (s.pctBp < 0 || s.pctBp > 10_000)) return "bad_value"
    if (s.amount != null && (s.amount < 0 || !safe(s.amount))) return "bad_value"
    if (!safe(s.iat) || !safe(s.exp) || s.iat <= 0) return "bad_time"
    if (s.exp <= s.iat || s.exp - s.iat > TTL_MAX_MS) return "bad_time"
    if (!NONCE_RE.matches(s.nonce)) return "bad_nonce"
    //  «خالی» همان `trim()` ِ JS (BOM خالی است، U+001F نه) — `JsText`
    if (JsText.isBlank(s.reason) || s.reason.length > MAX_REASON || '\n' in s.reason || '\r' in s.reason) return "bad_reason"
    if (s.sig.isEmpty() || s.sig.length > 200) return "bad_sig"
    return null
  }

  /**
   *  نیازِ عددی که برگه نمی‌پوشاند (همان `uncovered` ِ JS): بی نیاز (null یا صحیحِ ≤ ۰) ⇐ نه؛ نیاز یا سقفِ بیرون از
   *  ±(۲^۵۳−۱) ⇐ هرگز پوشیده نمی‌شود (بسته‌به‌خطا)؛ وگرنه بیش از سقفِ برگه.
   */
  private fun uncovered(need: Long?, cap: Long?): Boolean {
    if (need == null || need in -MAX_SAFE..0L) return false
    return !safe(need) || cap == null || !safe(cap) || need > cap
  }

  /** آیا برگه همین کار را می‌پوشاند؟ null یعنی بله */
  fun covers(s: Slip, need: Need): String? {
    if (s.perm != need.perm || s.doc != need.doc) return "approval_mismatch"
    if (uncovered(need.pctBp, s.pctBp)) return "approval_too_small"
    if (uncovered(need.amount, s.amount)) return "approval_too_small"
    if (need.at != null && (!safe(need.at) || need.at < s.iat - SKEW_MS || need.at > s.exp + SKEW_MS)) return "approval_expired"
    return null
  }

  /** درخواستِ عددی برای `PermCore.evaluate`ِ تأییدکننده */
  fun evalReq(s: Slip): Map<String, Double> = buildMap {
    s.pctBp?.let { put("pct", it / 100.0) }
    s.amount?.let { put("amount", it.toDouble()) }
  }

  /**
   *  نیازِ تخفیفِ یک فاکتور (همان `needOf`ِ سرور): درصد با بخششِ نیم افغانی
   *  (`PermCore.discountBp`) و مبلغِ Minor.
   */
  fun discountNeed(saleId: String, discountMinor: Long, totalMinor: Long, at: Long? = null): Need =
    Need("sales.discount", "sales/$saleId", PermCore.discountBp(discountMinor, totalMinor), discountMinor, at)

  /** برگه ⇐ درختِ JSON (همان کلیدهای سرور) برای نشستن در `approval`ِ سند */
  fun toMap(s: Slip): Map<String, Any?> = linkedMapOf(
    "v" to s.v, "shop" to s.shop, "doc" to s.doc, "perm" to s.perm,
    "pct_bp" to s.pctBp, "amount" to s.amount, "by" to s.by, "key" to s.key,
    "iat" to s.iat, "exp" to s.exp, "nonce" to s.nonce, "reason" to s.reason, "sig" to s.sig,
  )

  /** درختِ JSON ⇐ برگه؛ شکلِ نادرست ⇐ null */
  fun fromMap(m: Map<*, *>?): Slip? {
    if (m == null) return null
    fun s(k: String) = m[k] as? String
    fun l(k: String): Long? = (m[k] as? Number)?.let { n ->
      val d = n.toDouble()
      if (d % 1.0 != 0.0) null else n.toLong()
    }
    return runCatching {
      Slip(
        shop = s("shop")!!, doc = s("doc")!!, perm = s("perm")!!,
        pctBp = if (m["pct_bp"] == null) null else l("pct_bp")!!,
        amount = if (m["amount"] == null) null else l("amount")!!,
        by = s("by")!!, key = s("key")!!, iat = l("iat")!!, exp = l("exp")!!,
        nonce = s("nonce")!!, reason = s("reason")!!, sig = s("sig") ?: "",
        //  نه `toInt()` ِ بی‌مرز: ۴۲۹۴۹۶۷۲۹۷ (۲^۳۲+۱) نسخهٔ ۱ می‌شد و JS ردش می‌کند
        v = l("v")?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt() ?: 0,
      )
    }.getOrNull()
  }
}
