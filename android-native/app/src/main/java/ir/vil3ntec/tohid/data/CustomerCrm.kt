package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.core.text.LocaleFormat
import ir.vil3ntec.tohid.core.text.TextNormalizer

/**
 *  پروندهٔ مشتری — v3 §۶.۷: شمارهٔ دوم (CRM-02)، گروه و لیستِ قیمتِ پیش‌فرض (CRM-08)،
 *  برچسب (CRM-11) و رضایت برای پیامِ تبلیغاتی (CRM-12).
 *
 *  ── چیزی را که بود عوض نمی‌کند ───────────────────────────────────────
 *  `DebtorEngine.add/edit` همان می‌مانند (نام، تلفن، یادداشت، سقف) و `edit` با `copy`
 *  فیلدهای این‌جا را دست نمی‌زند. این پرونده فقط فیلدهای تازهٔ `Debtor` را می‌نویسد و
 *  همه‌شان `null`‌پذیرند: مشتریِ قدیمی همان است که بود.
 *
 *  ── «فعال» ─────────────────────────────────────────────────────────────
 *  مشتریِ ادغام‌شده (`mergedInto`) پاک نمی‌شود ولی فعال نیست — فهرست، جست‌وجو و
 *  انتخابِ مشتری باید از `active` بخوانند (CRM-10، `CustomerMerge`).
 */
object CustomerCrm {

  const val PRICE_RETAIL = "retail"
  const val PRICE_WHOLESALE = "wholesale"

  const val MAX_TAGS = 12
  const val MAX_TAG_LENGTH = 30
  const val MAX_GROUP_LENGTH = 40

  /* ------------------------------ فعال و ادغام ------------------------------ */

  fun isActive(debtor: Debtor): Boolean = debtor.mergedInto.isNullOrBlank()

  /** مشتریانِ فعال — همان ترتیبِ دفتر */
  fun active(d: ShopData): List<Debtor> = d.debtors.filter(::isActive)

  /**
   *  شناسهٔ ماندگار: زنجیرهٔ `mergedInto` را دنبال می‌کند (حداکثر ۳۲ گام؛ حلقه یا
   *  مقصدِ ناپیدا ⇒ آخرین شناسهٔ موجود). شناسهٔ ناپیدا همان برمی‌گردد.
   */
  fun resolve(d: ShopData, id: String): String {
    val byId = d.debtors.associateBy { it.id }
    var cur = id
    val seen = HashSet<String>()
    repeat(32) {
      val next = byId[cur]?.mergedInto?.takeIf { it.isNotBlank() } ?: return cur
      if (!seen.add(cur) || byId[next] == null) return cur
      cur = next
    }
    return cur
  }

  /* --------------------------------- تلفن --------------------------------- */

  /** همهٔ شماره‌های مشتری — اول، سپس دوم؛ خالی بیرون */
  fun phones(debtor: Debtor): List<String> =
    listOfNotNull(debtor.phone.takeIf { it.isNotBlank() }, debtor.phone2?.takeIf { it.isNotBlank() })

  /**
   *  کلیدِ مقایسهٔ شماره: ‎+93 / 0093 / 07… همه یک کلید. چیزی که کمتر از ۷ رقم دارد
   *  کلید ندارد (`null`) — «۱۲۳» دو مشتری را یکی نمی‌کند.
   */
  fun phoneKey(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    LocaleFormat.normalizePhone(raw)?.let { return it }
    val digits = LocaleFormat.asciiDigits(raw).filter { it in '0'..'9' }
    return digits.takeIf { it.length >= 7 }
  }

  /** مشتریانِ فعالی که یکی از شماره‌هایشان همین است */
  fun findByPhone(d: ShopData, raw: String): List<Debtor> {
    val key = phoneKey(raw) ?: return emptyList()
    return active(d).filter { c -> phones(c).any { phoneKey(it) == key } }
  }

  /**
   *  جست‌وجوی مشتری: نام (با همان نرمال‌سازیِ برنامه)، هر دو شماره، برچسب و گروه.
   *  پرسشِ خالی همه را می‌پذیرد.
   */
  fun matches(debtor: Debtor, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    if (TextNormalizer.matches(debtor.name, q)) return true
    if (phones(debtor).any { LocaleText.phoneMatches(it, q) }) return true
    val key = TextNormalizer.searchKey(q.removePrefix("#"))
    if (key.isEmpty()) return false
    if (debtor.tags.orEmpty().any { TextNormalizer.searchKey(it).contains(key) }) return true
    return debtor.group?.let { TextNormalizer.searchKey(it).contains(key) } == true
  }

