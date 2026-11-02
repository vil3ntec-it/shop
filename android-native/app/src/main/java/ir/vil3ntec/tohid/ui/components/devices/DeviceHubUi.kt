package ir.vil3ntec.tohid.ui.components.devices

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.hw.DeviceClassifier
import ir.vil3ntec.tohid.core.hw.DeviceKind
import ir.vil3ntec.tohid.core.hw.DeviceRoster
import ir.vil3ntec.tohid.core.hw.DeviceSignal
import ir.vil3ntec.tohid.core.hw.DeviceState
import ir.vil3ntec.tohid.core.hw.DeviceVerdict
import ir.vil3ntec.tohid.core.hw.PendingScans
import ir.vil3ntec.tohid.core.hw.PrinterProfile
import ir.vil3ntec.tohid.core.hw.SavedDevice
import ir.vil3ntec.tohid.core.hw.ScanTarget
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrDevices
import ir.vil3ntec.tohid.data.HeldInvoiceStore
import ir.vil3ntec.tohid.data.HeldInvoices
import ir.vil3ntec.tohid.data.ScanSaleHold
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.hw.DeviceLinks
import ir.vil3ntec.tohid.hw.DeviceSignals
import ir.vil3ntec.tohid.hw.HwPlatform
import ir.vil3ntec.tohid.hw.Pole
import ir.vil3ntec.tohid.print.PrinterRegistry
import ir.vil3ntec.tohid.scan.ScanBackground
import ir.vil3ntec.tohid.scan.ScanInbox
import ir.vil3ntec.tohid.scan.ScanRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  «دستگاه‌ها» (درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۱) — بدنهٔ مشترکِ رابطِ امروز (‎`ui/screens/Devices.kt`‎، گوشی و کامپیوتر) و
 *  نسخهٔ ۳ (‎`ui/v3/settings/devices/DevicesScreen.kt`‎). قاب و رنگ را صفحه می‌دهد؛ منطق در `core/hw/DeviceHub.kt`،
 *  وصل‌ها در `hw/DeviceLinks.kt`، صف در `scan/ScanInbox.kt`.
 */
data class HubColors(val ok: Color, val bad: Color, val muted: Color, val text: Color)

/** نوشتهٔ نوع و وضعیت به زبانِ صفحه */
object DeviceText {
  fun kind(k: DeviceKind): String = when (k) {
    DeviceKind.PRINTER -> StrDevices.kindPrinter
    DeviceKind.SCANNER -> StrDevices.kindScanner
    DeviceKind.SCALE -> StrDevices.kindScale
    DeviceKind.DISPLAY -> StrDevices.kindDisplay
    DeviceKind.OTHER -> StrDevices.kindOther
    DeviceKind.UNKNOWN -> StrDevices.kindUnknown
  }

  fun state(s: DeviceState?): String = when (s) {
    DeviceState.CONNECTED -> StrDevices.stateConnected
    DeviceState.CONNECTING -> StrDevices.stateConnecting
    DeviceState.OFFLINE -> StrDevices.stateOffline
    DeviceState.KEYBOARD_MODE -> StrDevices.stateKeyboard
    DeviceState.NO_PERMISSION -> StrDevices.stateNoPermission
    DeviceState.BLUETOOTH_OFF -> StrDevices.stateBluetoothOff
    null -> StrDevices.stateUntested
  }

  /** نقش‌هایی که صاحب‌کار می‌تواند بگذارد */
  val ROLES: List<DeviceKind> = listOf(DeviceKind.PRINTER, DeviceKind.SCANNER, DeviceKind.SCALE, DeviceKind.DISPLAY)
}

