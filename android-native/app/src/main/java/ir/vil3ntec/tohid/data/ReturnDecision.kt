package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.returns.ReturnPolicy

/**
 *  RET-09، RET-10 — سدِ تأییدِ یک سندِ برگشت، یک‌جا برای صفحه و ثبت: [of] را هر دو فرمِ برگشت (`ReturnFlowDialog` ِ
 *  کامپیوتر و `ReturnFlowSheet` ِ نسخهٔ ۳) نشان می‌دهند و [gate] را `ReturnFlow.post` هنگامِ ثبت دوباره می‌سنجد؛ هر دو
 *  همان یک حساب‌اند: سقفِ نقش و سقفِ دستگاه تجمعیِ همان فاکتور (برگشت‌های پیشین + همین سند) و سیاستِ برگشتِ دکان
 *  (مهلت و اقلامِ غیرقابلِ برگشت). جدا بودنِ این دو در برگهٔ نسخهٔ ۳ همان بن‌بستِ «مجاز در صفحه، APPROVAL_REQUIRED در
 *  ثبت» و برگشتِ بی تأییدِ بیرون از سیاست بود (`ReturnDecisionTest`).
 */
object ReturnDecision {

  /**
   *  @param priorMinor برگشت‌های پیشینِ همین فاکتور به پول (`ReturnFlow.priorMinor`)
   *  @param perm تصمیمِ مجوزِ نقش با همان سقفِ تجمعی (`ReturnFlow.permDecision`) — همان که در `Request.perm` به ثبت می‌رود
   *  @param verdict حکمِ سیاستِ برگشت (هشدارِ «بیرون از سیاست» در صفحه، یادداشتِ استثنا در سابقه)
   */
  data class Decision(val priorMinor: Long, val perm: String?, val verdict: ReturnPolicy.Verdict, val gate: ReturnFlow.Gate)

  /**
   *  سدِ ثبت برای قلم‌های [lines] به جمعِ [total] — همان که `ReturnFlow.post` با `Request` می‌سنجد. [policy] `null` ⇒ بی
   *  سیاست (رفتارِ پیشین)؛ برگشتِ بی رسید مهلت ندارد (`ReturnFlow.policyCheck`).
   */
  fun gate(
    d: ShopData, sale: Sale?, lines: List<ReturnFlow.Line>, total: Double, role: String?, perm: String?, limit: Double,
    policy: ReturnPolicy.Policy?, today: String, priorMinor: Long = ReturnFlow.priorMinor(d, sale?.id),
  ): Pair<ReturnPolicy.Verdict, ReturnFlow.Gate> {
    val verdict = policy?.let { ReturnFlow.policyCheck(d, sale, lines, it, today) } ?: ReturnPolicy.Verdict()
    val g = ReturnFlow.gate(
      role, perm, ReturnFlow.minor(total), sale == null, lines.any { it.disposition == ReturnFlow.DAMAGED }, ReturnFlow.minor(limit),
      priorMinor, policyBreach = !verdict.ok,
    )
    return verdict to g
  }

  /**
   *  آنچه فرمِ برگشت نشان می‌دهد و با «ثبت» می‌فرستد: مجوزِ نقش با سقفِ تجمعی و همان [gate]. [quote] `null` (هنوز قلمی
   *  برگزیده نشده) ⇒ مبلغِ صفر و سیاستِ پذیرفته.
   */
  fun of(
    d: ShopData, sale: Sale?, quote: ReturnFlow.Quote?, role: String?, layers: List<Any?>?, limit: Double,
    policy: ReturnPolicy.Policy?, today: String,
  ): Decision {
    val prior = ReturnFlow.priorMinor(d, sale?.id)
    val total = quote?.total ?: 0.0
    val perm = ReturnFlow.permDecision(role, layers, ReturnFlow.minor(total), prior)
    val (verdict, g) = gate(d, sale, quote?.lines.orEmpty(), total, role, perm, limit, policy.takeIf { quote != null }, today, prior)
    return Decision(prior, perm, verdict, g)
  }
}
