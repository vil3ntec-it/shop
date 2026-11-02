package ir.vil3ntec.tohid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.core.hw.Discovery
import ir.vil3ntec.tohid.core.hw.FoundPrinter
import ir.vil3ntec.tohid.core.hw.NetScan
import ir.vil3ntec.tohid.core.hw.ScannerDetect
import ir.vil3ntec.tohid.core.hw.ScannerSheet
import ir.vil3ntec.tohid.core.hw.Subnet
import ir.vil3ntec.tohid.hw.LocalNetworkAccess
import ir.vil3ntec.tohid.hw.PrinterFinderPlatform
import ir.vil3ntec.tohid.print.HwPages
import ir.vil3ntec.tohid.print.HwTools
import ir.vil3ntec.tohid.print.NetPrinterScan
import ir.vil3ntec.tohid.print.PrinterRegistry
import ir.vil3ntec.tohid.print.ScannerSheetPrint
import ir.vil3ntec.tohid.print.ThermalPrinter
import ir.vil3ntec.tohid.scan.ScanPrefs
import ir.vil3ntec.tohid.scan.scanChar
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 *  HW-12 — «راه‌اندازیِ آسان» در «دستگاه‌ها»، فقط با `EasySetupPref` ِ روشن (پیش‌فرض خاموش ⇒ هیچ‌کدام
 *  رسم نمی‌شود و صفحهٔ چاپگر و بارکدخوان همان امروز است). منطق در `core/hw` (EasySetupTest)؛ سوکت در
 *  `print/EasySetupAndroid.kt`، کارتِ شبکه و بلوتوث در `hw/PrinterFinderAndroid.kt` (کامپیوتر: `PrinterFinderDesktop.kt`).
 *  روی چاپگر و بارکدخوانِ واقعی آزموده نشده.
 */

/* ============================ پیدا کردنِ خودکارِ چاپگر ============================ */

private fun viaLabel(f: FoundPrinter): String = when (f.via) {
  FoundPrinter.Via.NETWORK -> "شبکه · درگاهِ ۹۱۰۰ باز است"
  FoundPrinter.Via.BT_PAIRED -> "بلوتوث · جفت‌شده"
  FoundPrinter.Via.BT_NEARBY -> "بلوتوث · نزدیک، هنوز جفت نشده"
} + if (f.printerLike) "" else " · شاید چاپگر نباشد"

