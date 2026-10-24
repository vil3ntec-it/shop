package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.print.PrinterRegistry
import ir.vil3ntec.tohid.print.ThermalPrinter
import ir.vil3ntec.tohid.sync.PhoneScanner
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  دستگاه‌های جانبی — همهٔ چیزهایی که به برنامه وصل می‌شوند، در یک صفحه.
 *
 *    • **چاپگرها** — چند چاپگر (بلوتوث، شبکه/وای‌فای، سیم یا چاپگرِ نصب‌شده
 *      در ویندوز/مک/لینوکس)؛ یکی پیش‌فرضِ فاکتور، برگهٔ آزمون برای هر کدام
 *    • **بارکدخوان‌ها** — USB و بلوتوث بی نصب کار می‌کنند (خودشان را
 *      صفحه‌کلید معرفی می‌کنند)؛ کادرِ آزمون می‌گوید بارکدخوان درست
 *      می‌خواند یا نه
 *    • **گوشی به‌عنوانِ بارکدخوان** — کدِ شش‌رقمیِ کامپیوتر را در گوشی
 *      بزنید؛ هر بارکدی که گوشی بخواند در سبدِ کامپیوتر می‌نشیند
 *    • **همگام‌سازیِ زنده** — فروشِ هر دستگاه همان لحظه روی بقیه
 *    • **عکسِ کالاها** — نمایش روشن/خاموش روی همین دستگاه
 *
 *  همین صفحه روی گوشی، تبلت و کامپیوتر است؛ روی کامپیوتر دوستونه.
 */
@Composable
fun DevicesScreen(data: ir.vil3ntec.tohid.data.ShopData, snackbar: SnackbarHostState) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  fun toast(t: String) { scope.launch { snackbar.showSnackbar(t) } }
  LaunchedEffect(Unit) { PhoneScanner.load(context); PhotoPrefs.load(context) }

  //  تنظیمِ چاپگر و کشو فقط صاحب و مدیر (`settings.write`)؛ باز کردنِ کشو با `drawer.open`
  //  نقش یا سیاستِ دکان که عوض شد (پاسخِ تازهٔ سرور) همین‌جا دوباره سنجیده می‌شود
  val policy by ir.vil3ntec.tohid.data.ShopRole.policyVersion.collectAsState()
  val canAdmin = remember(policy) { ir.vil3ntec.tohid.data.ShopRole.canOpenSettings(context) }
  val canDrawer = remember(policy) { ir.vil3ntec.tohid.data.ShopRole.canOpenDrawer(context) }

  val wide = screenSize() == Screen.WIDE
  val left: @Composable () -> Unit = {
    PrintersPanel(::toast, canAdmin, canDrawer)
    Spacer(Modifier.height(14.dp))
    PrintQueuePanel(::toast, canAdmin, canDrawer)
    Spacer(Modifier.height(14.dp))
    LabelsPanel(data, ::toast)
    Spacer(Modifier.height(14.dp))
    ScannersPanel()
  }
  val right: @Composable () -> Unit = {
    PhoneScannerPanel(data, ::toast)
    Spacer(Modifier.height(14.dp))
    ScalePanel(data, ::toast)
    Spacer(Modifier.height(14.dp))
    if (ir.vil3ntec.tohid.hw.CustomerFacing.supported) { CustomerDisplayPanel(); Spacer(Modifier.height(14.dp)) }
    LivePanel()
    Spacer(Modifier.height(14.dp))
    PhotosPanel()
  }
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
    Text("هر دستگاهی که به برنامه وصل است — چاپگر، بارکدخوان، گوشی — همین‌جا ثبت و آزموده می‌شود.",
      style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    Spacer(Modifier.height(12.dp))
    if (wide) {
      Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f)) { left() }
        Column(Modifier.weight(1f)) { right() }
      }
    } else {
      left(); Spacer(Modifier.height(14.dp)); right()
    }
    Spacer(Modifier.height(32.dp))
  }
}

@Composable
private fun Head(icon: ImageVector, title: String, sub: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Shop.colors.primaryTint), contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = null, tint = Shop.colors.primary, modifier = Modifier.size(20.dp))
    }
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text)
      Text(sub, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
  }
  Spacer(Modifier.height(10.dp))
}

/* ============================== چاپگرها ============================== */

