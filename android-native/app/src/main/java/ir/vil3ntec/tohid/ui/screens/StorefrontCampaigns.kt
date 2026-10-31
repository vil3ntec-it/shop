package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.hw.EscPos
import ir.vil3ntec.tohid.core.hw.PrintQueue
import ir.vil3ntec.tohid.core.marketing.CampaignLinks
import ir.vil3ntec.tohid.core.qr.QrCode
import ir.vil3ntec.tohid.core.receipt.ReceiptRaster
import ir.vil3ntec.tohid.core.storefront.CampaignDto
import ir.vil3ntec.tohid.core.storefront.CampaignLinksApi
import ir.vil3ntec.tohid.core.storefront.CampaignLinksApi.Gate
import ir.vil3ntec.tohid.core.storefront.CampaignLinksRepository
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.fa
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.print.LabelSheetPrint
import ir.vil3ntec.tohid.print.PrintSpooler
import ir.vil3ntec.tohid.print.PrinterRegistry
import ir.vil3ntec.tohid.print.ReceiptPainter
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  «پیوند و QR» — برگهٔ صفحهٔ ویترین (MKT-10، MKT-11؛ `/api/shop/storefront/campaigns`).
 *
 *  دکان برای هر جا که مشتری از آن می‌آید یک پیوند می‌سازد (QR ِ دیوار، QR ِ رسید،
 *  اینستاگرام، تراکت…) و این‌جا می‌بیند هر کدام در ۳۰ روزِ اخیر چند بازدید، چند سفارش
 *  و چه فروشِ تحویل‌شده‌ای آورده — فقط شمار، هیچ نام یا شماره‌ای. QR ِ هر پیوند روی
 *  کاغذِ رسید یا برگهٔ A4 ِ دیوار چاپ می‌شود؛ پیوند به دکان گره دارد، پس با عوض شدنِ
 *  نشانیِ ویترین هم QR ِ چاپ‌شده کار می‌کند.
 *
 *  پرچمِ خاموش یا ویترینِ خاموش ⇒ یک یادداشتِ کوتاه، بی دکمهٔ بی‌اثر. ساختن و بایگانی
 *  فقط برای صاحب/مدیر با اشتراکِ ویترین؛ «ثبت شد» فقط پس از پاسخِ سرور.
 */
@Composable
internal fun CampaignsPane(storeTitle: String, snackbar: SnackbarHostState) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val repo = remember(context) { CampaignLinksRepository(Backend.api(context)) }
  val base = remember(context) { AppConfig.baseUrl(context) }
  var manage by remember { mutableStateOf(false) }
  var items by remember { mutableStateOf<List<CampaignDto>>(emptyList()) }
  var days by remember { mutableStateOf(30) }
  var gate by remember { mutableStateOf<Gate?>(null) }
  var loadError by remember { mutableStateOf("") }
  var shown by remember { mutableStateOf<CampaignDto?>(null) }

  suspend fun reload() {
    gate = null
    val cfg = repo.config().valueOrNull()
    if (cfg != null && !cfg.flag) { gate = Gate.OFF; return }
    manage = cfg?.let { it.enabled && it.permissions.manage } == true
    repo.list()
      .onSuccess { items = it.campaigns; days = it.days; gate = Gate.READY }
      .onFailure { e -> loadError = e.userMessage; gate = CampaignLinksApi.gateOf(e.status, e.code) }
  }
  LaunchedEffect(Unit) { reload() }

  fun replace(c: CampaignDto) {
    items = if (items.any { it.id == c.id }) items.map { if (it.id == c.id) c.copy(stats = it.stats) else it } else listOf(c) + items
  }

  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    when (gate) {
      null -> TohidLoadingState(rows = 3)
      Gate.ERROR -> TohidErrorState("پیوندها باز نشد", loadError.ifBlank { "اتصال را بررسی کنید." }, onRetry = { scope.launch { reload() } })
      Gate.OFF -> TohidEmptyState("پیوند و QR هنوز روشن نیست", "این بخش پس از روشن شدن از طرفِ سامانه دیده می‌شود.", icon = Icons.Filled.QrCode)
      Gate.NO_PERMISSION -> TohidEmptyState("فقط اعضای دکان", "دیدنِ پیوندها اجازهٔ خواندنِ دادهٔ دکان را می‌خواهد.", icon = Icons.Filled.QrCode)
      Gate.READY -> {
        Text("هر پیوند نشان می‌دهد مشتری از کجا آمده. شمارها مالِ ${days.fa()} روزِ اخیرند و هیچ نام یا شماره‌ای ندارند.",
          style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
        if (manage) {
          QuickQrRow(repo, snackbar, items) { replace(it); shown = it }
          NewCampaignForm(repo, snackbar) { replace(it) }
        }
        if (items.isEmpty()) {
          TohidEmptyState("هنوز پیوندی نیست", if (manage) "با «QR ِ دیوار» یا فرمِ بالا نخستین پیوند را بسازید." else "صاحب دکان یا مدیر پیوند می‌سازد.", icon = Icons.Filled.QrCode)
        }
        items.forEach { c ->
          CampaignRow(c, manage, onShow = { shown = c }, onArchive = { archived ->
            scope.launch {
              repo.setArchived(c.id, archived)
                .onSuccess { replace(it); snackbar.showSnackbar(if (archived) "بایگانی شد" else "برگشت") }
                .onFailure { snackbar.showSnackbar(it.userMessage) }
            }
          })
        }
      }
    }
  }

  shown?.let { c ->
    val url = CampaignLinks.publicUrl(base, c.path)
    if (url == null) {
      LaunchedEffect(c.id) { snackbar.showSnackbar("نشانیِ این پیوند خوانده نشد"); shown = null }
    } else {
      QrDialog(c, url, storeTitle, snackbar) { shown = null }
    }
  }
}

