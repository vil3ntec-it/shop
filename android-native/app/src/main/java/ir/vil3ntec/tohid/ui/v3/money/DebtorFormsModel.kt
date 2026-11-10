package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrLedger
import ir.vil3ntec.tohid.core.perm.CreditLimitPerm
import ir.vil3ntec.tohid.core.shopper.GeoPin
import ir.vil3ntec.tohid.core.shopper.PinText
import ir.vil3ntec.tohid.data.CreditLimitGate
import ir.vil3ntec.tohid.data.CustomerCrm
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.data.DeliveryAddress
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.latinDigits

/** نوشته‌های برگهٔ قرض‌دار (`debtors/new`، `debtors/{id}/edit`) — با پروندهٔ CRM (#330) */
internal data class DebtorForm(
  val name: String = "",
  val phone: String = "",
  val phone2: String = "",
  val notes: String = "",
  val creditLimit: String = "",
  /** برچسب‌ها با «،» جدا */
  val tags: String = "",
  /** رضایتِ پیامِ تبلیغاتی: null نپرسیده، true بله، false نه */
  val consent: Boolean? = null,
  /** منبعِ «بله» («حضوری»، «تلفنی») — بی آن «بله» پذیرفته نیست */
  val consentSource: String = "",
  /** نشانیِ تحویل (CRM-09) — همه اختیاری؛ همه خالی ⇒ بی نشانی */
  val address: DeliveryAddress = DeliveryAddress(),
  /** پینِ همان نشانی، همان‌طور که نوشته یا چسبانده شد (مختصات یا پیوندِ نقشه، `PinText`)؛ خالی ⇒ بی پین */
  val pinText: String = "",
)

/** نوشته‌های برگهٔ تراکنش (`debtors/{id}/tx?kind=`) */
internal data class TxForm(
  val kind: DebtorEngine.Kind = DebtorEngine.Kind.GIVE,
  val amount: String = "",
  val date: String = "",
  val notes: String = "",
  /** سررسیدِ برد (`yyyy-MM-dd`)؛ خالی ⇒ ندارد */
  val due: String = "",
  /** نوشتهٔ همان لحظهٔ کادرِ سررسید (`DueDateEditor`) — فقط برای بستنِ «ثبت» وقتی ناخواناست */
  val dueText: String = "",
)

/**
 *  فرم‌های قرض‌دار و تراکنش — منطقِ خالص، زیرِ آزمون. سنجشِ درجا همان شرط‌های موتور است
 *  (`DebtorEngine.add/editWithLimit/addTransaction`، `CustomerCrm.setProfile/setMarketingConsent`)؛
 *  تصمیمِ «بیش از سقف» همان `DebtBook.creditCheck`.
 */
internal object DebtorFormsModel {

  fun of(debtor: Debtor?): DebtorForm {
    if (debtor == null) return DebtorForm()
    val p = CustomerCrm.profileOf(debtor)
    return DebtorForm(
      name = debtor.name,
      phone = debtor.phone,
      phone2 = p.phone2,
      notes = debtor.notes,
      creditLimit = limitText(debtor.creditLimit),
      tags = p.tags.joinToString("، "),
      consent = debtor.marketingConsent,
      address = debtor.deliveryAddress ?: DeliveryAddress(),
      pinText = CustomerCrm.pinText(debtor),
    )
  }

  /** CRM-09 — پین از کادر: خالی ⇒ بی پین (null)؛ نوشتهٔ بی مختصات ⇒ خطای درجا ([pinError]) */
  fun pinOf(f: DebtorForm): GeoPin? = f.pinText.takeIf { it.isNotBlank() }?.let { PinText.parse(it) }

  fun pinError(f: DebtorForm): String? = if (f.pinText.isNotBlank() && pinOf(f) == null) StrLedger.addressPinBad else null

  /** متنِ کادرِ سقف ⇒ عدد؛ خالی ⇒ null (بی سقف)؛ نوشتهٔ نامعتبر ⇒ NaN تا موتور ردش کند، نه این‌که بی‌صدا «بی سقف» شود */
  fun parseLimit(text: String): Double? =
    latinDigits(text).trim().takeIf { it.isNotEmpty() }?.let { it.toDoubleOrNull() ?: Double.NaN }

