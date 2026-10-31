package ir.vil3ntec.tohid.core.image

/*
 *  کارتِ «منبعِ عکس» ِ یک کالا در برنامهٔ مدیریت — v3 ۳۷.۷؛ IMG-BANK-08، ۰۹ و ۱۶.
 *
 *  ── ورودی ────────────────────────────────────────────────────────────
 *  همان پاسخِ `GET /api/shop/images/products/:id` (`resolve` ِ `lib/image-bank.js`)
 *  و `proposals` ِ عکسِ خودِ دکان از `GET /api/shop/images/mine?product_id=`.
 *
 *  ── قاعده‌ها ─────────────────────────────────────────────────────────
 *  • منبعِ فعال همیشه صریح است و کارت می‌گوید همین عکس در ویترینِ مشتری
 *    دیده می‌شود (۳۷.۷). جای خالی یعنی placeholder، هرگز عکسِ مشابه.
 *  • نسخهٔ تازهٔ مرکزی فقط **پیشنهاد** است (`update_available`)؛ هیچ‌چیز خودکار
 *    جایگزین نمی‌شود و نسخهٔ قبلی برمی‌گردد (IMG-BANK-09).
 *  • عکسِ خودِ دکان و عکسِ مرکزیِ نگه‌داشته: «بازگشت به عکسِ مرکزی» (IMG-BANK-16).
 *  • پیشنهادِ عکسِ خود به بانکِ مرکزی فقط برای صاحبِ دکان و فقط وقتی پیشنهادِ
 *    بازی (در انتظار یا پذیرفته) نیست؛ اجازهٔ صریح را رابط می‌گیرد (IMG-BANK-08).
 *  • عکسِ بایگانی‌شده سرِ جایش می‌ماند ولی دیگر هیچ انتخابی رویش نیست
 *    (سرور `image_not_selectable` می‌دهد).
 *  • این‌جا فقط «چه نشان داده شود» است؛ سرور هر کار را دوباره می‌سنجد.
 *
 *  منطقِ خالص، بی JSON و بی android.*؛ آزمون: `BankPhotoCardTest` (commonTest).
 */
object BankPhotoCard {

  enum class Source { CENTRAL, SHOP, LEGACY, NONE }

  /** یادداشت‌های کارت؛ متنِ هر کدام با برنامه است (زبان و واژه) */
  enum class Note {
    /** همین عکس در ویترینِ مشتری دیده می‌شود */
    SHOWN_IN_STOREFRONT,
    /** بی عکس — ویترین جای خالی نشان می‌دهد، نه عکسِ کالای دیگر */
    PLACEHOLDER,
    /** عکسِ همگام‌شدهٔ امروزِ دستگاه، بیرون از بانک */
    LEGACY_DEVICE,
    /** تطبیق بارکدِ دقیق نبود و کاربر خودش تأیید کرد */
    USER_CONFIRMED,
    /** عکسِ مرکزی از بانک بایگانی شده؛ همین‌جا می‌ماند ولی انتخاب‌شدنی نیست */
    ARCHIVED,
    /** آخرین عکسِ مرکزیِ این کالا برای بازگشت نگه داشته شده */
    CENTRAL_KEPT,
    /** پیشنهاد به بانکِ مرکزی در انتظارِ بررسیِ مدیر است */
    PROPOSAL_PENDING,
    /** پیشنهاد به بانکِ مرکزی پذیرفته شد */
    PROPOSAL_APPROVED,
  }

  data class Resolved(
    val source: Source,
    val imageId: String = "",
    val version: Int = 0,
    val archived: Boolean = false,
    /** نسخهٔ تازه‌ترِ تأییدشدهٔ همین عکسِ مرکزی؛ ۰ = نیست */
    val updateAvailable: Int = 0,
    val centralImageId: String = "",
    val centralVersion: Int = 0,
    val userConfirmed: Boolean = false,
  )

  data class Proposal(val id: String, val status: String)

  sealed interface Action {
    /** `select {source: central}` بی image_id ⇒ آخرین عکسِ مرکزیِ همین کالا */
    data object RevertToCentral : Action

    /**
     *  `select {source: central, image_id, version}`؛ `newer` = نسخهٔ تازه‌تر،
     *  وگرنه بازگشت به نسخهٔ قبلی. `confirm` همان تأییدِ پیشینِ کاربر است — کالای
     *  بانک همان است، پس تطبیق همان؛ تطبیقِ دقیق تأیید نمی‌خواهد.
     */
    data class UseCentralVersion(val imageId: String, val version: Int, val newer: Boolean, val confirm: Boolean) : Action

    /** `POST /:id/propose {consent: true}` — فقط صاحبِ دکان */
    data class Propose(val imageId: String) : Action
  }

  data class Card(val source: Source, val version: Int, val notes: List<Note>, val actions: List<Action>)

  /** همان رشته‌های `source` ِ سرور؛ ناشناخته ⇒ «بی عکس» (هرگز عکسِ ساختگی) */
  fun sourceOf(raw: String?): Source = when (raw) {
    "central" -> Source.CENTRAL
    "shop" -> Source.SHOP
    "legacy_photo" -> Source.LEGACY
    else -> Source.NONE
  }

  /** پیشنهادی که هنوز باز است — سرور تکرارش را همان برمی‌گرداند، پس دکمه لازم نیست */
  private val OPEN = setOf("DRAFT", "NEEDS_REVIEW", "PROCESSING", "APPROVED")

  fun card(r: Resolved, proposals: List<Proposal> = emptyList(), owner: Boolean = false): Card {
    val notes = ArrayList<Note>()
    val actions = ArrayList<Action>()
    val hasImage = r.imageId.isNotBlank() && r.version > 0
    val keptCentral = r.centralImageId.isNotBlank()
    //  «مرکزی/خود» بی شناسه یا نسخه یعنی چیزی برای نمایش نیست ⇒ همان «بی عکس»
    val shown = if ((r.source == Source.CENTRAL || r.source == Source.SHOP) && !hasImage) Source.NONE else r.source
    var version = 0
    when (shown) {
      Source.CENTRAL -> {
        version = r.version
        notes += Note.SHOWN_IN_STOREFRONT
        if (r.userConfirmed) notes += Note.USER_CONFIRMED
        if (r.archived) {
          notes += Note.ARCHIVED
        } else {
          if (r.updateAvailable > r.version) {
            actions += Action.UseCentralVersion(r.imageId, r.updateAvailable, newer = true, confirm = r.userConfirmed)
          }
          if (r.version > 1) {
            actions += Action.UseCentralVersion(r.imageId, r.version - 1, newer = false, confirm = r.userConfirmed)
          }
        }
      }
      Source.SHOP -> {
        version = r.version
        notes += Note.SHOWN_IN_STOREFRONT
        if (keptCentral) {
          notes += Note.CENTRAL_KEPT
          actions += Action.RevertToCentral
        }
        val open = proposals.filter { it.status in OPEN }
        when {
          open.any { it.status == "APPROVED" } -> notes += Note.PROPOSAL_APPROVED
          open.isNotEmpty() -> notes += Note.PROPOSAL_PENDING
          owner -> actions += Action.Propose(r.imageId)
        }
      }
      Source.LEGACY -> {
        notes += Note.LEGACY_DEVICE
        notes += Note.SHOWN_IN_STOREFRONT
      }
      Source.NONE -> {
        notes += Note.PLACEHOLDER
        if (keptCentral) {
          notes += Note.CENTRAL_KEPT
          actions += Action.RevertToCentral
        }
      }
    }
    return Card(shown, version, notes, actions)
  }
}