@Composable
private fun PrintersPanel(toast: (String) -> Unit, canAdmin: Boolean, canDrawer: Boolean) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var tick by remember { mutableStateOf(0) }
  val printers = remember(tick) { PrinterRegistry.list(context) }
  val def = remember(tick) { PrinterRegistry.defaultLink(context) }
  var adding by remember { mutableStateOf(false) }
  var name by remember { mutableStateOf("") }
  var editing by remember { mutableStateOf<PrinterRegistry.Saved?>(null) }

  Panel {
    Head(Icons.Filled.Print, "چاپگرها", "حرارتیِ ۵۸ و ۸۰ میلی‌متری — بلوتوث، وای‌فای، سیم یا چاپگرِ نصب‌شده")
    if (printers.isEmpty()) {
      Text("هنوز چاپگری ثبت نشده.", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    }
    printers.forEach { p ->
      val isDefault = p.link == def
      Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp))
          .background(if (isDefault) Shop.colors.primaryTint else Shop.colors.surface2).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f)) {
          Text(p.name + if (isDefault) " — پیش‌فرض" else "", fontWeight = FontWeight.Bold, color = Shop.colors.text,
            style = MaterialTheme.typography.bodyMedium)
          Text("${PrinterRegistry.kind(p.link)} · ${PrinterRegistry.label(p.link)} · " +
            (if (p.isLabel) "برچسبِ کالا" else "${if (p.width >= ThermalPrinter.WIDTH_80MM) "۸۰" else "۵۸"} میلی‌متر") +
            (if (p.drawer && !p.isLabel) " · کشو" else ""),
            style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        }
        if (canAdmin) TextButton(onClick = { editing = p }) { Text("تنظیم") }
        TextButton(onClick = {
          toast("برگهٔ آزمون به «${p.name}» رفت…")
          scope.launch { toast(PrinterRegistry.testPrint(context, p) ?: "چاپِ آزمون انجام شد") }
        }) { Text("آزمون") }
        if (canAdmin && !isDefault) TextButton(onClick = { PrinterRegistry.makeDefault(context, p); tick++ }) { Text("پیش‌فرض") }
        if (canAdmin) IconButton(onClick = { PrinterRegistry.remove(context, p.id); tick++ }) {
          Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = Shop.colors.danger)
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    if (!canAdmin) {
      Text("افزودن و تنظیمِ چاپگر و کشو فقط برای صاحب و مدیرِ دکان است.", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    } else {
      OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true,
        label = { Text("نامِ چاپگرِ تازه (اختیاری)") }, modifier = Modifier.fillMaxWidth())
      Spacer(Modifier.height(6.dp))
      Button(onClick = { adding = true }) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp)); Text("افزودنِ چاپگر")
      }
    }
    Spacer(Modifier.height(6.dp))
    Text(
      "روی کامپیوتر: چاپگرِ بلوتوثی را اول در تنظیماتِ بلوتوثِ ویندوز/مک جفت کنید؛ یا به‌شکلِ «چاپگرِ نصب‌شده» دیده می‌شود یا در برگهٔ بلوتوث به‌شکلِ درگاهِ COM.",
      style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2,
    )
  }
  editing?.takeIf { canAdmin }?.let { p ->
    PrinterSettingsDialog(p, onDismiss = { editing = null }, onSaved = { editing = null; tick++; toast("تنظیمِ چاپگر ذخیره شد") }, toast = toast, canDrawer = canDrawer)
  }
  if (adding && canAdmin) {
    PrinterDialog(
      onDismiss = { adding = false },
      actionLabel = "ثبتِ چاپگر",
      makeDefault = false,
    ) { link, width ->
      adding = false
      PrinterRegistry.add(context, name, link, width)
      name = ""
      tick++
      toast("چاپگر ثبت شد")
    }
  }
}

/* ============================== بارکدخوان ============================== */

@Composable
private fun ScannersPanel() {
  var test by remember { mutableStateOf("") }
  var last by remember { mutableStateOf<String?>(null) }
  Panel {
    Head(Icons.Filled.QrCodeScanner, "بارکدخوان‌ها", "USB، بلوتوث، بی‌سیمِ ۲٫۴ گیگاهرتز — همه بی نصبِ درایور")
    Text(
      "بارکدخوانِ دستی، رومیزی یا بلوتوثی خودش را «صفحه‌کلید» معرفی می‌کند: وصل کنید (بلوتوثی را در تنظیماتِ دستگاه جفت کنید) و در صفحهٔ فروش اسکن کنید. چند بارکدخوان هم‌زمان هم کار می‌کنند.",
      style = MaterialTheme.typography.bodySmall, color = Shop.colors.text,
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
      value = test,
      onValueChange = { v ->
        if (v.contains('\n')) { last = v.trim().ifBlank { null }; test = "" } else test = v.take(64)
      },
      singleLine = false, maxLines = 2,
      label = { Text("آزمون: این‌جا بزنید و یک کالا اسکن کنید") },
      keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { last = test.trim().ifBlank { null }; test = "" }),
      modifier = Modifier.fillMaxWidth(),
    )
    last?.let {
      Spacer(Modifier.height(6.dp))
      Text("خوانده شد: $it  (${it.length} نویسه)", color = Shop.colors.success, fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(6.dp))
    Text(
      "اگر حروف به‌هم‌ریخته آمد، زبانِ صفحه‌کلید را انگلیسی کنید یا بارکدخوان را روی «USB HID / Keyboard» بگذارید (از دفترچهٔ خودش).",
      style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2,
    )
  }
}