@Composable
private fun Dot(color: Color) {
  Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

/**
 *  «همهٔ دستگاه‌ها»: یک «جست‌وجو» (گوشی: جفت‌شده‌ها و بلوتوثِ نزدیک؛ کامپیوتر: درگاه‌های COM)، نوعِ خودکار، افزودن،
 *  نقش، «وصلِ خودکار»، آزمایش و وضعیتِ سبز/سرخ برای هر دستگاه.
 */
@Composable
fun DeviceHubBody(colors: HubColors, toast: (String) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val saved by DeviceLinks.roster
  var found by remember { mutableStateOf(listOf<Pair<DeviceSignal, DeviceVerdict>>()) }
  var busy by remember { mutableStateOf(false) }
  var message by remember { mutableStateOf<String?>(null) }
  var lastScan by remember { mutableStateOf<String?>(null) }

  fun classify(list: List<DeviceSignal>) = list.map { s -> (s to DeviceClassifier.classify(s)).also { DeviceLinks.verdicts[DeviceLinks.key(s.link)] = it.second } }

  fun refresh() {
    scope.launch {
      val paired = withContext(Dispatchers.IO) { DeviceSignals.paired(context) }
      found = DeviceRoster.merge(DeviceLinks.load(context), classify(paired) + found)
      //  وضعیتِ ثبت‌شده‌هایی که خواننده ندارند: یک آزمونِ کوتاه
      DeviceLinks.load(context).filter { it.kind != DeviceKind.SCANNER || !it.auto }.forEach { d ->
        if (DeviceLinks.state(d.link) == null) launch(Dispatchers.IO) { DeviceLinks.probe(context, d) }
      }
    }
  }

  fun search() {
    if (busy) return
    busy = true
    message = StrDevices.searching
    scope.launch {
      val paired = withContext(Dispatchers.IO) { DeviceSignals.paired(context) }
      found = DeviceRoster.merge(DeviceLinks.load(context), classify(paired))
      val err = if (DeviceSignals.search()) DeviceSignals.nearby(context, DeviceSignals.SEARCH_MS) { s ->
        found = DeviceRoster.merge(DeviceLinks.load(context), found + classify(listOf(s)))
      } else null
      busy = false
      message = err ?: if (found.isEmpty()) StrDevices.nothingFound else null
    }
  }

  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
    if (r.values.all { it } && DeviceSignals.granted(context)) { search(); ScanBackground.sync(context) } else message = StrDevices.permission
  }

  LaunchedEffect(Unit) { DeviceLinks.load(context); refresh() }
  //  اسکنِ بارکدخوانِ وصل‌شده تا این صفحه باز است همین‌جا دیده می‌شود (آزمونِ اسکن)، نه در صندوق
  val owner = remember { Any() }
  DisposableEffect(Unit) {
    ScanRouter.register(owner) { code -> lastScan = code; true }
    onDispose { ScanRouter.unregister(owner) }
  }

  fun assign(d: SavedDevice) = assignTo(context, d)

  fun store(list: List<SavedDevice>) {
    DeviceLinks.save(context, list)
    ScanBackground.sync(context)
  }

  fun add(s: DeviceSignal, v: DeviceVerdict) {
    //  نوعِ نامشخص حدس زده نمی‌شود: درگاهی که شاید چاپگر است نباید با خوانندهٔ بارکدخوان بسته بماند — «نقش» را صاحب‌کار می‌گذارد
    val kind = if (v.kind == DeviceKind.OTHER) DeviceKind.UNKNOWN else v.kind
    val d = SavedDevice(s.link, s.name, kind, auto = true)
    store(DeviceRoster.upsert(DeviceLinks.load(context), d))
    assign(d)
    found = found.filterNot { DeviceLinks.key(it.first.link) == DeviceLinks.key(s.link) }
    if (kind != DeviceKind.SCANNER) scope.launch(Dispatchers.IO) { DeviceLinks.probe(context, d) }
  }

  fun test(d: SavedDevice) {
    scope.launch {
      val r = withContext(Dispatchers.IO) {
        when (d.kind) {
          DeviceKind.PRINTER -> DeviceLinks.testPrint(context, d)
          DeviceKind.SCALE -> DeviceLinks.testWeigh(context, d)
          DeviceKind.DISPLAY -> DeviceLinks.testDisplay(context, d)
          else -> DeviceLinks.probe(context, d)
        }
      }
      toast(r ?: StrDevices.testOk)
    }
  }

  Column(Modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Button(enabled = !busy, onClick = {
        if (DeviceSignals.granted(context)) search() else ask.launch(DeviceSignals.permissions().toTypedArray())
      }) { Text(StrDevices.search) }
      Spacer(Modifier.width(10.dp))
      message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.muted, modifier = Modifier.weight(1f)) }
    }
    lastScan?.let {
      Spacer(Modifier.height(6.dp))
      Text(ir.vil3ntec.tohid.core.i18n.StrSettings.scanned(it, it.length), color = colors.ok, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
    }

    if (saved.isNotEmpty()) {
      Spacer(Modifier.height(12.dp))
      Text(StrDevices.savedHead, style = MaterialTheme.typography.labelLarge, color = colors.muted)
      saved.forEach { d ->
        SavedRow(d, colors, onTest = { test(d) }, onRole = { k ->
          val next = d.copy(kind = k)
          store(DeviceRoster.upsert(DeviceLinks.load(context), next))
          assign(next)
        }, onAuto = { on -> store(DeviceRoster.upsert(DeviceLinks.load(context), d.copy(auto = on))) }, onRemove = {
          store(DeviceRoster.remove(DeviceLinks.load(context), d.link))
          DeviceLinks.states.remove(DeviceLinks.key(d.link))
          refresh()
        })
      }
    }

    if (found.isNotEmpty()) {
      Spacer(Modifier.height(12.dp))
      Text(StrDevices.foundHead, style = MaterialTheme.typography.labelLarge, color = colors.muted)
      found.forEach { (s, v) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(s.name, color = colors.text, style = MaterialTheme.typography.bodyMedium)
            Text(
              DeviceText.kind(v.kind) + if (v.keyboard) " · " + StrDevices.stateKeyboard else "",
              color = colors.muted, style = MaterialTheme.typography.bodySmall,
            )
          }
          if (s.paired) OutlinedButton(onClick = { add(s, v) }) { Text(StrDevices.add) }
          else OutlinedButton(onClick = {
            if (DeviceSignals.bond(context, s.link.removePrefix("bt:"))) toast(StrDevices.pairFirst)
          }) { Text(StrDevices.pair) }
        }
      }
    }
  }
}

