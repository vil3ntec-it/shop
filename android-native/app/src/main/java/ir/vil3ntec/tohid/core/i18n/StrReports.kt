package ir.vil3ntec.tohid.core.i18n

/**
 *  گزارش‌ها — نوشته‌های تازهٔ صفحهٔ «گزارش‌ها» (از RPT-17: شماره‌های جاافتادهٔ اسناد).
 *
 *  قاعده‌ها سرِ `I18n.kt`. ترجمه‌های نامطمئن در `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 */
@Suppress("unused")
object StrReports {

  // تنظیمات ← شماره‌های جاافتاده (فقط کامپیوتر)
  val docGaps: String get() = t("شماره‌های جاافتادهٔ اسناد", "Missing document numbers", "د سندونو ورکې شمېرې")
  val docGapsOn: String get() = t("زبانهٔ «شماره‌های جاافتاده» در «گزارش‌ها»", "“Missing numbers” tab under “Reports”", "په «راپورونه» کې د «ورکې شمېرې» پاڼه")
  val docGapsNote: String get() = t(
    "یک زبانهٔ تازه در «گزارش‌ها»: برای هر سریِ فاکتورِ فروش و برای فاکتورهای خرید، شماره‌هایی که میانِ نخستین و آخرین سند نیستند. جای خالی‌ای که شمارنده پس از همگام‌سازی با دستگاهِ دیگر جلو پریده «پرشِ شمارنده» نام دارد و جاافتاده نیست؛ بقیه «جاافتاده»اند: سندی پاک شده، هنوز از دستگاهِ دیگری نرسیده یا دفتر از پشتیبانِ کهنه برگشته. شمارهٔ تکراری و فاکتورِ لغوشده (که شماره‌اش را نگه می‌دارد) هم شمرده می‌شوند. فقط خواندنی است و چیزی را عوض نمی‌کند. در صفحهٔ فعلیِ گزارش‌ها می‌آید، نه در «گزارشِ تازه». پس از تغییر، «گزارش‌ها» را دوباره باز کنید.",
    "A new tab under “Reports”: for each sales invoice series and for purchase invoices, the numbers missing between the first and the last document. A gap left when the counter moved ahead after syncing with another device is a “counter jump”, not a missing number; every other gap is “missing”: a document was deleted, has not arrived from another device yet, or the ledger was restored from an old backup. Duplicate numbers and cancelled invoices (which keep their number) are counted too. It is read-only and changes nothing. It appears on the current reports screen, not in “New report”. After changing this, reopen “Reports”.",
    "په «راپورونه» کې یوه نوې پاڼه: د پلور د بل هرې لړۍ او د پېرود بلونو لپاره، هغه شمېرې چې د لومړي او وروستي سند ترمنځ نشته. هغه تشه چې شمېرونکی له بلې وسیلې سره له همغږۍ وروسته مخکې ټوپ کړی «د شمېرونکي ټوپ» بلل کېږي او ورکه نه ده؛ نورې ټولې «ورکې» دي: کوم سند ړنګ شوی، لا له بلې وسیلې نه دی راغلی یا دفتر له زاړه بیک‌اپ څخه بېرته راغلی. تکراري شمېرې او لغوه شوي بلونه (چې خپله شمېره ساتي) هم شمېرل کېږي. یوازې د لوستلو دی او هېڅ نه بدلوي. په اوسني راپورونو پاڼه کې راځي، نه په «نوی راپور» کې. له بدلون وروسته «راپورونه» بیا پرانیزئ.",
  )

  // زبانه و کارت‌ها
  val gapsTab: String get() = t("شماره‌های جاافتاده", "Missing numbers", "ورکې شمېرې")
  val gapsNow: String get() = t("همهٔ اسنادِ شماره‌دارِ دفتر — بی بازهٔ تاریخ", "All numbered documents in the ledger — no date range", "د دفتر ټول شمېره‌لرونکي سندونه — بې نېټې")
  val kpiMissing: String get() = t("شمارهٔ جاافتاده", "Missing numbers", "ورکې شمېرې")
  val kpiJumped: String get() = t("پرشِ شمارنده", "Counter jumps", "د شمېرونکي ټوپونه")
  val kpiDuplicates: String get() = t("شمارهٔ تکراری", "Duplicate numbers", "تکراري شمېرې")
  val kpiCancelled: String get() = t("لغوشده (شماره دارد)", "Cancelled (numbered)", "لغوه شوي (شمېره لري)")

  // برگه (سرستون و خانه‌ها)
  val colKind: String get() = t("نوعِ سند", "Document type", "د سند ډول")
  val colSeries: String get() = t("سری", "Series", "لړۍ")
  val colFrom: String get() = t("از شمارهٔ", "From no.", "له شمېرې")
  val colTo: String get() = t("تا شمارهٔ", "To no.", "تر شمېرې")
  val colCount: String get() = t("تعداد", "Count", "شمېر")
  val colStatus: String get() = t("وضعیت", "Status", "حالت")
  val colBefore: String get() = t("روزِ سندِ پیش", "Date of document before", "د مخکیني سند نېټه")
  val colAfter: String get() = t("روزِ سندِ پس", "Date of document after", "د وروسته سند نېټه")
  val kindSale: String get() = t("فاکتورِ فروش", "Sales invoice", "د پلور بل")
  val kindPurchase: String get() = t("فاکتورِ خرید", "Purchase invoice", "د پېرود بل")
  val noSeries: String get() = t("بی سری", "No series", "بې لړۍ")
  val statusMissing: String get() = t("جاافتاده", "Missing", "ورکه")
  fun statusJump(series: String): String = t("پرشِ شمارنده (سریِ $series)", "Counter jump (series $series)", "د شمېرونکي ټوپ (لړۍ $series)")
  fun statusDuplicate(n: Int): String = t("تکراری: $n سند با یک شماره", "Duplicate: $n documents with one number", "تکراري: $n سندونه په یوه شمېره")
  fun moreGaps(n: Int): String = t("… و $n جای خالیِ دیگر (شمارها بالا کامل‌اند)", "… and $n more gaps (the totals above are complete)", "… او $n نورې تشې (پورته شمېرې بشپړې دي)")
  val sheetName: String get() = t("شماره‌های-جاافتاده", "missing-numbers", "ورکې-شمېرې")
}
