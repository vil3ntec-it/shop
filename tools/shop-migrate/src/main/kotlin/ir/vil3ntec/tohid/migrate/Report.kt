package ir.vil3ntec.tohid.migrate

import ir.vil3ntec.tohid.data.ShopData

/**
 *  متنِ گزارشِ تطبیق.
 *
 *  پیش‌فرض **بی دادهٔ شخصی** است (بخشِ ۸، مهاجرت بند ۲): نه نام، نه تلفن،
 *  نه یادداشت، نه نامِ فروشگاه؛ هر کالا/مشتری/تأمین‌کننده با یک برچسبِ
 *  هش‌شده (`p:…`، `c:…`، `s:…`) می‌آید. همین متن است که به ایجنت می‌رسد.
 *  `names = true` نام‌ها را نشان می‌دهد — فقط برای خودِ صاحبِ دکان، روی
 *  کامپیوترِ خودش؛ آن خروجی فرستاده نمی‌شود.
 */
object Report {

  private val CODES = linkedMapOf(
    "DOUBLE_DECIMALS" to "اعشار ناشی از Double: ورودیِ قدیم بیش از ۲ رقمِ اعشار (پول) یا ۳ رقم (مقدار) داشت",
    "PLACEHOLDER_PRODUCT" to "کالای حذف‌شده‌ای که سابقه به آن ارجاع دارد ⇐ ردیفِ جانشینِ غیرفعال",
    "PLACEHOLDER_CUSTOMER" to "قرض‌دارِ حذف‌شده‌ای که سابقه به آن ارجاع دارد ⇐ ردیفِ جانشین",
    "PLACEHOLDER_SUPPLIER" to "تأمین‌کنندهٔ حذف‌شده‌ای که سابقه به آن ارجاع دارد ⇐ ردیفِ جانشین",
    "DUPLICATE_ID" to "شناسهٔ تکراری در یک فهرست (کالا/مشتری/تأمین‌کننده: اولی ماند؛ فروش: هر دو ماندند)",
    "DUPLICATE_BARCODE" to "یک بارکد روی چند کالا ⇐ همه با پرچمِ «تکراری» ماندند (۳.۴)",
    "DUPLICATE_INVOICE_NO" to "شمارهٔ فاکتورِ تکراری (فقط اطلاع؛ شمارهٔ قدیم دست نمی‌خورد)",
    "UNIT_FACTOR_NOT_RATIONAL" to "ضریبِ واحدِ فروش با ≤ ۹ رقمِ اعشار نوشته نمی‌شود ⇐ تبدیل ثبت نشد",
    "ORPHAN_PRICE_CHANGE" to "تغییرِ قیمتِ بی کالا ⇐ کنار گذاشته شد",
    "NEGATIVE_PRICE" to "قیمتِ منفی ⇐ کنار گذاشته شد",
    "NEGATIVE_PAYMENT" to "پرداختِ منفی روی فاکتور ⇐ کنار گذاشته شد",
    "PAYMENTS_NOT_EQUAL_TOTAL" to "نقد + نسیهٔ فاکتور با مبلغِ نهایی نمی‌خواند (فقط اطلاع)",
    "ORPHAN_SALE_ITEM" to "قلمِ فروشی که فاکتورش نیست ⇐ کنار گذاشته شد (موجودیِ قدیم آن را فروخته می‌شمرد)",
    "ORPHAN_RETURN" to "مرجوعیِ بی فاکتور/قلمِ معتبر ⇐ کنار گذاشته شد (سودِ قدیم آن را کم می‌کرد)",
    "RETURNED_QTY_MISMATCH" to "«مقدارِ مرجوعی» روی قلم با ردیف‌های مرجوعی نمی‌خواند؛ مدلِ تازه از ردیف‌ها می‌شمارد",
    "CANCEL_WITHOUT_REASON" to "فاکتورِ لغوشدهٔ بی دلیل ⇐ دلیلِ «ثبت نشده» گذاشته شد",
    "ADJUSTMENT_WITHOUT_REASON" to "اصلاحِ موجودیِ بی دلیل ⇐ دلیلِ «ثبت نشده» گذاشته شد",
    "ORPHAN_STOCK_ENTRY" to "ورودِ انبارِ بی کالا ⇐ کنار گذاشته شد",
    "ORPHAN_LEDGER_ENTRY" to "ردیفِ حسابِ قرضِ بی قرض‌دار ⇐ کنار گذاشته شد",
    "NEGATIVE_LEDGER_AMOUNT" to "مبلغِ منفی در حسابِ قرض ⇐ نوعش برعکس شد؛ مانده همان ماند",
    "NEGATIVE_SHIFT_OPENING" to "مبلغِ اولِ صندوقِ منفی ⇐ کنار گذاشته شد",
    "DATE_NOT_ISO" to "تاریخِ فاکتور yyyy-MM-dd نیست ⇐ در هیچ ماهی نمی‌افتد",
    "ONLY_IN_OLD" to "این عدد فقط در محاسبهٔ قدیم هست",
    "ONLY_IN_NEW" to "این عدد فقط در مدلِ تازه هست",
  )

  private val SECTIONS = listOf(
    "rows" to "تعدادِ ردیف‌ها",
    "sales" to "جمعِ فروش و سود (همه)",
    "pay" to "جمعِ پرداخت‌ها",
    "customer" to "ماندهٔ هر مشتری",
    "supplier" to "ماندهٔ هر تأمین‌کننده",
    "stock" to "موجودیِ هر کالا",
    "month" to "فروش و سودِ هر ماهِ خورشیدی",
  )

