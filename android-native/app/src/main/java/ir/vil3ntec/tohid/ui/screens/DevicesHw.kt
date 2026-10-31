package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.core.hw.NciScale
import ir.vil3ntec.tohid.core.hw.PrintQueue
import ir.vil3ntec.tohid.core.hw.ReceiptCopies
import ir.vil3ntec.tohid.core.hw.WeightBarcode
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ScaleLabels
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.hw.CustomerFacing
import ir.vil3ntec.tohid.hw.HwPlatform
import ir.vil3ntec.tohid.hw.ScaleReader
import ir.vil3ntec.tohid.print.HwPages
import ir.vil3ntec.tohid.print.PrintLog
import ir.vil3ntec.tohid.print.PrintSpooler
import ir.vil3ntec.tohid.print.ReceiptCopiesPref
import ir.vil3ntec.tohid.print.PrinterRegistry
import ir.vil3ntec.tohid.print.ThermalPrinter
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.SegmentTab
import ir.vil3ntec.tohid.ui.components.common.SegmentedTabs
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 *  دستگاه‌ها (HW-01..04، HW-09..11) — بخش‌های تازهٔ صفحهٔ «دستگاه‌های جانبی»:
 *  صفِ چاپ و نشانِ وضعیت، پروفایلِ هر چاپگر با پیش‌نمایش و بررسیِ اتصال،
 *  برچسبِ کالا، ترازو و نمایشگرِ مشتری.
 *
 *  هیچ دستگاهِ ساختگی‌ای نیست: هر دکمه یا کارِ واقعی می‌کند (چاپ، خواندن،
 *  پیش‌نمایشِ بایت‌های واقعی) یا نیست و دلیلش نوشته شده است.
 */

/* ============================== نشانِ وضعیتِ چاپ ============================== */

/** «آماده · در حالِ چاپ · تلاشِ دوباره · چاپگر در دسترس نیست» — متن + رنگ + نقطه */
@Composable
fun PrinterStatusPill(modifier: Modifier = Modifier) {
  val s by PrintSpooler.status
  val (text, tone) = when (val st = s) {
    PrintQueue.Status.Idle -> "چاپ: آماده" to PillTone.SUCCESS
    is PrintQueue.Status.Busy -> "در حالِ چاپ${waiting(st.waiting)}" to PillTone.NEUTRAL
    is PrintQueue.Status.Retrying -> "تلاشِ دوبارهٔ چاپ (${LocaleText.digitsIn(st.job.attempts.toString(), LocaleChoice.digits)})${waiting(st.waiting)}" to PillTone.WARNING
    is PrintQueue.Status.NeedsUser -> "چاپگر در دسترس نیست${waiting(st.waiting)}" to PillTone.DANGER
  }
  StatusPill(text, tone, modifier)
}

private fun waiting(n: Int) = if (n > 0) " · ${LocaleText.digitsIn(n.toString(), LocaleChoice.digits)} در صف" else ""

/* ============================== صفِ چاپ ============================== */

/**
 *  عکس‌گیریِ خودکارِ کامپیوتر (`DevShot`، hwshot:receipt|label|a4|close) —
 *  همان پیش‌نمایش‌هایی که دکمه‌ها باز می‌کنند، بی کلیک.
 */
object HwShot {
  val preview = mutableStateOf<String?>(null)
}

/** پیش‌نمایشِ بی چاپگر: برگهٔ آزمون با پروفایلِ چاپگرِ پیش‌فرض، یا ۸۰ میلی‌مترِ معمولی */
private fun dryRunPrinter(context: android.content.Context): PrinterRegistry.Saved =
  PrinterRegistry.defaultSaved(context) ?: PrinterRegistry.Saved("dry", "بی چاپگر", "", ThermalPrinter.WIDTH_80MM)

