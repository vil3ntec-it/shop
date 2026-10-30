package ir.vil3ntec.tohid.core.audit

/**
 * SEC-03 — فاکتورِ ثبت‌شده ویرایش نمی‌شود؛ لغو و «اصلاح» فقط با **سندِ معکوس**.
 *
 *  ── چه می‌کند ─────────────────────────────────────────────────────────
 *  ۱) [check]: یک قاعده برای هر صفحه‌ای که لغو یا «اصلاح در سبد» دارد (صفحهٔ امروزِ تاریخچه و
 *     `CancelSheet` ِ نسخهٔ ۳، #391): فاکتور هست، لغوشده نیست، سفارشِ اینترنتی نیست (ORD-20)،
 *     کاربر اجازه دارد (`sales.cancel` ⇐ امروز صاحب و مدیر) و لغو **دلیل** دارد (تا ۱۲۰ نویسه؛
 *     «اصلاح» دلیلِ ثابتِ [CORRECTION_REASON] را دارد). کدِ ردِ هر حالت ثابت است؛ متن مالِ صفحه است.
 *  ۲) [reversalDoc]: سندِ معکوس برای زنجیرهٔ اسنادِ دستگاه (SEC-02): همان سندِ فاکتور
 *     ([DocChain.saleDoc]) با [PostedDocument.reverse] — مقدار و مبلغ قرینه، قیمتِ واحد همان، و
 *     `reverses`، `reason`، `by`، زمان و مرجع؛ `type = "void"` و `voidKind` (لغو یا اصلاح). سندِ اصلی
 *     دست نمی‌خورد (حلقه‌اش در زنجیره همان می‌ماند).
 *
 *  اثرِ دفتری همان امروز است (`SalesEngine.cancel`: وضعیتِ «لغوشده» + حرکتِ برگشتِ انبار + رسیدِ
 *  قرض) — این‌جا فقط کنترل و سندِ معکوسِ لنگرشده افزوده می‌شود. Kotlin خالص (commonMain).
 */
object InvoiceVoid {

  enum class Kind(val code: String) { VOID("void"), CORRECTION("correction") }

  /** دلیلِ «اصلاحِ فاکتور» (لغو و بردن به سبد) — همان داده‌ای که صفحه از پیش می‌نوشت (`HistoryModel.EDIT_IN_CART` ِ #391) */
  const val CORRECTION_REASON = "ویرایش در سبد"

  /** سقفِ دلیل — همان `reason.take(120)` ِ صفحه */
  const val REASON_MAX = 120

  /** آنچه کنترل از فاکتور لازم دارد */
  data class Facts(val status: String, val online: Boolean)

  sealed class Decision {
    /** مجاز؛ [reason] همان دلیلِ پاک‌شده (یا دلیلِ ثابتِ اصلاح) که در فاکتور و سند می‌نشیند */
    data class Allowed(val reason: String) : Decision()
    /** not_found | already_cancelled | online_order | no_permission | reason_required | reason_too_long */
    data class Denied(val code: String) : Decision()
  }

  /**
   *  [facts] null ⇐ فاکتور نیست. [canCancel] از نقش/مجوزِ همین کاربر (`ShopRole.canCancelSale` یا
   *  `PermCore` برای `sales.cancel`). ترتیبِ ردها ثابت است تا هر صفحه یک پیام نشان دهد.
   */
  fun check(facts: Facts?, canCancel: Boolean, kind: Kind, reason: String = ""): Decision {
    if (facts == null) return Decision.Denied("not_found")
    if (facts.status == "cancelled") return Decision.Denied("already_cancelled")
    if (facts.online) return Decision.Denied("online_order")
    if (!canCancel) return Decision.Denied("no_permission")
    if (kind == Kind.CORRECTION) return Decision.Allowed(CORRECTION_REASON)
    val r = reason.trim()
    if (r.isEmpty()) return Decision.Denied("reason_required")
    if (r.length > REASON_MAX) return Decision.Denied("reason_too_long")
    return Decision.Allowed(r)
  }

  /** شناسهٔ سندِ معکوسِ فاکتورِ [saleId] — هر فاکتور فقط یک بار لغو می‌شود */
  fun reversalId(saleId: String): String = "$saleId~r"

  /**
   *  سندِ معکوسِ [original] در زنجیرهٔ [chain] (حلقهٔ [seq])، ثبت‌شده در [at] به دستِ [by].
   *  @throws HashChainException اگر فاکتور شماره ندارد (`bad_opts`) یا عددش نامعتبر است
   */
  fun reversalDoc(
    chain: String,
    seq: Long,
    by: String,
    original: SaleFacts,
    at: Long,
    kind: Kind,
    reason: String,
  ): Map<String, Any?> {
    val posted = DocChain.saleDoc(chain, seq, "", original)
    val rev = LinkedHashMap(
      PostedDocument.reverse(posted, ReversalRequest(reversalId(original.id), original.no, at, reason, by)),
    )
    rev["type"] = "void"
    rev["voidKind"] = kind.code
    return rev
  }
}
