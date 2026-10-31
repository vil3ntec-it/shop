package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.core.keymap.KeyMap
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.data.CustomerInsight
import ir.vil3ntec.tohid.data.CustomerMerge
import ir.vil3ntec.tohid.data.DebtReminder
import ir.vil3ntec.tohid.data.DocGapsPref
import ir.vil3ntec.tohid.data.DocGapsSheet
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.ReportKeysPref
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.Xlsx
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  گزارش‌ها — کامپیوتر.
 *
 *  هر گزارش یک **جدولِ اکسلی** است — همان برگه‌ای که اکسل و چاپ می‌شود
 *  (`ReportCsv`)، پس آنچه روی صفحه دیده می‌شود با فایل یکی است. بالایش
 *  عددهای کلِ همان بازه. بازه: امروز، ۷ روز، این ماه، ماهِ خورشیدی، سالِ
 *  خورشیدی، همه، یا «از … تا …» با تاریخِ خورشیدی (۱۴۰۵/۰۷/۰۱).
 *
 *  Ctrl+P چاپ، Ctrl+E اکسل — از ثبتِ مرکزیِ کلیدها (`KeyMap.GLOBAL_PRINT` / `GLOBAL_EXPORT`).
 */
/**
 *  `crm`: گزارشِ مشتری — فقط وقتی `ShopPolicy.crmReports` روشن است (پیش‌فرض خاموش).
 *  `gaps`: شماره‌های جاافتادهٔ اسناد (RPT-17) — فقط وقتی `DocGapsPref` روشن است (پیش‌فرض خاموش).
 *  `text`: عنوانِ سه‌زبانه (جدولِ `Str*`) برای زبانه‌های تازه؛ بی آن همان `label`.
 */
private enum class Rep(
  val key: String, val label: String, val ranged: Boolean, val crm: Boolean = false,
  val gaps: Boolean = false, val text: (() -> String)? = null,
) {
  SALES("sales", "فروش", true),
  PROFIT("profit", "سود و زیان", true),
  PURCHASES("purchases", "خرید", true),
  EXPENSES("expenses", "مصارف", true),
  SELLERS("sellers", "فروشِ هر کاربر", true),
  PRODUCTS("products", "کالاها", false),
  DEBTORS("debtors", "قرض‌داران", false),
  STOCK("stock", "گردشِ موجودی", false),
  EXPIRY("expiry", "انقضا", false),
  AGING("aging", "سنِ بدهی", false, crm = true),
  RFM("rfm", "تحلیلِ خریدِ مشتریان", false, crm = true),
  DUPLICATES("duplicates", "مشتریانِ تکراری", false, crm = true),
  REMINDERS("reminders", "یادآوریِ امروز", false, crm = true),
  GAPS("gaps", "", false, gaps = true, text = { StrReports.gapsTab }),
  ;

  val title: String get() = text?.invoke() ?: label
}

private enum class Span(val label: String) { TODAY("امروز"), WEEK("۷ روز"), MONTH("این ماه"), JMONTH("ماهِ خورشیدی"), JYEAR("سالِ خورشیدی"), ALL("همه"), CUSTOM("از … تا …") }