private fun dryRun(context: android.content.Context, what: String): Pair<String, HwPages.Preview>? = when (what) {
  "receipt" -> {
    val s = dryRunPrinter(context)
    "پیش‌نمایشِ برگهٔ آزمون — بی کاغذ" to HwPages.previewEscPos(HwPages.testBytes(context, s), s.profile().paperDots)
  }
  "label" -> HwPages.sampleLabel(context)?.let { "پیش‌نمایشِ برچسبِ ۴۰×۳۰" to HwPages.Preview(ThermalPrinter.toBitmap(it), listOf("label ${it.width}x${it.height}"), emptyList()) }
  "a4" -> HwPages.sampleLabel(context)?.let {
    val sheet = ir.vil3ntec.tohid.core.hw.Labels.A4.sheet(it, 24)
    "پیش‌نمایشِ برگهٔ A4 (۳×۸)" to HwPages.Preview(ThermalPrinter.toBitmap(sheet), listOf("a4 ${sheet.width}x${sheet.height}"), emptyList())
  }
  else -> null
}

/**
 *  `canAdmin` (`settings.write`): «چاپِ خودکار» و «چاپِ معوق» فقط برای صاحب و مدیر.
 *  `canDrawer` (`drawer.open`): «باز کردنِ کشو» بی فروش — با دلیل و ردیفِ `no_sale`.
 */
