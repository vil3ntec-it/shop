package ir.vil3ntec.tohid.core.image

import ir.vil3ntec.tohid.core.image.BankPhotoCard.Action
import ir.vil3ntec.tohid.core.image.BankPhotoCard.Note
import ir.vil3ntec.tohid.core.image.BankPhotoCard.Proposal
import ir.vil3ntec.tohid.core.image.BankPhotoCard.Resolved
import ir.vil3ntec.tohid.core.image.BankPhotoCard.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** IMG-BANK-08، ۰۹، ۱۰ و ۱۶ — همان پاسخ‌های `image-bank.test.js` ِ سرور. */
class BankPhotoCardTest {

  private val central = Resolved(Source.CENTRAL, imageId = "img_c", version = 1, centralImageId = "img_c", centralVersion = 1)

  @Test fun sourceOfServerStrings() {
    assertEquals(Source.CENTRAL, BankPhotoCard.sourceOf("central"))
    assertEquals(Source.SHOP, BankPhotoCard.sourceOf("shop"))
    assertEquals(Source.LEGACY, BankPhotoCard.sourceOf("legacy_photo"))
    assertEquals(Source.NONE, BankPhotoCard.sourceOf("none"))
    //  ناشناخته یا نبودن ⇒ «بی عکس»، هرگز عکسِ ساختگی
    assertEquals(Source.NONE, BankPhotoCard.sourceOf("something-new"))
    assertEquals(Source.NONE, BankPhotoCard.sourceOf(null))
  }

  @Test fun centralSaysItIsTheStorefrontPhotoAndHasNoActionWithoutUpdate() {
    val c = BankPhotoCard.card(central)
    assertEquals(Source.CENTRAL, c.source)
    assertEquals(1, c.version)
    assertEquals(listOf(Note.SHOWN_IN_STOREFRONT), c.notes)
    assertEquals(emptyList(), c.actions)
  }

  @Test fun img_bank_09_newerCentralVersionIsOnlyASuggestion() {
    val c = BankPhotoCard.card(central.copy(updateAvailable = 2))
    //  سنجاقِ دکان همان نسخهٔ ۱ است؛ نسخهٔ ۲ فقط یک کار است، نه جایگزینِ خودکار
    assertEquals(1, c.version)
    assertEquals(listOf<Action>(Action.UseCentralVersion("img_c", 2, newer = true, confirm = false)), c.actions)
  }

  @Test fun img_bank_09_previousVersionCanBeRestoredWithTheSameConfirmation() {
    val c = BankPhotoCard.card(central.copy(version = 2, centralVersion = 2, userConfirmed = true))
    assertTrue(Note.USER_CONFIRMED in c.notes)
    assertEquals(listOf<Action>(Action.UseCentralVersion("img_c", 1, newer = false, confirm = true)), c.actions)
  }

  @Test fun updateThatIsNotNewerIsIgnored() {
    assertEquals(emptyList(), BankPhotoCard.card(central.copy(updateAvailable = 1)).actions)
  }

  @Test fun img_bank_15_archivedCentralStaysButNothingIsSelectable() {
    val c = BankPhotoCard.card(central.copy(version = 2, archived = true, updateAvailable = 3))
    assertTrue(Note.ARCHIVED in c.notes)
    assertTrue(Note.SHOWN_IN_STOREFRONT in c.notes)
    assertEquals(emptyList(), c.actions)
  }

  @Test fun img_bank_16_ownPhotoWithKeptCentralOffersRevert() {
    val r = Resolved(Source.SHOP, imageId = "img_own", version = 1, centralImageId = "img_c", centralVersion = 1)
    val c = BankPhotoCard.card(r)
    assertEquals(Source.SHOP, c.source)
    assertEquals(listOf(Note.SHOWN_IN_STOREFRONT, Note.CENTRAL_KEPT), c.notes)
    assertEquals(listOf<Action>(Action.RevertToCentral), c.actions)
  }

  @Test fun img_bank_08_onlyTheOwnerMayProposeAndOnlyOnce() {
    val own = Resolved(Source.SHOP, imageId = "img_own", version = 1)
    assertEquals(listOf<Action>(Action.Propose("img_own")), BankPhotoCard.card(own, owner = true).actions)
    //  شاگرد/مدیر: سرور ۴۰۳ می‌دهد، پس دکمه‌ای هم نیست
    assertEquals(emptyList(), BankPhotoCard.card(own, owner = false).actions)
    val pending = BankPhotoCard.card(own, listOf(Proposal("img_p", "NEEDS_REVIEW")), owner = true)
    assertEquals(emptyList(), pending.actions)
    assertTrue(Note.PROPOSAL_PENDING in pending.notes)
    val approved = BankPhotoCard.card(own, listOf(Proposal("img_p", "APPROVED")), owner = true)
    assertTrue(Note.PROPOSAL_APPROVED in approved.notes)
    assertEquals(emptyList(), approved.actions)
    //  پیشنهادِ ردشده یا بایگانی‌شده باز نیست ⇒ پیشنهادِ دوباره ممکن است (همان قاعدهٔ `propose` ِ سرور)
    val rejected = BankPhotoCard.card(own, listOf(Proposal("img_p", "REJECTED"), Proposal("img_q", "ARCHIVED")), owner = true)
    assertEquals(listOf<Action>(Action.Propose("img_own")), rejected.actions)
  }

  @Test fun img_bank_10_noPhotoIsAPlaceholderNeverASimilarImage() {
    val c = BankPhotoCard.card(Resolved(Source.NONE))
    assertEquals(Source.NONE, c.source)
    assertEquals(listOf(Note.PLACEHOLDER), c.notes)
    assertEquals(emptyList(), c.actions)
    //  «بی عکس» با عکسِ مرکزیِ نگه‌داشته ⇒ بازگشت ممکن است
    val kept = BankPhotoCard.card(Resolved(Source.NONE, centralImageId = "img_c", centralVersion = 1))
    assertEquals(listOf<Action>(Action.RevertToCentral), kept.actions)
  }

  @Test fun centralOrShopWithoutImageIsShownAsPlaceholder() {
    val c = BankPhotoCard.card(Resolved(Source.CENTRAL, imageId = "", version = 0))
    assertEquals(Source.NONE, c.source)
    assertEquals(listOf(Note.PLACEHOLDER), c.notes)
    val s = BankPhotoCard.card(Resolved(Source.SHOP, imageId = "img_own", version = 0), owner = true)
    assertEquals(Source.NONE, s.source)
    assertEquals(emptyList(), s.actions)
  }

  @Test fun legacyDevicePhotoIsExplicit() {
    val c = BankPhotoCard.card(Resolved(Source.LEGACY))
    assertEquals(listOf(Note.LEGACY_DEVICE, Note.SHOWN_IN_STOREFRONT), c.notes)
    assertEquals(emptyList(), c.actions)
  }
}