  /** گروه‌هایی که یک ردیف برای هر کالا/مشتری دارند — بی `all` فقط ناهم‌خوان‌ها چاپ می‌شوند */
  private val PER_ENTITY = setOf("customer", "supplier", "stock")

  fun render(
    r: DryRun.Result,
    input: BackupReader.Opened?,
    all: Boolean = false,
    names: ShopData? = null,
  ): String = buildString {
    val label = labeler(names)
    appendLine("shop-migrate dry-run — گزارشِ تطبیق (اجرای آزمایشی؛ هیچ فایلی نوشته نشد)")
    if (input != null) {
      appendLine("input: format=${input.format} bytes=${input.bytes} sha256=${input.sha256.take(16)}… " +
        "exportedAt=${input.exportedAt ?: "-"} photos=${input.photos}")
    }
    appendLine("months=${r.months}  saleChain=${r.chain.saleLinks}  auditChain=${r.chain.auditLinks}  " +
      "chainCheck=${r.chain.broken?.let { "BROKEN at " + label(it) } ?: "OK"}")
    appendLine("verdicts: EQUAL=${r.count(Verdict.EQUAL)} DOUBLE=${r.count(Verdict.DOUBLE)} " +
      "EXPLAINED=${r.count(Verdict.EXPLAINED)} UNEXPLAINED=${r.count(Verdict.UNEXPLAINED)}")
    appendLine("RESULT: " + if (r.ok) "PASS — هیچ اختلافِ بی‌دلیلی نیست" else "FAIL — اختلافِ بی‌دلیل هست؛ مهاجرت جلو نمی‌رود")

    SECTIONS.forEach { (group, title) ->
      val rows = r.rows.filter { it.key.substringBefore('/') == group }
      if (rows.isEmpty()) return@forEach
      appendLine()
      val tally = Verdict.entries.joinToString(" ") { v -> "${v.name}=${rows.count { it.verdict == v }}" }
      appendLine("## $group — $title (${rows.size}: $tally)")
      rows.filter { all || group !in PER_ENTITY || it.verdict != Verdict.EQUAL }.forEach { row ->
        val old = row.old?.let { Scale.format(it, row.scale) } ?: "-"
        val new = row.new?.let { Scale.format(it, row.scale) } ?: "-"
        val diff = if (row.old != null && row.new != null && row.old != row.new) {
          "  diff=" + Scale.format(row.new - row.old, row.scale)
        } else ""
        val raw = if (row.verdict != Verdict.EQUAL && row.oldRaw != null) "  oldRaw=${row.oldRaw}" else ""
        val why = if (row.reasons.isEmpty()) "" else "  [" + row.reasons.joinToString(",") + "]"
        appendLine("${label(row.key).padEnd(34)} old=$old  new=$new$diff$raw  ${row.verdict}$why")
      }
    }

    appendLine()
    appendLine("## anomalies — ناهنجاری‌های داده (${r.anomalies.size})")
    r.anomalies.groupBy { it.code }.toSortedMap().forEach { (code, list) ->
      val refs = list.take(5).joinToString(" ") { label(refKey(it)) }
      appendLine("${code.padEnd(26)} ${list.size.toString().padStart(5)}  ${CODES[code] ?: ""}")
      appendLine("${"".padEnd(33)}${refs}${if (list.size > 5) " …" else ""}")
    }

    appendLine()
    appendLine("## info — فقط اطلاع (مقایسه نمی‌شود)")
    r.info.forEach { (k, v) -> appendLine("${k.padEnd(26)} $v") }

    appendLine()
    appendLine("## reasons — معنیِ دلیل‌ها")
    r.rows.flatMap { it.reasons }.distinct().sorted().forEach { appendLine("${it.padEnd(26)} ${CODES[it] ?: ""}") }
  }

  private fun refKey(a: Anomaly): String = when {
    a.ref.contains(':') -> a.ref
    else -> "ref:${a.ref}"
  }

  /** «stock/<شناسه>» ⇐ «stock/p:1a2b3c4d5e» یا با `names`: «stock/برنج» */
  private fun labeler(names: ShopData?): (String) -> String {
    val productNames = names?.products?.associate { it.id to it.name }.orEmpty()
    val customerNames = names?.debtors?.associate { it.id to it.name }.orEmpty()
    val supplierNames = names?.suppliers?.associate { it.id to it.name }.orEmpty()
    return { key ->
      val head = key.substringBefore('/')
      val id = key.substringAfter('/', "")
      val sep = key.indexOf(':')
      when {
        head == "stock" -> "stock/" + (productNames[id]?.takeIf { names != null } ?: Ids.anon("p", id))
        head == "customer" -> "customer/" + (customerNames[id]?.takeIf { names != null } ?: Ids.anon("c", id))
        head == "supplier" -> "supplier/" + (supplierNames[id]?.takeIf { names != null } ?: Ids.anon("s", id))
        key.startsWith("sale ") || key.startsWith("auditLog ") -> key.substringBefore(' ') + " " + Ids.anon("x", key.substringAfter(' '))
        sep > 0 && !key.contains('/') -> key.substring(0, sep) + "/" + Ids.anon("x", key.substring(sep + 1))
        else -> key
      }
    }
  }
}