@Composable
internal fun PrintQueuePanel(toast: (String) -> Unit, canAdmin: Boolean = false, canDrawer: Boolean = false) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  LaunchedEffect(Unit) { PrintSpooler.start(context) }
  val jobs by PrintSpooler.jobs
  val lastError by PrintSpooler.lastError
  var auto by remember { mutableStateOf(PrintSpooler.autoPrint(context)) }
  var deferred by remember { mutableStateOf(PrintSpooler.deferred(context)) }
  var copies by remember { mutableIntStateOf(ReceiptCopiesPref.get(context)) }
  var tick by remember { mutableIntStateOf(0) }
  val reprints = remember(tick, jobs.size) { PrintLog.reprints(context, 5) }
  var dry by remember { mutableStateOf<Pair<String, HwPages.Preview>?>(null) }
  val shot by HwShot.preview
  LaunchedEffect(shot) {
    dry = shot?.let { w -> withContext(Dispatchers.IO) { runCatching { dryRun(context, w) }.getOrNull() } }
  }

  Panel {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("صفِ چاپ", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text)
      PrinterStatusPill()
    }
    Text("فروش همیشه ثبت می‌شود؛ رسیدی که چاپ نشد این‌جا به ترتیب می‌ماند و دوباره تلاش می‌شود.",
      style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    if (jobs.isNotEmpty() && lastError != null) {
      Spacer(Modifier.height(Space.xs))
      Text("آخرین خطا: $lastError", style = MaterialTheme.typography.bodySmall, color = Shop.colors.danger)
    }
    jobs.forEachIndexed { i, j ->
      Row(
        Modifier.fillMaxWidth().padding(top = Space.xs).clip(Shape.card).background(Shop.colors.surface2).padding(Space.xs),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f)) {
          Text(j.title + if (j.kind == PrintQueue.Kind.REPRINT) " — چاپِ دوباره" else "", style = MaterialTheme.typography.bodyMedium,
            color = Shop.colors.text, fontWeight = FontWeight.SemiBold)
          Text(
            listOfNotNull(
              PrinterRegistry.label(j.link),
              if (j.attempts > 0) "${LocaleText.digitsIn(j.attempts.toString(), LocaleChoice.digits)} تلاش" else null,
              j.lastError,
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
          )
        }
        if (i == 0) TextButton(onClick = { PrintSpooler.retryNow() }) { Text("تلاشِ دوباره") }
        TextButton(onClick = { PrintSpooler.discard(j.id); toast("از صف برداشته شد — فروش دست نخورد") }) {
          Text("کنار گذاشتن", color = Shop.colors.danger)
        }
      }
    }
    Spacer(Modifier.height(Space.xs))
    if (canAdmin) {
      HwSwitchRow("چاپِ خودکارِ فاکتور پس از هر فروش", "روی چاپگرِ پیش‌فرض. خاموش = مثلِ همیشه فقط با دکمهٔ «چاپ».", auto) {
        auto = it; PrintSpooler.setAutoPrint(context, it)
      }
      //  POS-35: شمارِ نسخهٔ همین دستگاه؛ ۱ = همان یک رسیدِ امروز. نسخهٔ دوم به بعد «نسخهٔ k از n» دارد
      if (auto) {
        Text("تعدادِ نسخهٔ چاپِ خودکار", style = MaterialTheme.typography.labelLarge, color = Shop.colors.text)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          ReceiptCopies.plan(ReceiptCopies.MAX).forEach { n ->
            TohidFilterChip(LocaleText.digitsIn(n.toString(), LocaleChoice.digits), selected = copies == n, onClick = {
              copies = n; ReceiptCopiesPref.set(context, n)
            })
          }
        }
        Text("نسخهٔ دوم به بعد روی کاغذ «نسخهٔ ۲ از …» دارد تا با اصل اشتباه نشود.",
          style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
      }
      HwSwitchRow("چاپِ معوق", "روشن: تا چاپگر برگردد خودش هر دقیقه تلاش می‌کند. خاموش: پس از چند تلاش می‌ایستد و می‌پرسد.", deferred) {
        deferred = it; PrintSpooler.setDeferred(context, it)
      }
    } else {
      Text("چاپِ خودکار: ${if (auto) "روشن" + (if (copies > 1) " (${LocaleText.digitsIn(copies.toString(), LocaleChoice.digits)} نسخه)" else "") else "خاموش"} · چاپِ معوق: ${if (deferred) "روشن" else "خاموش"} — تنظیمش با صاحب یا مدیرِ دکان است.",
        style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
    //  HW-03: کشو بی فروش — فقط با مجوز، با دلیل، و همیشه در سابقهٔ دکان
    val drawerPrinter = remember(tick) { PrinterRegistry.defaultSaved(context)?.takeIf { it.drawer } }
    var openDrawer by remember { mutableStateOf(false) }
    if (drawerPrinter != null) {
      Spacer(Modifier.height(Space.xs))
      OutlinedButton(enabled = canDrawer, onClick = { openDrawer = true }) { Text("باز کردنِ کشو") }
      if (!canDrawer) Text("باز کردنِ کشو بی فروش فقط برای صاحب و مدیر است.", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
    if (openDrawer && drawerPrinter != null) OpenDrawerDialog(drawerPrinter, onDismiss = { openDrawer = false }, toast = toast)
    OutlinedButton(onClick = {
      scope.launch { dry = withContext(Dispatchers.IO) { runCatching { dryRun(context, "receipt") }.getOrElse { toast("پیش‌نمایش ساخته نشد: ${it.message}"); null } } }
    }) { Text("پیش‌نمایشِ برگهٔ آزمون (بی چاپگر)") }
    if (reprints.isNotEmpty()) {
      Spacer(Modifier.height(Space.xs))
      Text("چاپ‌های دوبارهٔ اخیر", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text)
      reprints.forEach { e ->
        Text("${ir.vil3ntec.tohid.formatMillis(e.at)} — ${e.reason}", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
      }
    }
  }
  DryRunDialog(dry) { dry = null; HwShot.preview.value = null }
}

@Composable
private fun DryRunDialog(dry: Pair<String, HwPages.Preview>?, onClose: () -> Unit) {
  dry?.let { (title, p) -> PrintPreviewDialog(title, p, onClose) }
}

@Composable
private fun HwSwitchRow(title: String, note: String, on: Boolean, onChange: (Boolean) -> Unit) {
  Row(Modifier.fillMaxWidth().padding(vertical = Space.xxs), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
      Text(note, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
    Switch(checked = on, onCheckedChange = onChange)
  }
}

/* ============================== پروفایلِ چاپگر ============================== */

/**
 *  تنظیمِ یک چاپگر: نقش (رسید/برچسب)، کاغذ، راهِ تصویر، برش، کشو، نشان.
 *  «پیش‌نمایش» همان بایت‌های برگهٔ آزمون را می‌خواند و روی صفحه می‌کشد —
 *  بی چاپگر هم می‌شود دید چه بیرون می‌آید.
 */
@Composable
internal fun PrinterSettingsDialog(
  initial: PrinterRegistry.Saved,
  onDismiss: () -> Unit,
  onSaved: () -> Unit,
  toast: (String) -> Unit,
  canDrawer: Boolean = false,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var s by remember { mutableStateOf(initial) }
  var preview by remember { mutableStateOf<HwPages.Preview?>(null) }
  var busy by remember { mutableStateOf(false) }
  var probe by remember { mutableStateOf<String?>(null) }
  var pickLogo by remember { mutableStateOf(false) }
  var hasLogo by remember { mutableStateOf(HwPages.hasLogo(context)) }
  var drawerTest by remember { mutableStateOf(false) }

  fun work(label: String, block: suspend () -> String?) {
    busy = true
    scope.launch {
      val msg = runCatching { withContext(Dispatchers.IO) { block() } }.getOrElse { "خطا: ${it.message}" }
      busy = false
      toast(msg ?: label)
    }
  }

  Dialog(onDismissRequest = onDismiss) {
    Column(
      Modifier.widthIn(max = 520.dp).clip(Shape.dialog).background(Shop.colors.surfaceSolid)
        .verticalScroll(rememberScrollState()).padding(Space.md),
    ) {
      Text("تنظیمِ «${initial.name}»", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text)
      Text("${PrinterRegistry.kind(s.link)} · ${PrinterRegistry.label(s.link)}", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
      Spacer(Modifier.height(Space.sm))

      HwLabel("کارِ این چاپگر")
      SegmentedTabs(listOf(SegmentTab(PrinterRegistry.ROLE_RECEIPT, "رسید"), SegmentTab(PrinterRegistry.ROLE_LABEL, "برچسبِ کالا")), s.role, { s = s.copy(role = it) })

      if (s.isLabel) {
        HwLabel("زبانِ چاپگرِ برچسب")
        SegmentedTabs(listOf(SegmentTab(PrinterRegistry.LANG_TSPL, "TSPL (TSC، Xprinter)"), SegmentTab(PrinterRegistry.LANG_ESCPOS, "ESC/POS")), s.labelLang,
          { s = s.copy(labelLang = it) })
        Text("برچسبِ ۴۰×۳۰ میلی‌متر، ۲۰۳ نقطه در اینچ. نام و قیمت به‌صورتِ تصویر چاپ می‌شوند.",
          style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
      } else {
        HwLabel("عرضِ کاغذ")
        SegmentedTabs(listOf(SegmentTab(ThermalPrinter.WIDTH_58MM, "۵۸ میلی‌متر"), SegmentTab(ThermalPrinter.WIDTH_80MM, "۸۰ میلی‌متر")),
          if (s.width >= ThermalPrinter.WIDTH_80MM) ThermalPrinter.WIDTH_80MM else ThermalPrinter.WIDTH_58MM, { s = s.copy(width = it) })
        HwLabel("راهِ فرستادنِ تصویر")
        SegmentedTabs(listOf(SegmentTab("raster", "رستری GS v 0 (پیش‌فرض)"), SegmentTab("column", "ستونی ESC *")), s.mode, { s = s.copy(mode = it) })
        Text("اگر چاپگر به‌جای رسید نویسه‌های بی‌معنا زد، «ستونی» را بزنید.", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        HwLabel("برشِ کاغذ")
        SegmentedTabs(listOf(SegmentTab("partial", "نیمه"), SegmentTab("full", "کامل"), SegmentTab("none", "بی‌تیغه")), s.cut, { s = s.copy(cut = it) })
        HwSwitchRow("باز کردنِ کشوی پول پس از فروشِ نقدی", "از راهِ چاپگر (ESC p). فروشِ نسیه کشو را باز نمی‌کند.", s.drawer) { s = s.copy(drawer = it) }
        if (s.drawer) {
          SegmentedTabs(listOf(SegmentTab(0, "پینِ ۲ (رایج)"), SegmentTab(1, "پینِ ۵")), s.drawerPin, { s = s.copy(drawerPin = it) })
        }
        HwSwitchRow("نشانِ دکان بالای رسید", if (hasLogo) "نشان انتخاب شده است" else "اول یک تصویر انتخاب کنید", s.logo) { s = s.copy(logo = it) }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
          OutlinedButton(onClick = { pickLogo = true }) { Text(if (hasLogo) "عوض کردنِ نشان" else "انتخابِ نشان") }
          if (hasLogo) TextButton(onClick = { HwPages.removeLogo(context); hasLogo = false; s = s.copy(logo = false) }) {
            Text("برداشتنِ نشان", color = Shop.colors.danger)
          }
        }
      }

      Spacer(Modifier.height(Space.sm))
      probe?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Shop.colors.text) }
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        OutlinedButton(enabled = !busy, onClick = {
          scope.launch {
            preview = withContext(Dispatchers.IO) {
              runCatching {
                if (s.isLabel) {
                  val img = HwPages.label(context, "برچسبِ آزمون", LocaleText.afghani(120.0, LocaleChoice.digits, LocaleChoice.symbol), HwPages.SAMPLE_EAN).image!!
                  HwPages.Preview(ThermalPrinter.toBitmap(img), listOf("label ${img.width}x${img.height}"), emptyList())
                } else HwPages.previewEscPos(HwPages.testBytes(context, s), s.profile().paperDots)
              }.getOrElse { toast("پیش‌نمایش ساخته نشد: ${it.message}"); null }
            }
          }
        }) { Text("پیش‌نمایش") }
        OutlinedButton(enabled = !busy, onClick = {
          busy = true
          scope.launch {
            val r = withContext(Dispatchers.IO) {
              ThermalPrinter.Link.parse(s.link)?.let { ThermalPrinter.probe(context, it) }
            }
            busy = false
            probe = r?.message ?: "نشانیِ چاپگر خوانده نشد"
          }
        }) { Text("بررسیِ اتصال") }
        //  آزمونِ کشو هم کشو را بی فروش باز می‌کند ⇒ همان «باز کردنِ کشو»: مجوز، دلیل، سابقه
        if (!s.isLabel && s.drawer && canDrawer) OutlinedButton(enabled = !busy, onClick = { drawerTest = true }) { Text("آزمونِ کشو") }
        OutlinedButton(enabled = !busy, onClick = { work("برگهٔ آزمون چاپ شد") { PrinterRegistry.testPrint(context, s) } }) { Text("برگهٔ آزمون") }
      }
      Spacer(Modifier.height(Space.sm))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss) { Text("انصراف") }
        Button(onClick = { PrinterRegistry.update(context, s); onSaved() }) { Text("ذخیره") }
      }
    }
  }

  PhotoSourceSheet(open = pickLogo, onDismiss = { pickLogo = false }, note = "نشانِ سیاه‌وسفید بهتر چاپ می‌شود") { uri ->
    pickLogo = false
    val err = HwPages.saveLogo(context, uri)
    if (err == null) { hasLogo = true; s = s.copy(logo = true) } else toast(err)
  }
  preview?.let { PrintPreviewDialog("پیش‌نمایشِ چاپ — بی کاغذ", it) { preview = null } }
  if (drawerTest) OpenDrawerDialog(s, initialReason = "آزمونِ کشو", onDismiss = { drawerTest = false }, toast = toast)
}

/**
 *  «باز کردنِ کشو» بی فروش (HW-03): دلیل اجباری، یک ضربه بیرون از صف و یک
 *  ردیفِ `no_sale` در سابقهٔ دکان (`PrintSpooler.openDrawer`). مجوز را صدازننده سنجیده.
 */
@Composable
internal fun OpenDrawerDialog(printer: PrinterRegistry.Saved, initialReason: String = "", onDismiss: () -> Unit, toast: (String) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var reason by remember { mutableStateOf(initialReason) }
  var busy by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text("باز کردنِ کشو بی فروش") },
    text = {
      Column {
        Text("در سابقهٔ دکان ثبت می‌شود: چه کسی، کی، از کدام دستگاه و چرا.",
          style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
        Spacer(Modifier.height(Space.xs))
        OutlinedTextField(value = reason, onValueChange = { reason = it.take(80) }, singleLine = true,
          label = { Text("دلیل") }, modifier = Modifier.fillMaxWidth())
      }
    },
    confirmButton = {
      Button(enabled = !busy && reason.isNotBlank(), onClick = {
        busy = true
        scope.launch {
          val msg = PrintSpooler.openDrawer(context, printer, reason)
          busy = false
          onDismiss()
          toast(msg)
        }
      }) { Text("باز کردنِ کشو") }
    },
    dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("انصراف") } },
  )
}

