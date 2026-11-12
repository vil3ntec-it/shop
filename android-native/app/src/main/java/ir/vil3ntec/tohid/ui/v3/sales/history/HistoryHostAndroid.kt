package ir.vil3ntec.tohid.ui.v3.sales.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.hw.PrintBatch
import ir.vil3ntec.tohid.core.hw.PrintQueue
import ir.vil3ntec.tohid.core.hw.PrinterProfile
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.receipt.ReceiptDoc
import ir.vil3ntec.tohid.core.receipt.ReceiptTokens
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.ReceiptProfileStore
import ir.vil3ntec.tohid.data.ReportBridge
import ir.vil3ntec.tohid.data.ReturnFlow
import ir.vil3ntec.tohid.data.SalesPeriods
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.displayDate
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.print.PeriodPrint
import ir.vil3ntec.tohid.print.PrintSpooler
import ir.vil3ntec.tohid.print.PrinterRegistry
import ir.vil3ntec.tohid.print.Receipts
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.screens.ReturnsShot
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerModel
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 *  درزِ سکوی تاریخچه: نقش و سیاستِ دکان، ساعتِ گزارش‌ها، چاپگرِ فاکتور و صفِ چاپ. هرچه حساب است در
 *  `SalesPeriods`، `HistoryBook` و `PeriodSlip` (کدِ مشترک) است.
 */

/** `sales` — تاریخچهٔ روز/ماه/سال؛ دفترِ تاریخچه در پس‌زمینه برای هر تغییرِ دفتر ساخته می‌شود */
@Composable
internal fun RouteScope.HistoryHost() {
  val context = LocalContext.current
  val v2 = returnsV2()
  val canCancel = remember { ShopRole.canCancelSale(context) }
  val askCancel = remember { ir.vil3ntec.tohid.data.CancelAsk.offered(context) } // STF-02: لغو با برگهٔ مدیر (Sync v3)
  val blind = remember { ShopPolicy.payV2(context) && ShopPolicy.blindHere(context) } // PAY-13: شمارشِ کورِ اختیاریِ دکان
  val query = rememberSaveable(saver = HistoryQuerySaver) {
    HistoryQuery(HistoryFilter.of(args.query("filter")), args.query("q").orEmpty(), SalesPeriods.Unit.DAY, HistoryChannel.of(args.query("where")))
  }
  //  نشانِ همین دکان در QRِ فاکتورِ نسخهٔ ۲ (`ReceiptTokens.shopTag`)
  val shopTag = remember { ReceiptTokens.shopTag(LedgerOwner.currentShop(context)) }
  //  عکسِ CI (`DevShot` «ret»): فرمِ برگشتِ تازه‌ترین فاکتورِ زنده، یا برگشتِ بی رسید
  val shot = ReturnsShot.open.value
  LaunchedEffect(shot) {
    when (shot) {
      "first" -> data.sales.filter { HistoryModel.canReturn(data, it) }.maxByOrNull { it.createdAt }?.let { nav.open(SalesPaths.returnOf(it.id)) }
      "none" -> nav.open(SalesPaths.RETURN_NONE)
    }
  }
  //  تقویمِ ماه و سال همان تنظیمِ برنامه؛ عوض شدنش دفترِ تاریخچه را از نو می‌سازد
  val gregorian = HistoryBook.gregorianNow()
  val d = data
  val book by produceState<HistoryBook?>(null, d, gregorian) {
    val built = withContext(Dispatchers.Default) {
      runCatching { HistoryBook.build(d, ReportBridge.deviceClock(), ReportBridge.nowMs(), gregorian) }
    }
    built.exceptionOrNull()?.let { toasts.show(it.message ?: Str.unknownReason, V3Tone.ERROR) }
    built.getOrNull()?.let { value = it }
  }
  var printing by remember { mutableStateOf<SalesPeriods.Period?>(null) }
  val onDesk = desk

  RejectedWatch(data = d)
  HistoryScreen(
    d = d,
    book = book,
    returnsV2 = v2,
    canCancel = canCancel,
    askCancel = askCancel,
    drawer = DrawerModel.line(d, ReportBridge.nowMs(), blind),
    query = query,
    actions = HistoryActions(
      open = { nav.open(SalesPaths.invoice(it)) },
      ret = { nav.open(SalesPaths.returnOf(it)) },
      cancel = { nav.open(SalesPaths.cancelOf(it)) },
      fix = { nav.open(SalesPaths.fixOf(it)) },
      returnReceipt = { nav.open(SalesPaths.returnReceipt(it)) },
      drawer = { nav.open(SalesPaths.DRAWER) },
      noReceipt = if (v2) ({ nav.open(SalesPaths.RETURN_NONE) }) else null,
      //  گوشی: QRِ رسید با دوربین؛ کامپیوتر: اسکنرِ دستی همین کادر را پر می‌کند
      scanReceipt = if (v2 && !Platform.DESKTOP) ({
        nav.openForResult(SalesPaths.scan(SalesPaths.SCAN_RECEIPT)) { code ->
          if (code != null) {
            query.search = code
            if (ReturnFlow.isForeignReceipt(code, shopTag)) toasts.show(ReturnFlow.FOREIGN_RECEIPT, V3Tone.WARNING)
            //  یک فاکتورِ زنده ⇒ همان لحظه فرمِ برگشت
            HistoryModel.scanned(data, code, shopTag)?.let { nav.open(SalesPaths.returnOf(it.id)) }
          }
        }
      }) else null,
      newSale = { nav.switchTab(SalesPaths.SALE) },
      back = backOrNull,
      payReview = { nav.open(SalesPaths.PAY_REVIEW) },
      print = { printing = it },
      table = if (onDesk) ({ nav.open(SalesPaths.TABLE) }) else null,
    ),
    bottomReserve = bottomReserve,
    shopTag = shopTag,
    reviews = reviewCount(d),
    money = !blind,
    banner = printBanner(),
  )

  val b = book
  printing?.let { p ->
    if (b != null) HistoryPrintSheet(
      b, p, money = !blind,
      onDevices = { nav.open(SettingsPaths.DEVICES) },
      onClose = { printing = null },
      toast = { toasts.show(it) },
      filtered = query.filtered,
    )
  }
}