  fun limitText(v: Double?): String = v?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() }.orEmpty()

  fun nameError(f: DebtorForm): String? = if (f.name.isBlank()) StrKit.required else null

  fun limitError(f: DebtorForm): String? = parseLimit(f.creditLimit)?.let { if (it.isNaN() || it < 0) StrKit.notANumber else null }

  /**
   *  CRM-03 — سقفِ تازه‌ای که این کاربر نمی‌گذارد (`customers.credit_limit` با پرچمِ `shop.credit_limit_perm`) ⇒ خطای درجا؛
   *  نوشتهٔ نادرست را [limitError] می‌گوید. مشتریِ تازه، سقفِ عوض‌نشده یا قاعدهٔ خاموش ⇒ null.
   */
  fun limitPermError(rules: CreditLimitPerm.Rules, debtor: Debtor?, f: DebtorForm): String? {
    if (limitError(f) != null) return null
    return CreditLimitGate.error(rules, debtor, parseLimit(f.creditLimit))
  }

  /** «بله» بی منبع ⇒ خطای درجا (همان شرطِ `setMarketingConsent`) */
  fun consentError(f: DebtorForm): String? = if (f.consent == true && f.consentSource.isBlank()) StrKit.required else null

  fun draft(f: DebtorForm) = DebtorEngine.DebtorDraft(f.name, f.phone, f.notes, parseLimit(f.creditLimit))

  /**
   *  ذخیرهٔ کاملِ برگه — همه یا هیچ: قرض‌دار (`add` / `editWithLimit`)، بعد شمارهٔ دوم و برچسب
   *  (`CustomerCrm.setProfile`، گروه و لیستِ قیمت همان که بود)، نشانیِ تحویل (`setDeliveryAddress`، CRM-09) و پینش (`setDeliveryPin`)
   *  و رضایت (`setMarketingConsent`).
   *  هر کدام رد کند، هیچ‌چیز نوشته نمی‌شود و پیامِ همان موتور برمی‌گردد.
   */
  fun save(d: ShopData, editingId: String?, f: DebtorForm, now: Long, newId: () -> String): DebtorEngine.Result =
    save(d, editingId, f, now, CreditLimitPerm.Rules.OFF, newId)

  /** همان [save]، با CRM-03: آخرین سد پیش از نوشتنِ دفتر — تغییرِ سقفی که [limitRules] نمی‌پذیرد ⇒ پیامِ [limitPermError] و هیچ نوشتن */
  fun save(
    d: ShopData,
    editingId: String?,
    f: DebtorForm,
    now: Long,
    limitRules: CreditLimitPerm.Rules,
    newId: () -> String,
  ): DebtorEngine.Result {
    if (editingId != null) {
      limitPermError(limitRules, d.debtors.find { it.id == editingId }, f)?.let { return DebtorEngine.Result.Failed(it) }
    }
    val base = if (editingId == null) DebtorEngine.add(d, draft(f), now, newId) else DebtorEngine.editWithLimit(d, editingId, draft(f))
    val saved = base as? DebtorEngine.Result.Ok ?: return base
    val id = saved.id
    val debtor = saved.data.debtors.find { it.id == id } ?: return base
    val profile = CustomerCrm.profileOf(debtor).copy(phone2 = f.phone2, tags = CustomerCrm.parseTags(f.tags))
    val withProfile = CustomerCrm.setProfile(saved.data, id, profile)
    if (withProfile !is DebtorEngine.Result.Ok) return withProfile
    pinError(f)?.let { return DebtorEngine.Result.Failed(it) }
    val withAddress = CustomerCrm.setDeliveryAddress(withProfile.data, id, f.address)
    if (withAddress !is DebtorEngine.Result.Ok) return withAddress
    val pin = pinOf(f)
    val withPin = CustomerCrm.setDeliveryPin(withAddress.data, id, pin?.lat, pin?.lng)
    if (withPin !is DebtorEngine.Result.Ok) return withPin
    val withConsent = CustomerCrm.setMarketingConsent(withPin.data, id, f.consent, f.consentSource)
    return if (withConsent is DebtorEngine.Result.Ok) DebtorEngine.Result.Ok(withConsent.data, id) else withConsent
  }

  /* ------------------------------ تراکنش ------------------------------ */

  fun amountOf(f: TxForm): Double = f.amount.trim().toDoubleOrNull() ?: 0.0

  fun amountError(f: TxForm): String? = if (amountOf(f) <= 0) StrKit.notANumber else null

  /** سررسیدِ ناخوانا فقط برای برد (رسید سررسید ندارد) — «ثبت» بسته می‌ماند */
  fun dueBroken(f: TxForm): Boolean = f.kind == DebtorEngine.Kind.GIVE && DebtorTxModel.dueBroken(f.dueText)

  /** «مانده پس از ثبت» — بدهی + برد یا − رسید */
  fun balanceAfter(balance: Double, f: TxForm): Double =
    if (f.kind == DebtorEngine.Kind.GIVE) balance + amountOf(f) else balance - amountOf(f)

  /** «تسویهٔ کامل» (فقط رسید و فقط وقتی بدهکار است): همهٔ مانده تا پول، بی گرد — مانده پس از ثبت دقیقاً صفر */
  fun settleAll(balance: Double): String? = MoneyText.settleInput(balance)

  /** بردی که از سقف می‌گذرد ⇒ گامِ دوم با هشدار؛ رسید یا بی سقف ⇒ null */
  fun overLimit(d: ShopData, debtorId: String, f: TxForm): DebtBook.Credit? {
    if (f.kind != DebtorEngine.Kind.GIVE || amountOf(f) <= 0) return null
    return DebtBook.creditCheck(d, debtorId, DebtBook.minor(amountOf(f)))?.takeIf { it.over }
  }

  fun submit(d: ShopData, debtorId: String, f: TxForm, today: String, now: Long, newId: () -> String, overLimit: Boolean): DebtorEngine.Result =
    DebtorEngine.addTransaction(
      d, debtorId, f.kind, amountOf(f), f.date, f.notes, today, now, newId,
      dueDate = f.due.ifBlank { null }.takeIf { f.kind == DebtorEngine.Kind.GIVE },
      overLimit = overLimit,
    ).let { r ->
      //  PRM-13: رسیدِ مشتری امتیازِ سهمِ نسیهٔ پرداخت‌شده را در همان دفتر می‌دهد (همان `DebtEntry.change`)
      if (r is DebtorEngine.Result.Ok && f.kind == DebtorEngine.Kind.RECEIVE) r.copy(data = ir.vil3ntec.tohid.data.LoyaltyPoints.afterPayment(r.data, r.id, null)) else r
    }
}