@Composable
fun DeskReportsScreen(d: ShopData, snackbar: SnackbarHostState) {
  val context = LocalContext.current
  //  «گزارشِ تازه» (پیش‌فرض خاموش) ⇒ صفحهٔ هستهٔ مشترکِ گزارش؛ خاموش ⇒ همین صفحهٔ امروز، دست‌نخورده
  ReportFresh.load(context)
  if (ReportFresh.on.value) { DeskFreshReports(d, snackbar); return }
  val scope = rememberCoroutineScope()
  fun toast(t: String) { scope.launch { snackbar.showSnackbar(t) } }
  val today = ir.vil3ntec.tohid.todayIso()
  val j = remember(today) { Jalali.ofIso(today) }

  val crm = remember { ShopPolicy.crmReports(context) }
  val gaps = remember { DocGapsPref.on(context) }
  val reps = remember(crm, gaps) { Rep.values().filter { (crm || !it.crm) && (gaps || !it.gaps) } }
  var rep by remember { mutableStateOf(Rep.SALES) }
  var span by remember { mutableStateOf(Span.MONTH) }
  var fromText by remember { mutableStateOf(j?.let { "%d/%02d/01".format(it.year, it.month) }.orEmpty()) }
  var toText by remember { mutableStateOf(formatDate(today)) }
  //  RPT-13: − / + دوره و Ctrl+Shift+C مقایسه — فقط با `ReportKeysPref` روشن (پیش‌فرض خاموش)
  val keys = remember { ReportKeysPref.on(context) }
  var shift by remember(span) { mutableStateOf(0) }
  var compare by remember { mutableStateOf(false) }

  val (baseFrom, baseTo) = when (span) {
    Span.TODAY -> ReportEngine.rangeOf(ReportEngine.Range.TODAY, today)
    Span.WEEK -> ReportEngine.rangeOf(ReportEngine.Range.WEEK, today)
    Span.MONTH -> ReportEngine.rangeOf(ReportEngine.Range.MONTH, today)
    Span.JMONTH -> ReportEngine.rangeOf(ReportEngine.Range.JMONTH, today, j?.year ?: 1405, j?.month ?: 1)
    Span.JYEAR -> ReportEngine.rangeOf(ReportEngine.Range.JYEAR, today, j?.year ?: 1405)
    Span.ALL -> ReportEngine.rangeOf(ReportEngine.Range.ALL, today)
    Span.CUSTOM -> (Jalali.parseTyped(fromText) ?: "0000-01-01") to (Jalali.parseTyped(toText) ?: "9999-12-31")
  }
  val (from, to) = if (keys && shift != 0) ReportKeys.range(span.name, baseFrom, baseTo, shift, today) else baseFrom to baseTo
  val sheet = remember(d, rep, from, to) { ReportCsv.of(rep.key, d, from, to, today) }

  fun print() = ir.vil3ntec.tohid.print.ReportPrint.print(context, sheet, sheet.name)
  fun excel() { Platform.saveFile("${sheet.name}.xlsx", Xlsx.of(sheet))?.let { toast("ذخیره شد: $it") } }

  Column(
    Modifier
      .fillMaxSize()
      .onPreviewKeyEvent { e ->
        //  کلیدها از ثبتِ مرکزی (`core/keymap/keymap.json`)، نه کدِ کلیدِ دستی
        when {
          e.matches(KeyMap.GLOBAL_PRINT) -> { print(); true }
          e.matches(KeyMap.GLOBAL_EXPORT) -> { excel(); true }
          //  «از … تا …» کادرِ نوشتنِ تاریخ دارد: − و + آن‌جا نوشته می‌شوند، نه دوره
          keys && rep.ranged && span != Span.CUSTOM && e.matches(KeyMap.REPORTS_PREV_PERIOD) -> {
            if (ReportKeys.can(span.name, baseFrom, baseTo, shift - 1, today)) shift -= 1
            true
          }
          keys && rep.ranged && span != Span.CUSTOM && e.matches(KeyMap.REPORTS_NEXT_PERIOD) -> {
            if (ReportKeys.can(span.name, baseFrom, baseTo, shift + 1, today)) shift += 1
            true
          }
          keys && rep.ranged && e.matches(KeyMap.REPORTS_COMPARE) -> { compare = !compare; true }
          else -> false
        }
      }
      .padding(16.dp),
  ) {
    //  نوعِ گزارش
    ScrollableTabRow(selectedTabIndex = reps.indexOf(rep).coerceAtLeast(0), containerColor = Shop.colors.surface, edgePadding = 0.dp) {
      reps.forEach { r -> Tab(selected = rep == r, onClick = { rep = r }, text = { Text(r.title) }) }
    }
    Spacer(Modifier.height(10.dp))

    //  بازه
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (rep.ranged) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
          Span.values().forEach { s ->
            FilterChip(selected = span == s, onClick = { span = s }, label = { Text(s.label) })
            Spacer(Modifier.width(6.dp))
          }
          if (span == Span.CUSTOM) {
            OutlinedTextField(fromText, { fromText = it }, singleLine = true, label = { Text("از") }, modifier = Modifier.width(140.dp))
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(toText, { toText = it }, singleLine = true, label = { Text("تا") }, modifier = Modifier.width(140.dp))
          }
        }
        Text(if (span == Span.ALL) "همهٔ تاریخ‌ها" else "${formatDate(from)} تا ${formatDate(to)}", color = Shop.colors.muted, style = MaterialTheme.typography.bodySmall)
      } else {
        Text(if (rep == Rep.GAPS) StrReports.gapsNow else "وضعیتِ همین حالا — بی بازهٔ تاریخ", Modifier.weight(1f), color = Shop.colors.muted, style = MaterialTheme.typography.bodySmall)
      }
      Spacer(Modifier.width(12.dp))
      ReportFreshSwitch()
      Spacer(Modifier.width(12.dp))
      OutlinedButton(onClick = ::print) { Text("چاپ  ${KeyMap.GLOBAL_PRINT.hint}") }
      Spacer(Modifier.width(6.dp))
      Button(onClick = ::excel) { Text("اکسل  ${KeyMap.GLOBAL_EXPORT.hint}") }
    }
    Spacer(Modifier.height(10.dp))

    //  عددهای کلِ بازه
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      when (rep) {
        Rep.SALES, Rep.PROFIT, Rep.SELLERS -> {
          val r = remember(d, from, to) { ReportEngine.sales(d, from, to) }
          DeskKpi("فروشِ خالص", "${money(r.net)} ؋", Shop.colors.primary, Modifier.weight(1f))
          DeskKpi("فاکتورها", "${r.count}", Shop.colors.text, Modifier.weight(1f))
          DeskKpi("سودِ ناخالص", "${money(r.grossProfit)} ؋", Shop.colors.success, Modifier.weight(1f))
          DeskKpi("مصارف", "${money(r.expenses)} ؋", Shop.colors.danger, Modifier.weight(1f))
          DeskKpi("سودِ خالص", "${money(r.netProfit)} ؋", if (r.netProfit >= 0) Shop.colors.success else Shop.colors.danger, Modifier.weight(1f))
        }
        Rep.PURCHASES -> {
          val r = remember(d, from, to) { ReportEngine.purchases(d, from, to) }
          DeskKpi("جمعِ خرید", "${money(r.total)} ؋", Shop.colors.primary, Modifier.weight(1f))
          DeskKpi("پرداخت‌شده", "${money(r.paid)} ؋", Shop.colors.success, Modifier.weight(1f))
          DeskKpi("به حسابِ تأمین‌کننده", "${money(r.debt)} ؋", Shop.colors.danger, Modifier.weight(1f))
          DeskKpi("بیشترین تأمین‌کننده", r.bySupplier.firstOrNull()?.first ?: "—", Shop.colors.text, Modifier.weight(1f))
        }
        Rep.EXPENSES -> {
          val total = remember(d, from, to) { d.expenses.filter { it.date in from..to }.sumOf { it.amount } }
          val top = remember(d, from, to) { d.expenses.filter { it.date in from..to }.groupBy { it.category.ifBlank { "بی دسته" } }.maxByOrNull { (_, l) -> l.sumOf { it.amount } }?.key }
          DeskKpi("جمعِ مصارف", "${money(total)} ؋", Shop.colors.danger, Modifier.weight(1f))
          DeskKpi("بیشترین دسته", top ?: "—", Shop.colors.text, Modifier.weight(1f))
        }
        Rep.PRODUCTS, Rep.STOCK, Rep.EXPIRY -> {
          val value = remember(d) { d.products.sumOf { ShopStore.stock(d, it.id).coerceAtLeast(0.0) * it.purchasePrice } }
          val out = remember(d) { d.products.count { ShopStore.stockStatus(d, it) == "out" } }
          val low = remember(d) { d.products.count { ShopStore.stockStatus(d, it) == "low" } }
          DeskKpi("ارزشِ انبار (قیمتِ خرید)", "${money(value)} ؋", Shop.colors.primary, Modifier.weight(1f))
          DeskKpi("کالاها", "${d.products.size}", Shop.colors.text, Modifier.weight(1f))
          DeskKpi("تمام‌شده", "$out", Shop.colors.danger, Modifier.weight(1f))
          DeskKpi("کم‌موجود", "$low", Shop.colors.warning, Modifier.weight(1f))
        }
        Rep.DEBTORS -> {
          val total = remember(d) { d.debtors.sumOf { ShopStore.debt(d, it.id).coerceAtLeast(0.0) } }
          DeskKpi("طلبِ کل", "${money(total)} ؋", Shop.colors.danger, Modifier.weight(1f))
          DeskKpi("بدهکاران", "${d.debtors.count { ShopStore.debt(d, it.id) > 0.5 }}", Shop.colors.text, Modifier.weight(1f))
        }
        Rep.AGING -> {
          val a = remember(d, today) { CustomerInsight.aging(d, today) }
          DeskKpi("ماندهٔ کل", "${money(a.balance)} ؋", Shop.colors.danger, Modifier.weight(1f))
          DeskKpi("بیش از ۹۰ روز", "${money(a.totals.lastOrNull() ?: 0.0)} ؋", Shop.colors.warning, Modifier.weight(1f))
          DeskKpi("بدهکاران", "${a.lines.count { it.balance > 0.5 }}", Shop.colors.text, Modifier.weight(1f))
        }
        Rep.RFM -> {
          val rows = remember(d, today) { CustomerInsight.rfm(d, today) }
          fun n(s: CustomerInsight.Segment) = "${rows.count { it.segment == s }}"
          DeskKpi("مشتریانِ خریدار (یک سال)", "${rows.size}", Shop.colors.primary, Modifier.weight(1f))
          DeskKpi(CustomerInsight.Segment.CHAMPION.label, n(CustomerInsight.Segment.CHAMPION), Shop.colors.success, Modifier.weight(1f))
          DeskKpi(CustomerInsight.Segment.AT_RISK.label, n(CustomerInsight.Segment.AT_RISK), Shop.colors.warning, Modifier.weight(1f))
          DeskKpi(CustomerInsight.Segment.LOST.label, n(CustomerInsight.Segment.LOST), Shop.colors.danger, Modifier.weight(1f))
        }
        Rep.DUPLICATES -> {
          val groups = remember(d) { CustomerMerge.duplicates(d) }
          DeskKpi("گروه‌های تکراری", "${groups.size}", Shop.colors.warning, Modifier.weight(1f))
          DeskKpi("با شمارهٔ یکسان", "${groups.count { it.strong }}", Shop.colors.danger, Modifier.weight(1f))
        }
        Rep.GAPS -> {
          val g = remember(d) { DocGapsSheet.report(d) }
          DeskKpi(StrReports.kpiMissing, "${g.missing}", if (g.missing > 0) Shop.colors.danger else Shop.colors.success, Modifier.weight(1f))
          DeskKpi(StrReports.kpiJumped, "${g.jumped}", Shop.colors.text, Modifier.weight(1f))
          DeskKpi(StrReports.kpiDuplicates, "${g.duplicates}", if (g.duplicates > 0) Shop.colors.warning else Shop.colors.text, Modifier.weight(1f))
          DeskKpi(StrReports.kpiCancelled, "${g.cancelled}", Shop.colors.muted, Modifier.weight(1f))
        }
        Rep.REMINDERS -> {
          val due = remember(d, today) { DebtReminder.due(d, today) }
          DeskKpi("یادآوریِ امروز", "${due.size}", Shop.colors.warning, Modifier.weight(1f))
          DeskKpi("سررسید گذشته", "${due.count { it.overdueSince.isNotEmpty() }}", Shop.colors.danger, Modifier.weight(1f))
          DeskKpi("جمعِ مانده", "${money(due.sumOf { it.balance })} ؋", Shop.colors.text, Modifier.weight(1f))
        }
      }
    }
    if (keys && compare && rep.ranged) {
      Spacer(Modifier.height(6.dp))
      ReportCompareLine(d, rep.key, span.name, from, to, today)
    }
    Spacer(Modifier.height(10.dp))
    SheetTable(sheet, Modifier.weight(1f))
  }
}