private val HistoryQuerySaver = listSaver<HistoryQuery, String>(
  save = { listOf(it.filter.id, it.search, it.unit.name, it.channel.id) },
  restore = { HistoryQuery(HistoryFilter.of(it[0]), it[1], SalesPeriods.Unit.entries.firstOrNull { u -> u.name == it.getOrNull(2) } ?: SalesPeriods.Unit.DAY, HistoryChannel.of(it.getOrNull(3))) },
)

/** نوارِ چاپِ تاریخچه از حالِ `PeriodPrint` و صفِ چاپ (منتظرِ چاپگر ⇒ همان پیام) */
@Composable
private fun printBanner(): PrintBanner? {
  val p = PeriodPrint.progress.value ?: return null
  val waiting = PrintSpooler.status.value.let { it is PrintQueue.Status.Retrying || it is PrintQueue.Status.NeedsUser }
  val text = when (p.phase) {
    PrintBatch.Phase.RUNNING -> p.title + "\n" + StrHistory.printing(plain(p.printed), plain(p.total)) + if (waiting) "\n" + StrHistory.waitingPrinter else ""
    PrintBatch.Phase.DONE -> p.title + " — " + StrHistory.printed
    PrintBatch.Phase.CANCELLED -> StrHistory.printCancelled(plain(p.printed), plain(p.total))
    PrintBatch.Phase.FAILED -> StrHistory.printFailed(p.error ?: Str.unknownReason)
  }
  return PrintBanner(text, p.running, onCancel = PeriodPrint::cancel, onDismiss = PeriodPrint::dismiss)
}

/**
 *  برگهٔ «چاپ» ِ یک دوره: همهٔ فاکتورها (پیش‌فرض، خواستِ صاحب‌کار: «همهٔ فاکتورهای روز/ماه/سال یک‌جا») یا خلاصه، با شمار
 *  و کاغذِ تخمینی و نامِ چاپگر و پهنای کاغذ؛ تأیید ⇐ `PeriodPrint` (صفِ چاپ، در پس‌زمینه). سندها هم در پس‌زمینه ساخته
 *  می‌شوند؛ تا آماده‌اند دکمهٔ چاپ منتظر است. [filtered] ⇒ یک سطر که فیلترِ فهرست در چاپ نیست (چاپ همیشه همهٔ دوره است).
 *  [demoPrinter] فقط عکسِ CI (`SalesShots`)؛ برنامه همیشه چاپگرِ پیش‌فرضِ همین دستگاه را می‌گیرد.
 */
