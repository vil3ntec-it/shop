package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.text.TextNormalizer

/**
 *  پنجرهٔ «گروه، لیستِ قیمت و رضایتِ تبلیغ» ِ پروندهٔ مشتری — CRM-08 و CRM-12.
 *
 *  هستهٔ هر دو از #330 در `CustomerCrm` است (`setProfile` با ردِ `price_list_set`،
 *  `setMarketingConsent` با ردِ `marketing_consent` و منبع، `groups`، `promoRecipients`)؛ این‌جا فقط
 *  فرم است: سه انتخاب با هم ذخیره می‌شوند، **همه یا هیچ**، و شمارهٔ دوم و برچسب‌ها (CRM-02/11)
 *  همان می‌مانند که بودند.
 *
 *  ⛔ اثرِ لیستِ قیمت در صندوق (قیمتِ عمده برای مشتریِ «عمده») کارِ POS-18 است و این‌جا نیست؛
 *  این‌جا فقط پروندهٔ مشتری نوشته می‌شود.
 *
 *  Kotlin ِ خالص، بی android.* (آزمون: `CrmPricingFormTest`). چون `Debtor`/`ShopData` هنوز در `core`
 *  نیستند، کنارِ `CustomerCrm` ماند و همراهِ مدل‌ها به `core` می‌رود.
 */
object CrmPricingForm {

  /** `consent`: null = «نپرسیده»، true = «بله» (منبع لازم)، false = «نه» */
  data class Input(
    val group: String = "",
    val priceList: String = CustomerCrm.PRICE_RETAIL,
    val consent: Boolean? = null,
    val source: String = "",
  )

  fun of(debtor: Debtor): Input = Input(
    group = debtor.group.orEmpty(),
    priceList = CustomerCrm.priceListOf(debtor),
    consent = debtor.marketingConsent,
  )

  /**
   *  گروه و لیستِ قیمت (با `setProfile`، همان شمارهٔ دوم و برچسب‌ها) و رضایت (با
   *  `setMarketingConsent`) روی یک دفتر. هر کدام رد شود ⇒ `Failed` و هیچ چیز (دفترِ دست‌نخورده).
   *  بی تغییر ⇒ همان دفتر (بی op ِ بی‌دلیل).
   */
  fun save(
    d: ShopData,
    id: String,
    input: Input,
    today: String = ir.vil3ntec.tohid.todayIso(),
    now: Long = System.currentTimeMillis(),
    newId: () -> String = ::auditId,
  ): DebtorEngine.Result {
    val c = d.debtors.find { it.id == id } ?: return DebtorEngine.Result.Failed("مشتری پیدا نشد")
    val profile = CustomerCrm.profileOf(c).copy(group = input.group, priceList = input.priceList)
    val first = CustomerCrm.setProfile(d, id, profile, today, now, newId)
    if (first !is DebtorEngine.Result.Ok) return first
    //  رضایتِ دست‌نخورده دوباره سنجیده نمی‌شود: «بله»ی پیشین منبعِ تازه نمی‌خواهد
    if (input.consent == c.marketingConsent) return first
    return CustomerCrm.setMarketingConsent(first.data, id, input.consent, input.source, today, now, newId)
  }

  /** گروه‌های به‌کاررفته برای پیشنهاد (پرتکرارترین اول)؛ با نوشته، فقط آن‌هایی که نوشته را دارند */
  fun suggestions(d: ShopData, typed: String, limit: Int = 8): List<String> {
    val q = TextNormalizer.compareKey(typed.trim())
    return CustomerCrm.groups(d).map { it.first }
      .filter { q.isEmpty() || TextNormalizer.compareKey(it).contains(q) }
      .filter { TextNormalizer.compareKey(it) != q }
      .take(limit)
  }

  /** آخرین سندِ رضایتِ همین مشتری در دفترچهٔ ثبت (ردِ `marketing_consent`) */
  fun lastConsent(d: ShopData, id: String): AuditEntry? =
    d.auditLog.filter { it.type == "marketing_consent" && it.refId == id }.maxByOrNull { it.createdAt }

  /** «بله»ی تازه منبع می‌خواهد؛ «بله»ی از پیش ثبت‌شده که دست نخورده نه (چیزی نوشته نمی‌شود) */
  fun needsSource(debtor: Debtor, input: Input): Boolean =
    input.consent == true && debtor.marketingConsent != true && input.source.isBlank()
}
