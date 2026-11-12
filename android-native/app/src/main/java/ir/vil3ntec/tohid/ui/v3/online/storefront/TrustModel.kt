package ir.vil3ntec.tohid.ui.v3.online.storefront

import ir.vil3ntec.tohid.core.i18n.StrTrust
import ir.vil3ntec.tohid.data.repo.TrustCaseDto
import ir.vil3ntec.tohid.data.repo.TrustConfigDto

/**
 *  ABUSE-01 — قاعده‌های صفحهٔ «شکایت و گزارشِ خریداران» (`TrustCasesV3.kt`)، بی Compose. همان مرزهای سرور
 *  (`lib/market-trust.js`): پاسخ ۱ تا ۲۰۰۰ حرف و حداکثر ۲۰ پاسخ در هر پرونده، بسته ⇒ پاسخ نه؛ اعتراض فقط به «وارد است»،
 *  یک بار، در مهلت و ۱۰ تا ۲۰۰۰ حرف. سرور دوباره می‌سنجد؛ این‌جا فقط تا دکمه‌ای نشان داده نشود که سرور رد می‌کند.
 */
object TrustModel {
  const val TEXT_MAX = 2000
  const val APPEAL_MIN = 10
  const val MAX_RESPONSES = 20

  enum class Problem { EMPTY, SHORT, LONG }

  fun responses(c: TrustCaseDto): Int = c.history.count { it.type == "shop_response" }

  /** پاسخ: فقط با `trust.respond`، پروندهٔ نابسته و زیرِ سقفِ پاسخ */
  fun canReply(cfg: TrustConfigDto?, c: TrustCaseDto): Boolean =
    cfg?.permissions?.respond == true && c.status != "closed" && responses(c) < MAX_RESPONSES

  /** اعتراض: فقط با `trust.respond`، همان `canAppeal` ِ سرور و پیش از پایانِ مهلت (ساعتِ این دستگاه فقط برای پنهان کردن) */
  fun canAppeal(cfg: TrustConfigDto?, c: TrustCaseDto, now: Long): Boolean =
    cfg?.permissions?.respond == true && c.canAppeal && c.appeal == null && (c.appealDeadline == null || now <= c.appealDeadline)

  fun replyProblem(text: String): Problem? = text.trim().let {
    when {
      it.isEmpty() -> Problem.EMPTY
      it.length > TEXT_MAX -> Problem.LONG
      else -> null
    }
  }

  fun appealProblem(text: String): Problem? = text.trim().let {
    when {
      it.length < APPEAL_MIN -> Problem.SHORT
      it.length > TEXT_MAX -> Problem.LONG
      else -> null
    }
  }

  /** «بی پاسخ»: باز یا در دستِ بررسی و هنوز هیچ پاسخی از دکان ندارد — همین‌ها بالای فهرست */
  fun waiting(c: TrustCaseDto): Boolean = c.status in setOf("open", "in_review") && responses(c) == 0

  /** فهرست: پرونده‌های بی‌پاسخ و قابلِ اعتراض بالا، سپس تازه‌ترین */
  fun sorted(list: List<TrustCaseDto>): List<TrustCaseDto> =
    list.sortedWith(compareByDescending<TrustCaseDto> { waiting(it) || it.canAppeal }.thenByDescending { it.createdAt }.thenBy { it.id })

  fun category(kind: String, c: String): String = if (kind == "report") when (c) {
    "counterfeit" -> StrTrust.repCounterfeit
    "prohibited" -> StrTrust.repProhibited
    "misleading" -> StrTrust.repMisleading
    "offensive" -> StrTrust.repOffensive
    "wrong_price" -> StrTrust.repWrongPrice
    else -> StrTrust.repOther
  } else when (c) {
    "not_delivered" -> StrTrust.cmpNotDelivered
    "wrong_item" -> StrTrust.cmpWrongItem
    "damaged" -> StrTrust.cmpDamaged
    "overcharged" -> StrTrust.cmpOvercharged
    "fraud" -> StrTrust.cmpFraud
    "rude" -> StrTrust.cmpRude
    else -> StrTrust.cmpOther
  }

  fun status(s: String): String = when (s) {
    "open" -> StrTrust.stOpen
    "in_review" -> StrTrust.stInReview
    "decided" -> StrTrust.stDecided
    "appealed" -> StrTrust.stAppealed
    "closed" -> StrTrust.stClosed
    else -> s
  }

  fun outcome(o: String): String = when (o) {
    "upheld" -> StrTrust.outUpheld
    "dismissed" -> StrTrust.outDismissed
    "reversed" -> StrTrust.outReversed
    else -> o
  }

  fun event(type: String): String = when (type) {
    "filed" -> StrTrust.evFiled
    "evidence_added" -> StrTrust.evEvidence
    "shop_response" -> StrTrust.reply
    "assigned" -> StrTrust.evAssigned
    "decided" -> StrTrust.evDecided
    "appealed" -> StrTrust.evAppealed
    "appeal_decided" -> StrTrust.evAppealDecided
    "sanction_applied" -> StrTrust.evSanction
    "sanction_lifted" -> StrTrust.evSanctionLifted
    else -> type
  }

  fun sanctionKind(k: String): String = when (k) {
    "warning" -> StrTrust.snWarning
    "hide_listing" -> StrTrust.snHide
    "suspend_storefront" -> StrTrust.snSuspend
    else -> k
  }

  /** «دربارهٔ …»: دکان شمارهٔ سفارشِ شاکی را نمی‌گیرد (سرور)، پس فقط «یکی از سفارش‌ها» */
  fun subject(c: TrustCaseDto, productName: (String) -> String?): String = when {
    c.kind == "report" && !c.productId.isNullOrEmpty() -> StrTrust.aboutProduct(productName(c.productId) ?: c.productId)
    c.subject == "order" -> StrTrust.aboutOrder
    else -> StrTrust.aboutShop
  }
}