/** دو QR ِ ثابتِ MKT-10 — زدنِ دوباره همان پیوند را برمی‌گرداند (کلیدِ یکتاییِ ثابت) */
@Composable
private fun QuickQrRow(repo: CampaignLinksRepository, snackbar: SnackbarHostState, items: List<CampaignDto>, onReady: (CampaignDto) -> Unit) {
  val scope = rememberCoroutineScope()
  var busy by remember { mutableStateOf(false) }
  fun open(clientId: String, name: String, source: CampaignLinks.Source) {
    items.firstOrNull { it.clientId == clientId }?.let { onReady(it); return }
    busy = true
    scope.launch {
      repo.create(clientId, name, source)
        .onSuccess { onReady(it) }
        .onFailure { snackbar.showSnackbar(it.userMessage) }
      busy = false
    }
  }
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(enabled = !busy, onClick = { open(CampaignLinks.WALL_CLIENT_ID, "QR ِ دیوار و پیشخوان", CampaignLinks.Source.QR_WALL) }) { Text("QR ِ دیوار") }
    OutlinedButton(enabled = !busy, onClick = { open(CampaignLinks.RECEIPT_CLIENT_ID, "QR ِ رسید", CampaignLinks.Source.QR_RECEIPT) }) { Text("QR ِ رسید") }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewCampaignForm(repo: CampaignLinksRepository, snackbar: SnackbarHostState, onCreated: (CampaignDto) -> Unit) {
  val scope = rememberCoroutineScope()
  var name by remember { mutableStateOf("") }
  var source by remember { mutableStateOf(CampaignLinks.Source.SOCIAL) }
  var touched by remember { mutableStateOf(false) }
  var saving by remember { mutableStateOf(false) }
  //  یک کلید برای همین فرم تا پاسخ برسد: دوبار زدن یا تلاشِ دوباره ⇒ همان پیوند
  var clientId by remember { mutableStateOf(Ulid.next()) }
  val error = if (touched) CampaignLinks.nameError(name) else null

  Column(Modifier.fillMaxWidth().border(1.dp, Shop.colors.borderCard, MaterialTheme.shapes.medium).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("پیوندِ تازه", fontWeight = FontWeight.Bold)
    OutlinedTextField(
      value = name, onValueChange = { name = it; touched = true },
      label = { Text("نام (مثلاً «اینستاگرام» یا «تراکتِ محله»)") },
      singleLine = true, isError = error != null,
      supportingText = { if (error != null) Text(error) },
      modifier = Modifier.fillMaxWidth(),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      CampaignLinks.Source.entries.filter { it != CampaignLinks.Source.QR_WALL && it != CampaignLinks.Source.QR_RECEIPT }.forEach { s ->
        FilterChip(selected = source == s, onClick = { source = s }, label = { Text(s.label) })
      }
    }
    Button(enabled = !saving, onClick = {
      touched = true
      if (CampaignLinks.nameError(name) != null) return@Button
      saving = true
      scope.launch {
        repo.create(clientId, name, source)
          .onSuccess {
            onCreated(it)
            name = ""; touched = false; clientId = Ulid.next()
            snackbar.showSnackbar("ثبت شد")
          }
          .onFailure { snackbar.showSnackbar(it.userMessage) }
        saving = false
      }
    }) { Text(if (saving) "در حالِ ثبت…" else "ساختنِ پیوند") }
  }
}

@Composable
private fun CampaignRow(c: CampaignDto, manage: Boolean, onShow: () -> Unit, onArchive: (Boolean) -> Unit) {
  val s = c.stats
  Column(
    Modifier.fillMaxWidth().border(1.dp, Shop.colors.borderCard, MaterialTheme.shapes.medium).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(c.name, fontWeight = FontWeight.Bold, color = if (c.archived) Shop.colors.muted else Color.Unspecified)
        Text(CampaignLinks.Source.of(c.source).label + if (c.archived) " · بایگانی‌شده" else "",
          style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
      }
      TextButton(onClick = onShow) { Text("QR و پیوند") }
    }
    if (s != null) {
      Text("بازدید ${s.visits.fa()} · سفارش ${s.orders.fa()} · تحویل‌شده ${s.doneOrders.fa()} (${money(s.doneMinor / 100.0)} افغانی)",
        style = MaterialTheme.typography.bodySmall)
    }
    if (manage) {
      TextButton(onClick = { onArchive(!c.archived) }) {
        Text(if (c.archived) "برگرداندن" else "بایگانی (QR ِ چاپ‌شده هنوز به ویترین می‌برد)")
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QrDialog(c: CampaignDto, url: String, storeTitle: String, snackbar: SnackbarHostState, onClose: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val clipboard = LocalClipboardManager.current
  val modules = remember(url) { runCatching { QrCode.encode(url) }.getOrNull() }
  var busy by remember { mutableStateOf(false) }

  fun report(msg: String) { scope.launch { snackbar.showSnackbar(msg) } }

  AlertDialog(
    onDismissRequest = onClose,
    title = { Text(c.name) },
    text = {
      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (modules != null) QrImage(modules, Modifier.size(220.dp).semantics { contentDescription = "QR ِ $url" })
        Text(CampaignLinks.displayUrl(url), style = MaterialTheme.typography.bodySmall)
        Text(CampaignLinks.SCAN_HINT, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          OutlinedButton(onClick = { clipboard.setText(AnnotatedString(url)); report("پیوند کپی شد") }) { Text("کپیِ پیوند") }
          OutlinedButton(enabled = !busy, onClick = {
            val pr = PrinterRegistry.defaultSaved(context)
            if (pr == null) { report("چاپگرِ رسید تنظیم نشده است"); return@OutlinedButton }
            busy = true
            scope.launch {
              val msg = runCatching {
                val profile = pr.profile()
                val bytes = withContext(Dispatchers.Default) {
                  val img = ReceiptRaster.compose(CampaignLinks.slip(storeTitle, url, profile.paperDots), ReceiptPainter(context))
                  EscPos.document(img, profile)
                }
                PrintSpooler.submitAndWait(context, PrintQueue.Kind.TEST, pr.link, "QR ِ ${c.name}", bytes)
              }.getOrElse { it.message ?: "چاپ نشد" }
              busy = false
              report(msg)
            }
          }) { Text("چاپ روی رسید") }
          OutlinedButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
              runCatching {
                val page = withContext(Dispatchers.Default) {
                  CampaignLinks.page(ReceiptRaster.compose(CampaignLinks.poster(storeTitle, url), ReceiptPainter(context)))
                }
                LabelSheetPrint.print(context, page, "QR ِ ${c.name}")
              }.onFailure { report(it.message ?: "برگه ساخته نشد") }
              busy = false
            }
          }) { Text("برگهٔ A4") }
        }
      }
    },
    confirmButton = { TextButton(onClick = onClose) { Text("بستن") } },
  )
}

/** ماتریسِ QR (`core/qr`) با چهار خانه حاشیهٔ سفید، روی زمینهٔ سفید در هر دو تم */
@Composable
private fun QrImage(modules: Array<BooleanArray>, modifier: Modifier) {
  Canvas(modifier.background(Color.White)) {
    val n = modules.size + 2 * QrCode.QUIET_ZONE
    val cell = minOf(size.width, size.height) / n
    for (y in modules.indices) for (x in modules[y].indices) if (modules[y][x]) {
      drawRect(Color.Black, Offset((x + QrCode.QUIET_ZONE) * cell, (y + QrCode.QUIET_ZONE) * cell), Size(cell, cell))
    }
  }
}