  /* -------------------------------- برچسب -------------------------------- */

  private fun clean(s: String): String =
    s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

  /**
   *  برچسب‌ها ⇐ شکلِ ذخیره: «#» ِ اول برداشته، فاصله‌ها یکی، خالی بیرون، تکراری
   *  (با `compareKey`: ي/ی، ك/ک، نیم‌فاصله) فقط اولی. ترتیب همان ترتیبِ ورود.
   */
  fun normalizeTags(raw: List<String>): List<String> {
    val seen = HashSet<String>()
    return raw.map { clean(it.trim().trimStart('#')) }
      .filter { it.isNotEmpty() && seen.add(TextNormalizer.compareKey(it)) }
  }

  /** «عمده، همسایه #بدقول» ⇐ فهرست — جداکننده: ویرگولِ فارسی/لاتین یا سطرِ تازه */
  fun parseTags(text: String): List<String> = normalizeTags(text.split(',', '،', '\n', ';', '؛'))

  fun hasTag(debtor: Debtor, tag: String): Boolean {
    val k = TextNormalizer.compareKey(clean(tag.trimStart('#')))
    return k.isNotEmpty() && debtor.tags.orEmpty().any { TextNormalizer.compareKey(it) == k }
  }

  /** همهٔ برچسب‌های مشتریانِ فعال با شمار، پرتکرارترین اول */
  fun allTags(d: ShopData): List<Pair<String, Int>> {
    val first = LinkedHashMap<String, String>()
    val count = HashMap<String, Int>()
    for (c in active(d)) for (t in c.tags.orEmpty()) {
      val k = TextNormalizer.compareKey(t)
      first.putIfAbsent(k, t)
      count[k] = (count[k] ?: 0) + 1
    }
    return first.entries.map { (k, t) -> t to count.getValue(k) }
      .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
  }

  /** همهٔ گروه‌های به‌کاررفته با شمار، پرتکرارترین اول */
  fun groups(d: ShopData): List<Pair<String, Int>> =
    active(d).mapNotNull { it.group?.takeIf { g -> g.isNotBlank() } }
      .groupBy { TextNormalizer.compareKey(it) }
      .map { (_, l) -> l.first() to l.size }
      .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

  /* ----------------------------- لیستِ قیمت ----------------------------- */

  /** لیستِ قیمتِ پیش‌فرضِ مشتری — بی مشتری یا بی انتخاب: خرده */
  fun priceListOf(debtor: Debtor?): String =
    if (debtor?.priceList == PRICE_WHOLESALE) PRICE_WHOLESALE else PRICE_RETAIL

  /**
   *  قیمتِ واحدِ این کالا برای این مشتری (CRM-08، پایهٔ POS-18): عمده فقط وقتی کالا
   *  قیمتِ عمدهٔ مثبت دارد؛ وگرنه همان قیمتِ فروش. ⚠️ هنوز به صندوق وصل نیست.
   */
  fun unitPrice(product: Product, debtor: Debtor?): Double =
    if (priceListOf(debtor) == PRICE_WHOLESALE && product.wholesalePrice > 0) product.wholesalePrice
    else product.salePrice

  /* ------------------------------ ویرایشِ پرونده ------------------------------ */

  /** فیلدهای تازهٔ پرونده — ورودیِ فرم. خالی یعنی «ندارد». */
  data class Profile(
    val phone2: String = "",
    val tags: List<String> = emptyList(),
    val group: String = "",
    /** `retail` (یا خالی) | `wholesale` */
    val priceList: String = PRICE_RETAIL,
  )

  fun profileOf(debtor: Debtor): Profile = Profile(
    phone2 = debtor.phone2.orEmpty(),
    tags = debtor.tags.orEmpty(),
    group = debtor.group.orEmpty(),
    priceList = priceListOf(debtor),
  )