/**
 *  نقش ⇒ همان تنظیمِ امروزِ همان دستگاه: چاپگر به فهرستِ چاپگرها (اگر نیست)؛ روی کامپیوتر ترازو ⇒ درگاهِ ترازوی
 *  صندوق (`scale_port`، HW-09) و نمایشگر ⇒ درگاهِ نمایشگرِ قطبی (`Pole`، HW-10). بارکدخوان ⇒ خواننده (`ScanBackground`).
 */
private fun assignTo(context: android.content.Context, d: SavedDevice) {
  val port = d.link.removePrefix("bt:")
  when (d.kind) {
    DeviceKind.PRINTER -> if (PrinterRegistry.list(context).none { it.link == d.link || it.link == port }) {
      runCatching { PrinterRegistry.add(context, d.name, d.link, PrinterProfile.WIDTH_58MM) }
    }
    DeviceKind.SCALE -> if (HwPlatform.serial != null) runCatching {
      context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE).edit().putString("scale_port", port).apply()
    }
    DeviceKind.DISPLAY -> if (HwPlatform.serial != null) Pole.save(context, port)
    else -> Unit
  }
}

@Composable
private fun SavedRow(d: SavedDevice, colors: HubColors, onTest: () -> Unit, onRole: (DeviceKind) -> Unit, onAuto: (Boolean) -> Unit, onRemove: () -> Unit) {
  val state = DeviceLinks.states[DeviceLinks.key(d.link)]
  val note = DeviceLinks.notes[DeviceLinks.key(d.link)]
  var menu by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Dot(if (state == DeviceState.CONNECTED) colors.ok else if (state == null || state == DeviceState.CONNECTING) colors.muted else colors.bad)
      Spacer(Modifier.width(8.dp))
      Column(Modifier.weight(1f)) {
        Text(d.name, color = colors.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(DeviceText.kind(d.kind) + " · " + DeviceText.state(state), color = colors.muted, style = MaterialTheme.typography.bodySmall)
      }
      Box {
        TextButton(onClick = { menu = true }) { Text(StrDevices.role) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
          DeviceText.ROLES.forEach { k ->
            DropdownMenuItem(text = { Text(DeviceText.kind(k)) }, onClick = { menu = false; onRole(k) })
          }
        }
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Switch(checked = d.auto, onCheckedChange = onAuto)
      Text(StrDevices.autoConnect, color = colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
      TextButton(onClick = onTest) { Text(StrDevices.test) }
      TextButton(onClick = onRemove) { Text(StrDevices.remove, color = colors.bad) }
    }
    if (state == DeviceState.KEYBOARD_MODE) Text(StrDevices.keyboardHelp, color = colors.muted, style = MaterialTheme.typography.bodySmall)
    else if (note != null && state != DeviceState.CONNECTED) Text(note, color = colors.muted, style = MaterialTheme.typography.bodySmall)
    HorizontalDivider(Modifier.padding(top = 6.dp))
  }
}

/** «اسکن وقتی برنامه بسته است»: وضعیت (کلیدِ جدا ندارد) و راهنمای حالتِ صفحه‌کلید */
@Composable
fun BackgroundScanBody(colors: HubColors) {
  val context = LocalContext.current
  LaunchedEffect(Unit) { DeviceLinks.load(context) }
  val saved by DeviceLinks.roster
  val scanners = DeviceRoster.backgroundScanners(saved)
  val connected = scanners.count { DeviceLinks.states[DeviceLinks.key(it.link)] == DeviceState.CONNECTED }
  val keyboard by ScanInbox.keyboardSeen
  Column(Modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Dot(if (connected > 0) colors.ok else if (scanners.isEmpty()) colors.muted else colors.bad)
      Spacer(Modifier.width(8.dp))
      Text(if (scanners.isEmpty()) StrDevices.backgroundNone else StrDevices.backgroundOn(connected), color = colors.text, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(6.dp))
    Text(StrDevices.memoryNote, color = colors.muted, style = MaterialTheme.typography.bodySmall)
    if (keyboard) {
      Spacer(Modifier.height(8.dp))
      Text(StrDevices.keyboardTitle, color = colors.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
      Text(StrDevices.keyboardHelp, color = colors.muted, style = MaterialTheme.typography.bodySmall)
    }
  }
}

/**
 *  اسکنِ زندهٔ بارکدخوانِ وصل‌شده (SPP، COM) تا ریشهٔ برنامه باز است — [dispatch] همان راهِ صفحهٔ باز/صندوقِ هر رابط.
 *  بی این گیرنده، اسکنِ زنده هم به صفِ «در انتظار» می‌رود (`ScanInbox.collecting`).
 */
@Composable
fun LiveScans(dispatch: (String) -> Unit) {
  DisposableEffect(Unit) {
    ScanInbox.collecting = true
    onDispose { ScanInbox.collecting = false }
  }
  LaunchedEffect(Unit) { for (code in ScanInbox.live) dispatch(code) }
}

/**
 *  «N اسکن در انتظار» — با باز شدنِ برنامه (و هر بار که اسکنِ تازه در صف بنشیند). مقصد: انبارگردانی، ورودِ کالا،
 *  فاکتورِ فروش. فروش ⇒ فاکتورِ نگه‌داشته (`ScanSaleHold`) با جمعش؛ «پرداخت» صندوق را با همان فاکتور باز می‌کند و
 *  فروش فقط از پنلِ پرداختِ صندوق ثبت می‌شود. «بعداً» تا اسکنِ تازه چیزی نمی‌پرسد.
 *
 *  @param open صفحهٔ همان مقصد را باز می‌کند (برداشتنِ اسکن‌ها با خودِ صفحه است: `ScanHandoff.take`)
 *  @param onNewItem «ثبتِ کالا» با همین بارکد
 */
@Composable
fun PendingScansPrompt(data: ShopData, open: (ScanTarget) -> Unit, onNewItem: (String) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val count by remember { ScanInbox.load(context).count }.collectAsState()
  var quietUntil by remember { mutableStateOf(0) }
  var askDiscard by remember { mutableStateOf(false) }
  var ready by remember { mutableStateOf<ScanSaleHold.Plan?>(null) }
  var failure by remember { mutableStateOf<String?>(null) }
  //  صف کوتاه شد (برداشته یا کنار گذاشته) ⇒ «بعداً» ِ پیشین دیگر جلوی اسکنِ تازه را نمی‌گیرد
  LaunchedEffect(count) { if (count < quietUntil) quietUntil = count }

  ready?.let { plan ->
    //  بستنِ این پنجره کدهای ناشناسِ مانده را تا اسکنِ تازه دوباره نمی‌پرسد
    fun close() { ready = null; quietUntil = count }
    AlertDialog(
      onDismissRequest = { close() },
      title = { Text(StrDevices.heldReady(plan.invoices.size, ir.vil3ntec.tohid.money(plan.totalMinor / 100.0) + " ؋")) },
      text = {
        Column {
          Text(StrDevices.toSaleLine)
          if (plan.unknown.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text(StrDevices.unknownCodes(plan.unknown.size)) }
        }
      },
      confirmButton = {
        Button(onClick = {
          ScanSaleHold.payRequest.value = plan.invoices.firstOrNull()?.id
          close()
          open(ScanTarget.SALE)
        }) { Text(StrDevices.pay) }
      },
      dismissButton = {
        Row {
          plan.unknown.firstOrNull()?.let { u -> TextButton(onClick = { close(); onNewItem(u.code) }) { Text(StrDevices.newItem) } }
          TextButton(onClick = { close() }) { Text(StrDevices.later) }
        }
      },
    )
    return
  }

  val handoff = ScanInbox.handoff.value
  LaunchedEffect(handoff) {
    if (handoff != null) {
      kotlinx.coroutines.delay(ir.vil3ntec.tohid.scan.ScanHandoff.EXPIRE_MS)
      if (ScanInbox.handoff.value === handoff) ScanInbox.handoff.value = null
    }
  }
  if (count <= quietUntil || handoff != null) return
  val pending = remember(count) { ScanInbox.pending() }
  val sum = remember(pending) { PendingScans.summary(pending) }

  if (askDiscard) {
    AlertDialog(
      onDismissRequest = { askDiscard = false },
      text = { Text(StrDevices.discardAsk(sum.scans)) },
      confirmButton = { TextButton(onClick = { askDiscard = false; ScanInbox.discardAll() }) { Text(StrDevices.discard) } },
      dismissButton = { TextButton(onClick = { askDiscard = false }) { Text(Str.cancel) } },
    )
    return
  }

  fun toSale() {
    scope.launch {
      //  «کارتِ فرمان» روشن ⇒ «فاکتورِ تازه» مرزِ دو فاکتور است (خاموش ⇒ همه یک فاکتور، همان امروز)
      val commands = ir.vil3ntec.tohid.scan.ScanCashPref.commandOf(context)
      val plan = withContext(Dispatchers.Default) { ScanSaleHold.plan(data, pending, commands) }
      if (plan.settled.isNotEmpty()) ScanInbox.take(plan.settled)
      if (plan.invoices.isEmpty()) { failure = StrDevices.unknownCodes(plan.unknown.size); return@launch }
      val store = HeldInvoices.of(context)
      val now = System.currentTimeMillis()
      val parked = HashSet<String>()
      for (inv in plan.invoices) {
        when (val o = store.park(ScanSaleHold.held(inv, now))) {
          is HeldInvoiceStore.Outcome.Done -> parked.addAll(inv.scanIds)
          is HeldInvoiceStore.Outcome.Refused -> { failure = o.message; break }
        }
      }
      //  فقط اسکن‌هایی که فاکتورشان نشست برداشته می‌شوند؛ کدِ ناشناس می‌ماند تا کالایش ثبت شود
      ScanInbox.take(parked)
      if (parked.isNotEmpty()) ready = plan.copy(invoices = plan.invoices.filter { it.scanIds.all { id -> id in parked } })
    }
  }

  AlertDialog(
    onDismissRequest = { quietUntil = count },
    title = { Text(StrDevices.pendingTitle(sum.scans)) },
    text = {
      Column {
        Text(StrDevices.pendingLine(sum.codes, sum.units))
        if (sum.fromMemory > 0) Text(StrDevices.fromMemory(sum.fromMemory), style = MaterialTheme.typography.bodySmall)
        failure?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(10.dp))
        Text(StrDevices.pendingAsk, fontWeight = FontWeight.SemiBold)
        Target(StrDevices.toCount, StrDevices.toCountLine) { ScanInbox.handTo(ScanTarget.COUNT); open(ScanTarget.COUNT) }
        Target(StrDevices.toReceive, StrDevices.toReceiveLine) { ScanInbox.handTo(ScanTarget.RECEIVE); open(ScanTarget.RECEIVE) }
        Target(StrDevices.toSale, StrDevices.toSaleLine) { toSale() }
      }
    },
    confirmButton = { TextButton(onClick = { quietUntil = count }) { Text(StrDevices.later) } },
    dismissButton = { TextButton(onClick = { askDiscard = true }) { Text(StrDevices.discard) } },
  )
}

@Composable
private fun Target(title: String, line: String, onClick: () -> Unit) {
  OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
    Column(Modifier.fillMaxWidth()) {
      Text(title, fontWeight = FontWeight.SemiBold)
      Text(line, style = MaterialTheme.typography.bodySmall)
    }
  }
}