/* ======================= گوشی به‌عنوانِ بارکدخوان ======================= */

@Composable
private fun PhoneScannerPanel(d: ir.vil3ntec.tohid.data.ShopData, toast: (String) -> Unit) {
  val context = LocalContext.current
  val listen = PhoneScanner.listenCode.value
  val send = PhoneScanner.sendCode.value
  val last = PhoneScanner.lastReceived.value
  var typed by remember { mutableStateOf("") }
  val signedIn = remember { ir.vil3ntec.tohid.data.repo.Backend.isReady(context) }
  Panel {
    Head(Icons.Filled.PhoneAndroid, "گوشی ⇄ کامپیوتر", "بارکدخوان و دوربینِ گوشی برای کامپیوتر — با یک کدِ شش‌رقمی")
    if (!signedIn) {
      Text("برای این کار هر دو دستگاه باید با یک حساب وارد شده باشند.", color = Shop.colors.warning,
        style = MaterialTheme.typography.bodySmall)
      Spacer(Modifier.height(8.dp))
    }
    Text("این دستگاه گیرنده باشد (کامپیوترِ صندوق):", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
    Spacer(Modifier.height(6.dp))
    if (listen == null) {
      OutlinedButton(onClick = { PhoneScanner.startListening(context) }) { Text("ساختنِ کدِ جفت‌شدن") }
    } else {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(listen.chunked(3).joinToString(" "), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Shop.colors.primary,
          modifier = Modifier.weight(1f))
        TextButton(onClick = { PhoneScanner.startListening(context) }) { Text("کدِ تازه") }
        TextButton(onClick = { PhoneScanner.stopListening(context) }) { Text("قطع", color = Shop.colors.danger) }
      }
      Text("در گوشی: دستگاه‌های جانبی ⇐ همین کد را بزنید. بعد صفحهٔ فروشِ گوشی را باز کنید و اسکن کنید.",
        style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
      last?.let { (code, at) ->
        Text("آخرین بارکد از گوشی: $code — ${ir.vil3ntec.tohid.formatMillis(at)}", style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.success)
      }
    }
    //  عکس‌هایی که گوشی بی کالای مشخص فرستاده — «گذاشتن روی کالا…»
    val inbox = PhoneScanner.inboxItems
    if (listen != null && inbox.isNotEmpty()) {
      Spacer(Modifier.height(10.dp))
      Text("عکس‌های رسیده از گوشی (${inbox.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        color = Shop.colors.text)
      var assign by remember { mutableStateOf<PhoneScanner.InboxItem?>(null) }
      val scope = rememberCoroutineScope()
      inbox.toList().forEach { item ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
          Text("عکس — ${ir.vil3ntec.tohid.formatMillis(item.at)}", style = MaterialTheme.typography.bodySmall,
            color = Shop.colors.text, modifier = Modifier.weight(1f))
          TextButton(onClick = { assign = item }) { Text("گذاشتن روی کالا…") }
          TextButton(onClick = { scope.launch { PhoneScanner.discard(context, item) } }) { Text("دور انداختن", color = Shop.colors.danger) }
        }
      }
      assign?.let { item ->
        CommandPalette(d, onDismiss = { assign = null }) { hit ->
          assign = null
          if (hit.kind == "product") PhoneScanner.openNow.value = item.copy(productId = hit.id)
          else toast("یک کالا انتخاب کنید")
        }
      }
    }
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = Shop.colors.border)
    Spacer(Modifier.height(12.dp))
    Text("این دستگاه فرستنده باشد (گوشی):", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
    Spacer(Modifier.height(6.dp))
    if (send == null) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = typed, onValueChange = { typed = it.take(7) }, singleLine = true,
          label = { Text("کدِ شش‌رقمیِ کامپیوتر") }, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
          if (PhoneScanner.pairTo(context, typed)) { typed = ""; toast("وصل شد — حالا در صفحهٔ فروش اسکن کنید") }
          else toast("کد باید شش رقم باشد")
        }) { Text("وصل") }
      }
    } else {
      var pick by remember { mutableStateOf(false) }
      val scope = rememberCoroutineScope()
      PhotoSourceSheet(open = pick, onDismiss = { pick = false }, note = "عکس برای کامپیوتر فرستاده می‌شود") { uri ->
        toast("در حالِ فرستادن…")
        scope.launch { toast(PhoneScanner.sendPhoto(context, uri, "") ?: "عکس به کامپیوتر رسید") }
      }
      OutlinedButton(onClick = { pick = true }) {
        Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp)); Text("فرستادنِ عکس به کامپیوتر")
      }
      Spacer(Modifier.height(6.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("بارکدها به کامپیوترِ ${send.chunked(3).joinToString(" ")} می‌روند", color = Shop.colors.success,
          fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { PhoneScanner.unpair(context) }) { Text("قطع", color = Shop.colors.danger) }
      }
    }
  }
}