  /**
   *  شمارهٔ دوم، برچسب، گروه و لیستِ قیمت — همه یا هیچ. تغییرِ لیستِ قیمت (پول) در
   *  دفترچهٔ ثبت ردی می‌گذارد؛ بقیه مثلِ یادداشت بی رد.
   */
  fun setProfile(
    d: ShopData,
    id: String,
    p: Profile,
    today: String = ir.vil3ntec.tohid.todayIso(),
    now: Long = System.currentTimeMillis(),
    newId: () -> String = ::auditId,
  ): DebtorEngine.Result {
    val c = d.debtors.find { it.id == id } ?: return DebtorEngine.Result.Failed("مشتری پیدا نشد")
    if (!isActive(c)) return DebtorEngine.Result.Failed("این مشتری با مشتریِ دیگری ادغام شده است")

    val phone2 = LocaleText.phoneForSave(p.phone2).ifBlank { null }
    if (phone2 != null) {
      val k = phoneKey(phone2)
      if (phone2 == c.phone || (k != null && k == phoneKey(c.phone))) return DebtorEngine.Result.Failed("شمارهٔ دوم همان شمارهٔ اول است")
    }
    val tags = normalizeTags(p.tags)
    if (tags.size > MAX_TAGS) return DebtorEngine.Result.Failed("حداکثر $MAX_TAGS برچسب")
    if (tags.any { it.length > MAX_TAG_LENGTH }) return DebtorEngine.Result.Failed("هر برچسب حداکثر $MAX_TAG_LENGTH حرف")
    val group = clean(p.group).ifEmpty { null }
    if (group != null && group.length > MAX_GROUP_LENGTH) return DebtorEngine.Result.Failed("نامِ گروه حداکثر $MAX_GROUP_LENGTH حرف")
    val priceList = when (p.priceList.trim()) {
      "", PRICE_RETAIL -> null
      PRICE_WHOLESALE -> PRICE_WHOLESALE
      else -> return DebtorEngine.Result.Failed("لیستِ قیمتِ ناشناخته")
    }

    val next = c.copy(phone2 = phone2, tags = tags.ifEmpty { null }, group = group, priceList = priceList)
    if (next == c) return DebtorEngine.Result.Ok(d, id)
    fun label(v: String?) = if (v == PRICE_WHOLESALE) "عمده" else "خرده"
    val trace = if (priceList != c.priceList) AuditEntry(
      id = newId(), type = "price_list_set", date = today, refId = id, createdAt = now,
      notes = "لیستِ قیمتِ «${c.name}»: ${label(c.priceList)} ⇐ ${label(priceList)}",
    ) else null
    return DebtorEngine.Result.Ok(
      d.copy(
        debtors = d.debtors.map { if (it.id == id) next else it },
        auditLog = if (trace != null) d.auditLog + trace else d.auditLog,
      ),
      id = id,
    )
  }

  /* ------------------------- رضایتِ پیامِ تبلیغاتی ------------------------- */

  fun consentText(v: Boolean?): String = when (v) { true -> "بله"; false -> "نه"; null -> "نپرسیده" }

  /**
   *  رضایت برای پیامِ تبلیغاتی (CRM-12). هر تغییر در دفترچهٔ ثبت با تاریخ و منبع
   *  (`source`: «حضوری»، «تلفنی»…) می‌ماند — همان سندِ رضایت.
   */
  fun setMarketingConsent(
    d: ShopData,
    id: String,
    consent: Boolean?,
    source: String = "",
    today: String = ir.vil3ntec.tohid.todayIso(),
    now: Long = System.currentTimeMillis(),
    newId: () -> String = ::auditId,
  ): DebtorEngine.Result {
    val c = d.debtors.find { it.id == id } ?: return DebtorEngine.Result.Failed("مشتری پیدا نشد")
    if (!isActive(c)) return DebtorEngine.Result.Failed("این مشتری با مشتریِ دیگری ادغام شده است")
    if (c.marketingConsent == consent) return DebtorEngine.Result.Ok(d, id)
    val from = clean(source).take(60)
    val trace = AuditEntry(
      id = newId(), type = "marketing_consent", date = today, refId = id, createdAt = now,
      notes = "رضایتِ «${c.name}» برای پیامِ تبلیغاتی: ${consentText(c.marketingConsent)} ⇐ ${consentText(consent)}" +
        (if (from.isNotEmpty()) " — $from" else ""),
    )
    return DebtorEngine.Result.Ok(
      d.copy(
        debtors = d.debtors.map { if (it.id == id) it.copy(marketingConsent = consent) else it },
        auditLog = d.auditLog + trace,
      ),
      id = id,
    )
  }

  /**
   *  گیرندگانِ پیامِ تبلیغاتی: فقط مشتریِ فعال با رضایتِ **صریح** (`true`) و شماره‌ای
   *  که کلید دارد. «نپرسیده» یعنی نه. یادآوریِ بدهی تبلیغ نیست و از این‌جا نمی‌گذرد.
   */
  fun promoRecipients(d: ShopData): List<Debtor> =
    active(d).filter { it.marketingConsent == true && phones(it).any { p -> phoneKey(p) != null } }
}
