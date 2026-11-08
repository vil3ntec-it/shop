package ir.vil3ntec.tohid.print

import android.content.Context
import android.text.Layout
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.hw.Barcodes
import ir.vil3ntec.tohid.core.hw.EasyTestPrint
import ir.vil3ntec.tohid.core.hw.EscPos
import ir.vil3ntec.tohid.core.hw.MonoImage
import ir.vil3ntec.tohid.core.hw.NetScan
import ir.vil3ntec.tohid.core.hw.ScannerSheet
import ir.vil3ntec.tohid.core.i18n.StrPrint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 *  HW-12 — «راه‌اندازیِ آسانِ دستگاه‌ها»: تنظیمِ **همین دستگاه** (`tohid-ui` ⇐ `easy_setup`)، **پیش‌فرض خاموش**،
 *  همگام نمی‌شود. روشن ⇒ در «دستگاه‌ها»: «پیدا کردنِ خودکارِ چاپگر» (شبکه و بلوتوث) و «برگهٔ بارکدخوان» و
 *  «آزمونِ بارکدخوان». خاموش ⇒ صفحهٔ چاپگر و بارکدخوان همان امروز است (هیچ دکمه و جست‌وجویی نیست).
 *  منطق در `core/hw/PrinterDiscovery.kt` و `ScannerSetup.kt` (EasySetupTest)؛ روی دستگاهِ واقعی آزموده نشده.
 */
object EasySetupPref {
  private const val PREFS = "tohid-ui"
  const val KEY = "easy_setup"

  val on = mutableStateOf(false)
  private var loaded = false

  fun load(context: Context) {
    if (loaded) return
    loaded = true
    on.value = runCatching { context.getSharedPreferences(PREFS, 0).getBoolean(KEY, false) }.getOrDefault(false)
  }

  fun set(context: Context, value: Boolean) {
    loaded = true
    on.value = value
    runCatching { context.getSharedPreferences(PREFS, 0).edit().putBoolean(KEY, value).apply() }
  }
}

/**
 *  جست‌وجوی درگاهِ ۹۱۰۰ روی نشانی‌هایی که `Subnet.hosts` داده (فقط زیرشبکهٔ خصوصیِ همان کارت).
 *  دسته‌به‌دسته (`NetScan.batches`، حداکثر ۳۲ سوکتِ هم‌زمان) با مهلتِ هر وصل؛ **فقط وصل شدن — هیچ بایتی
 *  نوشته نمی‌شود** و سوکت همان دم بسته می‌شود. لغوِ کار (بستنِ پنجره) پس از همان دسته می‌ایستد.
 */
object NetPrinterScan {

  fun open(host: String, port: Int, timeoutMs: Int): Boolean = runCatching {
    Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs); true }
  }.getOrDefault(false)

  suspend fun scan(
    hosts: List<String>,
    port: Int = NetScan.PORT,
    timeoutMs: Int = NetScan.CONNECT_TIMEOUT_MS,
    onProgress: (NetScan.Progress, List<String>) -> Unit = { _, _ -> },
  ): List<String> = withContext(Dispatchers.IO) {
    val found = ArrayList<String>()
    var done = 0
    if (hosts.isEmpty()) onProgress(NetScan.Progress(0, 0, 0, finished = true), emptyList())
    for (batch in NetScan.batches(hosts)) {
      ensureActive()
      val hits = coroutineScope { batch.map { h -> async { h.takeIf { open(it, port, timeoutMs) } } }.awaitAll() }.filterNotNull()
      found += hits
      done += batch.size
      onProgress(NetScan.Progress(hosts.size, done, found.size, finished = done == hosts.size), found.toList())
    }
    found
  }

  /** «چاپِ شناسایی» (`EasyTestPrint`): متنِ ASCII با نشانی، بی صفِ چاپ */
  suspend fun identify(context: Context, link: String): String? = withContext(Dispatchers.IO) {
    val l = ThermalPrinter.Link.parse(link) ?: return@withContext StrPrint.badAddress
    ThermalPrinter.send(context, l, EasyTestPrint.bytes(l.save()))
  }
}

/** «برگهٔ راه‌اندازیِ بارکدخوان» روی کاغذِ حرارتی — محتوا از `ScannerSheet.build()` */
object ScannerSheetPrint {

  private const val QUIET = 10

  /** بارکد به پهنای کاغذ: ماژولِ ۱ تا ۳ نقطه، هر چه جا بدهد */
  fun barcode(modules: BooleanArray, paperDots: Int, heightDots: Int = 90): MonoImage {
    val dots = (paperDots / (modules.size + QUIET * 2)).coerceIn(1, 3)
    return Barcodes.render(modules, dots, heightDots, QUIET)
  }

  fun image(context: Context, paperDots: Int): MonoImage {
    val sheet = ScannerSheet.build()
    val w = paperDots
    fun lines(vararg l: HwPages.Line) = HwPages.text(context, w, l.toList())
    val right = Layout.Alignment.ALIGN_NORMAL
    val parts = ArrayList<MonoImage>()
    parts += lines(HwPages.Line(sheet.title, HwPages.Style.TITLE))
    parts += HwPages.text(context, w, sheet.steps.map { HwPages.Line(it, HwPages.Style.SMALL, right) })
    fun code(c: ScannerSheet.Code) {
      val m = c.modules() ?: return
      parts += lines(HwPages.Line(c.label, HwPages.Style.HEAD))
      parts += barcode(m, w)
      parts += lines(HwPages.Line(c.data, HwPages.Style.BODY))
    }
    sheet.codes.forEach(::code)
    sheet.vendors.forEach { v ->
      parts += lines(HwPages.Line(v.brand, HwPages.Style.HEAD), HwPages.Line(v.source, HwPages.Style.SMALL))
      v.codes.forEach(::code)
    }
    parts += lines(HwPages.Line(sheet.note, HwPages.Style.SMALL, right))
    return HwPages.stack(w, parts, gap = 10)
  }

  fun bytes(context: Context, s: PrinterRegistry.Saved): ByteArray {
    val p = s.profile()
    return EscPos.document(image(context, p.paperDots), p)
  }

  /** چاپ روی چاپگرِ پیش‌فرضِ رسید، بی صف؛ نبودِ چاپگر یا خطا ⇒ پیام */
  suspend fun print(context: Context): String? = withContext(Dispatchers.IO) {
    val s = PrinterRegistry.defaultSaved(context) ?: return@withContext StrPrint.noReceiptPrinter
    val bytes = runCatching { bytes(context, s) }.getOrElse { return@withContext StrPrint.sheetNotDrawn(it.message) }
    val link = ThermalPrinter.Link.parse(s.link) ?: return@withContext StrPrint.badAddress
    ThermalPrinter.send(context, link, bytes)
  }
}