@Composable
private fun HwLabel(text: String) {
  Spacer(Modifier.height(Space.sm))
  Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text)
  Spacer(Modifier.height(Space.xxs))
}

/**
 *  «چاپ روی کاغذِ خیالی»: تصویری که از **خودِ بایت‌ها** خوانده شده،
 *  با رویدادها (برش، کشو…) — اگر ESC/POS خراب باشد این‌جا هم خراب است.
 */
@Composable
internal fun PrintPreviewDialog(title: String, p: HwPages.Preview, onDismiss: () -> Unit) {
  Dialog(onDismissRequest = onDismiss) {
    Column(Modifier.widthIn(max = 520.dp).clip(Shape.dialog).background(Shop.colors.surfaceSolid).padding(Space.md)) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text)
      Spacer(Modifier.height(Space.xs))
      Box(
        Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())
          //  بی زمینهٔ ثابت: خودِ تصویرِ کاغذ سفید/سیاهِ کدر است (HardcodedColorsTest)
          .clip(Shape.card).border(1.dp, Shop.colors.border.copy(alpha = 0.4f), Shape.card),
      ) {
        val bmp = p.bitmap
        if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = "پیش‌نمایشِ کاغذ", Modifier.fillMaxWidth(),
          contentScale = ContentScale.FillWidth, filterQuality = FilterQuality.None)
        else Text("چیزی برای چاپ نیست", Modifier.padding(Space.md), color = Shop.colors.muted)
      }
      Spacer(Modifier.height(Space.xs))
      Text("دستورها: " + groupEvents(p.events).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
      if (p.unknown.isNotEmpty()) Text("بایتِ ناشناخته: ${p.unknown.take(5).joinToString()}", style = MaterialTheme.typography.labelSmall, color = Shop.colors.danger)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDismiss) { Text("بستن") } }
    }
  }
}