@Composable
internal fun HistoryPrintSheet(
  book: HistoryBook,
  p: SalesPeriods.Period,
  money: Boolean,
  onDevices: () -> Unit,
  onClose: () -> Unit,
  toast: (String) -> Unit,
  filtered: Boolean = false,
  demoPrinter: PrinterRegistry.Saved? = null,
) {
  val context = LocalContext.current
  val c = PhoneV3.colors
  val printer = remember { demoPrinter ?: runCatching { PrinterRegistry.defaultSaved(context) }.getOrNull() }
  val title = PeriodLabel.full(p, book.gregorian)
  V3Sheet(onDismiss = onClose, title = StrHistory.printTitle(title), detents = false) { closer ->
    if (printer == null) {
      Text(StrHistory.noPrinter, style = V3Type.body, color = c.ink, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
      Spacer(Modifier.height(8.dp))
      V3Button(Str.devices, { closer.close { onClose(); onDevices() } }, Modifier.padding(horizontal = 16.dp), icon = Icons.Outlined.Print)
      Spacer(Modifier.height(12.dp))
      return@V3Sheet
    }
    val profile = printer.profile()
    val width = profile.paperDots
    var all by remember { mutableStateOf(p.sales.isNotEmpty()) }
    val head = remember(p, title) {
      val shop = runCatching { ReceiptProfileStore.current(context).name }.getOrDefault("").ifBlank { Receipts.localName(context) }
      val range = if (p.unit == SalesPeriods.Unit.DAY || p.from.isEmpty()) null else displayDate(p.from) + " — " + displayDate(p.to)
      PeriodSlip.Head(shop, title, range, SaleTime.stamp(ReportBridge.nowMs()))
    }
    val docs by produceState<Pair<List<ReceiptDoc>, List<ReceiptDoc>>?>(null, p, width, money) {
      value = withContext(Dispatchers.Default) {
        val fmt = Receipts.format()
        listOf(PeriodSlip.summary(p, head, width, fmt, money)) to
          PeriodSlip.parts(PeriodSlip.sections(p, head, PeriodSlip.source(book.look), width, fmt, money), width)
      }
    }
    val chosen = docs?.let { if (all) it.second else it.first }
    if (p.sales.isNotEmpty()) Option(StrHistory.allInvoices, StrHistory.allInvoicesHint, Icons.AutoMirrored.Outlined.ReceiptLong, selected = all) { all = true }
    Option(StrHistory.summary, StrHistory.summaryHint, Icons.Outlined.Summarize, selected = !all) { all = false }
    val count = if (all) p.sales.size else p.totals.invoices
    val cm = chosen?.let { PeriodSlip.paperCm(it, profile.feedLines) }
    Text(
      StrHistory.paperLine(plain(count), cm?.let(::plain) ?: "…"), style = V3Type.headline, color = c.ink,
      modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
    )
    val mm = if (width >= PrinterProfile.WIDTH_80MM) 80 else 58
    Text(StrHistory.printerLine(printer.name, plain(mm)), style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    if ((cm ?: 0) > LONG_CM) Text(StrHistory.longPaper, style = V3Type.footnote, color = c.negative, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    if (filtered && all) Text(StrHistory.printIgnoresFilter(plain(count)), style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    Spacer(Modifier.height(14.dp))
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button(Str.cancel, { closer.close(onClose) }, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY)
      V3Button(
        if (all) StrHistory.printInvoices(plain(count)) else StrHistory.printSummary,
        {
          val list = chosen ?: return@V3Button
          PeriodPrint.start(context, printer, StrHistory.printTitle(title), list)?.let(toast)
          closer.close(onClose)
        },
        Modifier.weight(1f), icon = Icons.Outlined.Print, enabled = chosen != null, loading = chosen == null,
      )
    }
    Spacer(Modifier.height(12.dp))
  }
}

/** بیش از سه متر ⇒ هشدارِ کاغذ */
private const val LONG_CM = 300

@Composable
private fun Option(title: String, hint: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
  V3ListRow(
    title = title,
    subtitle = hint,
    icon = icon,
    hue = if (selected) V3Hue.BLUE else null,
    trailing = if (selected) ({ Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = PhoneV3.colors.accent) }) else null,
    chevron = false,
    onClick = onClick,
  )
}