/** پنجرهٔ «پیدا کردنِ خودکار»: شبکه (۹۱۰۰) و بلوتوث؛ هر یافته «چاپِ شناسایی» و «افزودن» دارد */
@Composable
internal fun PrinterFinderDialog(onDismiss: () -> Unit, onAdded: (String) -> Unit, toast: (String) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var width by remember { mutableStateOf(ThermalPrinter.WIDTH_58MM) }
  var saved by remember { mutableStateOf(PrinterRegistry.list(context).map { it.link }) }
  var found by remember {
    mutableStateOf(Discovery.merge(emptyList(), ThermalPrinter.paired(context).map {
      FoundPrinter("bt:${it.address}", it.name, FoundPrinter.Via.BT_PAIRED, printerLike = ir.vil3ntec.tohid.core.hw.BtClass.nameLooksLikePrinter(it.name))
    }))
  }
  fun add(list: List<FoundPrinter>) { found = Discovery.merge(found, list) }

  //  شبکه: فقط کارتِ وای‌فای/LAN ِ خصوصی، حداکثر دو کارت (هر کدام تا ۲۵۴ نشانی)
  val lans = remember { Subnet.usable(PrinterFinderPlatform.lanInterfaces(context)).take(2) }
  var progress by remember { mutableStateOf<NetScan.Progress?>(null) }
  var netJob by remember { mutableStateOf<Job?>(null) }
  val netBlocked = lans.any { LocalNetworkAccess.blocked(context, it.ip) }

  //  بلوتوث: اجازه فقط با زدنِ دکمه
  var btBusy by remember { mutableStateOf(false) }
  var btMessage by remember { mutableStateOf<String?>(null) }
  fun startBt() {
    if (btBusy) return
    btBusy = true
    btMessage = "جست‌وجوی بلوتوث… چاپگر را روشن و نزدیک نگه دارید"
    scope.launch {
      val before = found.size
      val err = PrinterFinderPlatform.bluetoothNearby(context, PrinterFinderPlatform.BLUETOOTH_SCAN_MS) { add(listOf(it)) }
      btBusy = false
      btMessage = err ?: if (found.size > before) "جست‌وجوی بلوتوث تمام شد" else
        "دستگاهِ بلوتوثیِ تازه‌ای پیدا نشد — چاپگر روشن است و دکمهٔ جفت‌سازی‌اش را زده‌اید؟"
    }
  }
  val askBt = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
    if (r.values.all { it } && PrinterFinderPlatform.bluetoothGranted(context)) startBt()
    else btMessage = "بی اجازهٔ «دستگاه‌های نزدیک» جست‌وجوی بلوتوث ممکن نیست"
  }

  Dialog(onDismissRequest = { netJob?.cancel(); onDismiss() }) {
    DialogEntry {
      Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
          Text("پیدا کردنِ خودکارِ چاپگر", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
          Spacer(Modifier.height(6.dp))
          Text("عرضِ کاغذِ چاپگری که افزوده می‌شود (بعداً در «تنظیم» هم عوض می‌شود):",
            style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
          SegmentedChoice(
            options = listOf(ThermalPrinter.WIDTH_58MM.toString() to "۵۸ میلی‌متر", ThermalPrinter.WIDTH_80MM.toString() to "۸۰ میلی‌متر"),
            selected = width.toString(),
            onSelect = { width = it.toIntOrNull() ?: ThermalPrinter.WIDTH_58MM },
          )

          /* ---------------- شبکه ---------------- */
          Spacer(Modifier.height(14.dp))
          Text("شبکه (وای‌فای / LAN)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text)
          if (lans.isEmpty()) {
            Text("این دستگاه به وای‌فای یا شبکهٔ سیمیِ خصوصی وصل نیست؛ جست‌وجوی شبکه فقط زیرشبکهٔ خودِ دکان را می‌گردد.",
              style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
          } else {
            Text(lans.joinToString(" · ") { "${it.ip}/${it.prefix}" } + " — فقط همین زیرشبکه، درگاهِ ۹۱۰۰؛ چیزی به چاپگر فرستاده نمی‌شود.",
              style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
            if (netBlocked) LocalNetworkNotice(context)
            val running = progress?.finished == false
            OutlinedButton(enabled = !running && !netBlocked, onClick = {
              val hosts = lans.flatMap { Subnet.hosts(it.ip, it.prefix) }.distinct()
              progress = NetScan.Progress(hosts.size, 0, 0)
              netJob = scope.launch {
                NetPrinterScan.scan(hosts) { p, hits ->
                  scope.launch { progress = p; add(hits.map { FoundPrinter.network(it) }) }
                }
              }
            }) { Text(if (running) "در حالِ جست‌وجو…" else "جست‌وجوی شبکه") }
            progress?.let { p ->
              if (!p.finished && p.total > 0) LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
              Text(p.message, style = MaterialTheme.typography.bodySmall,
                color = if (p.finished && p.found == 0) Shop.colors.warning else Shop.colors.text)
            }
          }

          /* ---------------- بلوتوث ---------------- */
          Spacer(Modifier.height(14.dp))
          Text("بلوتوث", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text)
          if (PrinterFinderPlatform.bluetoothScan()) {
            OutlinedButton(enabled = !btBusy, onClick = {
              if (PrinterFinderPlatform.bluetoothGranted(context)) startBt()
              else askBt.launch(PrinterFinderPlatform.bluetoothPermissions().toTypedArray())
            }) { Text(if (btBusy) "در حالِ جست‌وجو…" else "جست‌وجوی بلوتوثِ نزدیک (۱۲ ثانیه)") }
            if (btBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            btMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted) }
          } else {
            Text("چاپگرِ بلوتوثی را در تنظیماتِ بلوتوثِ سیستم جفت کنید؛ درگاهِ COM ِ آن در فهرستِ پایین می‌آید.",
              style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
          }

          /* ---------------- یافته‌ها ---------------- */
          Spacer(Modifier.height(14.dp))
          Text("یافته‌ها", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text)
          if (found.isEmpty()) Text("هنوز چیزی پیدا نشده.", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
          found.forEach { f ->
            val known = Discovery.known(f, saved)
            Column(
              Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Shop.colors.surface2).padding(10.dp),
            ) {
              Text(f.name, fontWeight = FontWeight.Bold, color = Shop.colors.text, style = MaterialTheme.typography.bodyMedium)
              Text("${viaLabel(f)} · ${PrinterRegistry.label(f.link)}", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
              Row(verticalAlignment = Alignment.CenterVertically) {
                if (f.via != FoundPrinter.Via.BT_NEARBY) TextButton(onClick = {
                  toast("چاپِ شناسایی به «${f.name}» رفت…")
                  scope.launch { toast(NetPrinterScan.identify(context, f.link) ?: "چاپِ شناسایی انجام شد — نشانی روی کاغذ است") }
                }) { Text("چاپِ شناسایی") }
                Spacer(Modifier.weight(1f))
                if (known) Text("ثبت شده", style = MaterialTheme.typography.labelSmall, color = Shop.colors.success)
                else TextButton(onClick = {
                  val mac = f.link.removePrefix("bt:")
                  if (f.via == FoundPrinter.Via.BT_NEARBY) PrinterFinderPlatform.bond(context, mac)
                  PrinterRegistry.add(context, f.name, f.link, width)
                  saved = PrinterRegistry.list(context).map { it.link }
                  onAdded(f.name)
                }) { Text(if (f.via == FoundPrinter.Via.BT_NEARBY) "جفت کردن و افزودن" else "افزودن") }
              }
            }
          }
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { netJob?.cancel(); onDismiss() }) { Text("بستن") }
          }
        }
      }
    }
  }
}

/* ============================ بارکدخوان ============================ */

/** دکمه‌های «برگهٔ بارکدخوان» و «آزمونِ بارکدخوان» زیرِ بخشِ بارکدخوان‌ها */
@Composable
internal fun ScannerSetupTools(canAdmin: Boolean, toast: (String) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var testing by remember { mutableStateOf(false) }
  var preview by remember { mutableStateOf<HwPages.Preview?>(null) }
  Text("راه‌اندازیِ آسانِ بارکدخوان", style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
  Text("برگهٔ بارکدِ آزمون را چاپ کنید (یا از روی صفحه اسکن کنید) و «آزمونِ بارکدخوان» را بزنید؛ پیشوند، پسوند و Enter خودکار پیدا می‌شود.",
    style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(onClick = { testing = true }) { Text("آزمونِ بارکدخوان") }
    OutlinedButton(onClick = {
      val s = PrinterRegistry.defaultSaved(context)
      val w = s?.profile()?.paperDots ?: ThermalPrinter.WIDTH_58MM
      preview = runCatching { HwPages.previewEscPos(ScannerSheetPrint.bytes(context, s ?: PrinterRegistry.savedFor(context, "", w)), w) }
        .getOrElse { toast("برگه کشیده نشد: ${it.message}"); null }
    }) { Text("پیش‌نمایشِ برگه") }
  }
  OutlinedButton(onClick = {
    toast("برگهٔ بارکدخوان به چاپگرِ پیش‌فرض رفت…")
    scope.launch { toast(ScannerSheetPrint.print(context) ?: "برگهٔ بارکدخوان چاپ شد") }
  }) { Text("چاپِ برگهٔ بارکدخوان") }
  preview?.let { p -> PrintPreviewDialog("برگهٔ راه‌اندازیِ بارکدخوان", p) { preview = null } }
  if (testing) ScannerDetectDialog(canAdmin, onDismiss = { testing = false }, toast = toast)
}

@Composable
private fun ScannerDetectDialog(canAdmin: Boolean, onDismiss: () -> Unit, toast: (String) -> Unit) {
  val context = LocalContext.current
  LaunchedEffect(Unit) { ScanPrefs.load(context); HwTools.load(context) }
  val typed = remember { StringBuilder() }
  var lastAt by remember { mutableStateOf(0L) }
  var result by remember { mutableStateOf<ScannerDetect.Result?>(null) }
  var field by remember { mutableStateOf("") }
  val focus = remember { FocusRequester() }
  val code = remember { ScannerSheet.build().codes.first() }
  val bitmap = remember {
    code.modules()?.let { ThermalPrinter.toBitmap(ScannerSheetPrint.barcode(it, ThermalPrinter.WIDTH_58MM, 110)) }
  }
  fun finish(enter: Boolean) {
    if (typed.isEmpty()) return
    result = ScannerDetect.detect(typed.toString(), enter)
    typed.setLength(0)
  }
  //  بارکدخوانِ بی Enter: پس از سکوتِ کوتاه همان چیزی که رسید سنجیده می‌شود
  LaunchedEffect(lastAt) {
    if (lastAt == 0L) return@LaunchedEffect
    delay(ScannerDetect.QUIET_MS)
    finish(enter = false)
  }
  LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
      Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
          Text("آزمونِ بارکدخوان", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
          Spacer(Modifier.height(6.dp))
          Text("این بارکد را (از روی برگه یا همین صفحه) اسکن کنید؛ برنامه می‌بیند بارکدخوان پیش و پس از «${code.data}» چه می‌فرستد.",
            style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
          Spacer(Modifier.height(8.dp))
          bitmap?.let {
            //  بی زمینهٔ ثابت: خودِ تصویر سفید/سیاهِ کدر است (HardcodedColorsTest)
            Image(it.asImageBitmap(), contentDescription = "بارکدِ آزمون", Modifier.fillMaxWidth().heightIn(max = 140.dp),
              contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
          }
          Spacer(Modifier.height(8.dp))
          OutlinedTextField(
            value = field,
            onValueChange = { field = it.takeLast(64) },
            label = { Text("این‌جا بزنید و اسکن کنید") },
            singleLine = false, maxLines = 2,
            modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
              if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
              if (e.key == Key.Enter || e.key == Key.NumPadEnter) {
                finish(enter = true); lastAt = 0L; field = ""
                return@onPreviewKeyEvent true
              }
              val ch = e.scanChar(allowSpace = true) ?: return@onPreviewKeyEvent false
              if (typed.isEmpty()) result = null
              if (typed.length < 64) typed.append(ch)
              lastAt = System.currentTimeMillis()
              false
            },
          )
          result?.let { r ->
            Spacer(Modifier.height(6.dp))
            Text(r.message, color = if (r.ok) Shop.colors.success else Shop.colors.danger, style = MaterialTheme.typography.bodyMedium)
            val now = ScanPrefs.saved.value
            if (r.ok && !ScannerDetect.changes(now, r)) {
              Text("تنظیمِ کنونیِ بارکدخوانِ همین دستگاه همین است.", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
            } else if (r.ok && canAdmin) {
              OutlinedButton(onClick = {
                ScanPrefs.save(context, ScannerDetect.apply(now, r))
                toast("پیشوند و پسوندِ بارکدخوان ذخیره شد")
              }) { Text("ذخیرهٔ همین پیشوند و پسوند") }
            } else if (r.ok) {
              Text("ذخیرهٔ تنظیم فقط برای صاحب و مدیرِ دکان است.", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
            }
            if (r.ok && !HwTools.on.value) {
              Text("پیشوند و پسوندِ ذخیره‌شده وقتی به کار می‌رود که «ابزارهای تازهٔ دستگاه» (بالای همین صفحه) روشن باشد.",
                style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning)
            }
          }
          Spacer(Modifier.height(4.dp))
          Text("کدِ تنظیمِ سازنده‌ها در برنامه نیست (برای هر سازنده جداست و کدِ حدسی ساخته نمی‌شود)؛ Enter و پیشوند را از دفترچهٔ خودِ بارکدخوان تنظیم کنید.",
            style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDismiss) { Text("بستن") } }
        }
      }
    }
  }
}