/** «column 384x24 ×۱۹» به‌جای نوزده بار همان؛ سطرهای خالی شمرده نمی‌شوند */
private fun groupEvents(events: List<String>): List<String> {
  val out = ArrayList<Pair<String, Int>>()
  events.filterNot { it == "feed" }.forEach { e ->
    val last = out.lastOrNull()
    if (last != null && last.first == e) out[out.size - 1] = e to last.second + 1 else out += e to 1
  }
  return out.map { (e, n) -> if (n > 1) "$e ×${LocaleText.digitsIn(n.toString(), LocaleChoice.digits)}" else e }
}

/* ============================== برچسبِ کالا ============================== */

@Composable
internal fun LabelsPanel(d: ShopData, toast: (String) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var term by remember { mutableStateOf("") }
  var picked by remember { mutableStateOf<Product?>(null) }
  var copies by remember { mutableIntStateOf(1) }
  var preview by remember { mutableStateOf<HwPages.Preview?>(null) }
  val printer = remember(d.products.size) { PrinterRegistry.labelPrinter(context) }
  val matches = remember(term, d.products) {
    val t = ir.vil3ntec.tohid.normalizeDigits(term.trim())
    if (t.isEmpty()) emptyList()
    else d.products.filter { it.active && (it.name.contains(t) || it.barcodes.any { b -> b == t }) }.take(6)
  }

  fun build(p: Product): HwPages.LabelResult =
    HwPages.label(context, p.name, LocaleText.afghani(p.salePrice, LocaleChoice.digits, LocaleChoice.symbol), p.barcodes.firstOrNull().orEmpty())

  Panel {
    Text("برچسبِ کالا", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text)
    Text("۴۰×۳۰ میلی‌متر: نامِ دری، قیمت و بارکدِ همان کالا (EAN-13 یا Code128).", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    Spacer(Modifier.height(Space.xs))
    OutlinedTextField(term, { term = it.take(40); picked = null }, singleLine = true, label = { Text("نام یا بارکدِ کالا") }, modifier = Modifier.fillMaxWidth())
    matches.forEach { p ->
      Text(p.name + (p.barcodes.firstOrNull()?.let { " · $it" } ?: " · بی‌بارکد"),
        Modifier.fillMaxWidth().clip(Shape.card).clickable { picked = p; term = p.name }
          .background(if (picked?.id == p.id) Shop.colors.primaryTint else Shop.colors.surface2).padding(Space.xs),
        style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
      Spacer(Modifier.height(Space.xxs))
    }
    picked?.let { p ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("تعداد:", style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
        TextButton(onClick = { copies = (copies - 1).coerceAtLeast(1) }) { Text("−") }
        Text(LocaleText.digitsIn(copies.toString(), LocaleChoice.digits), fontWeight = FontWeight.Bold, color = Shop.colors.text)
        TextButton(onClick = { copies = (copies + 1).coerceAtMost(99) }) { Text("+") }
      }
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        OutlinedButton(onClick = {
          scope.launch {
            val r = withContext(Dispatchers.IO) { build(p) }
            val img = r.image
            if (img == null) toast(r.error ?: "برچسب ساخته نشد")
            else preview = HwPages.Preview(ThermalPrinter.toBitmap(img), listOf("label ${img.width}x${img.height}"), emptyList())
          }
        }) { Text("پیش‌نمایش") }
        Button(enabled = printer != null, onClick = {
          val pr = printer
          if (pr != null) scope.launch {
            val r = withContext(Dispatchers.IO) { build(p) }
            val img = r.image ?: run { toast(r.error ?: "برچسب ساخته نشد"); return@launch }
            toast(PrintSpooler.submitAndWait(context, PrintQueue.Kind.LABEL, pr.link, "برچسبِ ${p.name}", HwPages.labelBytes(pr, img, copies), done = "برچسب چاپ شد"))
          }
        }) { Text("چاپِ برچسب") }
        OutlinedButton(onClick = {
          scope.launch {
            val r = withContext(Dispatchers.IO) { build(p) }
            val img = r.image ?: run { toast(r.error ?: "برچسب ساخته نشد"); return@launch }
            val sheet = withContext(Dispatchers.Default) { ir.vil3ntec.tohid.core.hw.Labels.A4.sheet(img, copies) }
            ir.vil3ntec.tohid.print.LabelSheetPrint.print(context, sheet, "برچسبِ ${p.name}")
          }
        }) { Text("برگهٔ A4") }
      }
      Text("برگهٔ A4: ۳ ستون × ۸ ردیف، تا ۲۴ برچسب در یک برگ؛ در پنجرهٔ چاپ «اندازهٔ واقعی / بی حاشیه» را بزنید.",
        style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
      if (printer == null) Text("هنوز چاپگری «برچسبِ کالا» تعریف نشده — در «چاپگرها» یکی را تنظیم کنید.",
        style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning)
    }
  }
  preview?.let { PrintPreviewDialog("پیش‌نمایشِ برچسب", it) { preview = null } }
}

/* ============================== ترازو ============================== */

@Composable
internal fun ScalePanel(d: ShopData, toast: (String) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var rules by remember { mutableStateOf(ScaleLabels.rules(context)) }
  var test by remember { mutableStateOf("") }
  val result = remember(test, rules, d.products) {
    if (test.isBlank()) null
    else when (val m = ScaleLabels.match(rules, test, ShopStore.barcodeIndex(d), d.products)) {
      null -> "برچسبِ ترازو نیست (یا قاعده خاموش است) — بارکدِ معمولی خوانده می‌شود"
      is ScaleLabels.Match.Rejected -> m.message
      is ScaleLabels.Match.Weighed -> "«${m.product.name}» — ${ir.vil3ntec.tohid.qty(m.quantity)} ${m.product.unit}"
    }
  }
  Panel {
    Text("ترازو", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text)
    HwSwitchRow(
      "برچسبِ ترازو در صندوق",
      "بارکدِ ۱۳ رقمیِ ۲۰ تا ۲۹: کدِ کالای ترازو ۵ رقم و وزن ۵ رقم به گرم. کدِ ترازو را به بارکدهای همان کالا بیفزایید.",
      rules.isNotEmpty(),
    ) { on ->
      val next = if (on) WeightBarcode.standardRules() else emptyList()
      ScaleLabels.saveRules(context, next); rules = next
    }
    OutlinedTextField(test, { test = it.take(20) }, singleLine = true, label = { Text("آزمون: یک برچسبِ ترازو این‌جا اسکن کنید") },
      modifier = Modifier.fillMaxWidth())
    result?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Shop.colors.text) }

    Spacer(Modifier.height(Space.sm))
    val serial = HwPlatform.serial
    if (serial == null) {
      Text("ترازوی سریال (NCI) فقط در برنامهٔ کامپیوتر است.", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    } else {
      val prefs = remember { context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE) }
      var ports by remember { mutableStateOf(emptyList<ir.vil3ntec.tohid.hw.SerialPorts.Port>()) }
      var port by remember { mutableStateOf(prefs.getString("scale_port", "") ?: "") }
      var reading by remember { mutableStateOf<String?>(null) }
      LaunchedEffect(Unit) { ports = withContext(Dispatchers.IO) { serial.list() } }
      Text("ترازوی سریال — پروتکلِ ${NciScale.title}، ۹۶۰۰ و 7E1. روی ترازوی واقعی آزموده نشده است.",
        style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
      if (ports.isEmpty()) Text("درگاهِ سریالی پیدا نشد.", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
      ports.forEach { pt ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          RadioButton(selected = port == pt.name, onClick = { port = pt.name; prefs.edit().putString("scale_port", pt.name).apply() })
          Text("${pt.name} — ${pt.title}", style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
        }
      }
      OutlinedButton(enabled = port.isNotBlank(), onClick = {
        scope.launch {
          reading = when (val r = withContext(Dispatchers.IO) { ScaleReader.read(port, NciScale) }) {
            is ScaleReader.Result.Failed -> r.message
            is ScaleReader.Result.Ok -> with(r.reading) {
              val w = kg?.let { "${ir.vil3ntec.tohid.qty(it)} کیلو" } ?: "بی‌وزن"
              w + listOfNotNull(
                if (motion) "در حرکت" else null, if (zero) "روی صفر" else null,
                if (over) "بیش از ظرفیت" else null, if (under) "زیرِ ظرفیت" else null, if (error) "خطای ترازو" else null,
              ).joinToString("") { " · $it" } + if (usable) " · ایستا" else ""
            }
          }
        }
      }) { Text("خواندنِ وزن") }
      reading?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text) }
    }
  }
}

/* ============================== نمایشگرِ مشتری ============================== */

@Composable
internal fun CustomerDisplayPanel() {
  if (!CustomerFacing.supported) return
  var open by CustomerFacing.open
  Panel {
    HwSwitchRow("نمایشگرِ مشتری", "پنجرهٔ دوم رو به مشتری: اقلامِ سبد و جمع. پنجره را به صفحهٔ دوم ببرید.", open) { open = it }
  }
}