/**
 *  هر برگهٔ `ReportCsv` به شکلِ جدولِ اکسلی: سطرِ اول سرستون، ستونی که همهٔ
 *  خانه‌هایش عدد است راست‌چین و پهنای ثابت، ستونِ تاریخ به خورشیدی، و ردیفِ
 *  جمع برای ستون‌های عددیِ مبلغ.
 */
@Composable
internal fun SheetTable(sheet: ReportCsv.Sheet, modifier: Modifier) {
  val head = sheet.rows.firstOrNull().orEmpty()
  val body = sheet.rows.drop(1)
  val iso = Regex("""^\d{4}-\d{2}-\d{2}$""")
  val numeric = head.indices.map { c -> body.isNotEmpty() && body.all { r -> r.getOrNull(c).orEmpty().let { it.isEmpty() || it.toDoubleOrNull() != null } } && body.any { it.getOrNull(c).orEmpty().isNotEmpty() } }
  val dates = head.indices.map { c -> body.isNotEmpty() && body.all { r -> r.getOrNull(c).orEmpty().let { it.isEmpty() || iso.matches(it) } } }
  //  جمع فقط برای ستونِ مبلغ — نه شماره، تعداد یا روز
  val noSum = listOf("شماره", "تعداد", "روز", "موجودی", "حداقل", "ردیف", "مقدار", "فی", "قیمت")
  val summable = head.mapIndexed { c, t -> numeric[c] && noSum.none { t.contains(it) } }
  //  ستونِ متنیِ کوتاه (نوع، وضعیت، فروشنده…) پهنای خودش را می‌گیرد تا له نشود؛
  //  فقط متن‌های بلند (نام، شرح) باقیِ جا را میانِ خود قسمت می‌کنند
  val longest = head.indices.map { c -> maxOf(head[c].length, body.maxOfOrNull { it.getOrNull(c).orEmpty().length } ?: 0) }
  val cols = head.mapIndexed { c, t ->
    val w = when {
      dates[c] -> 110.dp
      numeric[c] -> 120.dp
      longest[c] <= 14 -> (longest[c] * 9 + 30).dp
      else -> 0.dp
    }
    TCol(t, w, end = numeric[c])
  }.let { cs -> if (cs.none { it.width == 0.dp } && cs.isNotEmpty()) cs.mapIndexed { i, c -> if (i == cs.indexOfFirst { !it.end }) c.copy(width = 0.dp) else c } else cs }
  val footer = if (summable.any { it } && body.size > 1)
    head.indices.map { c -> if (summable[c]) money(body.sumOf { it.getOrNull(c)?.toDoubleOrNull() ?: 0.0 }) else if (c == 0) "جمع" else "" }
  else null
  DeskTable(
    cols, body.size, modifier,
    empty = "در این بازه چیزی نیست.",
    cell = { i, c ->
      val v = body[i].getOrNull(c).orEmpty()
      when {
        dates[c] && v.isNotEmpty() -> TCell(formatDate(v))
        numeric[c] && v.isNotEmpty() -> TCell(money(v.toDouble()), bold = summable[c])
        else -> TCell(v)
      }
    },
    footer = footer,
  )
}