/* ============================ همگام‌سازیِ زنده ============================ */

@Composable
private fun LivePanel() {
  val context = LocalContext.current
  val signedIn = remember { ir.vil3ntec.tohid.data.repo.Backend.isReady(context) }
  val live = ir.vil3ntec.tohid.sync.v1.LiveSocket.current?.connected == true
  Panel {
    Head(Icons.Filled.Sync, "همگام‌سازیِ زنده", "فروشِ گوشی همان لحظه روی کامپیوتر — و برعکس")
    val (text, color) = when {
      !signedIn -> "بی حساب کار می‌کنید — دفتر فقط روی همین دستگاه است. برای دیدنِ فروش‌ها روی همهٔ دستگاه‌ها وارد حساب شوید." to Shop.colors.warning
      live -> "وصل — هر فروش، کالا یا عکسی که روی دستگاهِ دیگرِ همین حساب ثبت شود، همان لحظه این‌جا می‌آید." to Shop.colors.success
      else -> "الان وصل نیست (اینترنت؟) — کارها نگه داشته می‌شوند و با وصل شدن خودشان می‌روند." to Shop.colors.warning
    }
    Text(text, color = color, style = MaterialTheme.typography.bodySmall)
  }
}

/* ============================== عکس‌ها ============================== */

@Composable
private fun PhotosPanel() {
  val context = LocalContext.current
  Panel {
    Head(Icons.Filled.Image, "عکسِ کالاها", "روی حساب ذخیره می‌شوند و روی همهٔ دستگاه‌ها می‌آیند")
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("نمایشِ عکس در فهرست‌ها و فروش", modifier = Modifier.weight(1f), color = Shop.colors.text,
        style = MaterialTheme.typography.bodyMedium)
      Switch(checked = PhotoPrefs.show.value, onCheckedChange = { PhotoPrefs.set(context, it) })
    }
    Text("خاموش کردن فقط روی همین دستگاه است و عکسی پاک نمی‌شود.", style = MaterialTheme.typography.labelSmall,
      color = Shop.colors.muted2)

    /* --------------------------- ربات عکس --------------------------- */
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = Shop.colors.border)
    Spacer(Modifier.height(10.dp))
    val bot = ir.vil3ntec.tohid.data.PhotoBot
    var auto by remember { mutableStateOf(bot.autoEnabled(context)) }
    Text("ربات عکس", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text)
    Text("همهٔ عکس‌ها را خودش بهتر می‌کند: زمینهٔ سفید، جدا کردنِ کالا، برش و هم‌اندازه، نور و رنگ و تیزی. اصلِ هر عکس نگه داشته می‌شود.",
      style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    Spacer(Modifier.height(8.dp))
    val prog = bot.progress.value
    if (prog != null) {
      LinearProgressIndicator(progress = { if (prog.second == 0) 0f else prog.first.toFloat() / prog.second }, modifier = Modifier.fillMaxWidth())
      Text("${ir.vil3ntec.tohid.plain(prog.first)} از ${ir.vil3ntec.tohid.plain(prog.second)}", style = MaterialTheme.typography.labelSmall,
        color = Shop.colors.muted)
    } else {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { bot.start(context, onlyNew = false) }) { Text("بهبودِ همهٔ عکس‌ها") }
        if (bot.originalsCount(context) > 0) OutlinedButton(onClick = { bot.restoreAll(context) }) { Text("برگرداندنِ اصلی‌ها") }
      }
    }
    bot.lastResult.value?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Shop.colors.success) }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("عکس‌های تازه (از هر دستگاه) خودکار بهتر شوند", modifier = Modifier.weight(1f), color = Shop.colors.text,
        style = MaterialTheme.typography.bodyMedium)
      Switch(checked = auto, onCheckedChange = { auto = it; bot.setAuto(context, it) })
    }
  }
}
