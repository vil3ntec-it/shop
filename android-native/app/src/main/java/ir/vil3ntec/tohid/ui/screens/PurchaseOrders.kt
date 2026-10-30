package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore
import ir.vil3ntec.tohid.core.text.TextNormalizer
import ir.vil3ntec.tohid.data.PoConfigDto
import ir.vil3ntec.tohid.data.PoLineDto
import ir.vil3ntec.tohid.data.PoPermsDto
import ir.vil3ntec.tohid.data.PoReceiptDto
import ir.vil3ntec.tohid.data.PoReceiptLineDto
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.PurchaseOrderBook
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.repo.PurchaseOrderRepository
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.platform.PurchaseOrderHost
import ir.vil3ntec.tohid.ui.platform.rememberPurchaseOrderHost
import kotlinx.coroutines.launch

/*
 *  سفارشِ خرید از تأمین‌کننده — PUR-02 (وضعیت‌ها)، PUR-03 (ساختن از کم‌موجودها و «همهٔ کالاهای این
 *  تأمین‌کننده»)، PUR-04 (دریافتِ کامل/جزئی با اسکن، خراب و باقی‌ماندهٔ باز)، PUR-14 (تطبیق). §۶.۶، S19.
 *
 *  ⛔ پشتِ پرچمِ سرورِ `shop.purchase_orders` (پیش‌فرض خاموش): تا `GET /config` نگوید `enabled`، هیچ
 *  دکمه‌ای دیده نمی‌شود و صفحهٔ خرید همان امروز است (آفلاین هم همین). سفارش و رسید در سرور‌اند؛
 *  رسیدِ ثبت‌شده با `PurchaseOrderBook.post` همان فاکتورِ خریدِ دفتر را می‌سازد (شناسه‌های قطعی،
 *  پس تکرار هرگز دو خرید نمی‌سازد). گوشی و کامپیوتر همین یک فایل.
 */

/** دکمهٔ «سفارش‌های خرید» — فقط با پرچمِ روشن */
@Composable
fun PurchaseOrdersEntry(store: ShopStore, d: ShopData, toast: (String) -> Unit, modifier: Modifier = Modifier) {
  //  دسترسیِ سکو و ساعت فقط از مرزِ سکو (`ui/platform/PurchaseOrderHost.kt`، §۴۰.۳)
  val host = rememberPurchaseOrderHost()
  val repo = host.repo
  var config by remember { mutableStateOf<PoConfigDto?>(null) }
  var open by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { config = repo.config().valueOrNull() }
  val cfg = config
  if (cfg == null || !cfg.enabled) return
  OutlinedButton(onClick = { open = true }, modifier = modifier) { Text("سفارش‌های خرید") }
  if (open) PurchaseOrdersDialog(store, d, host, cfg.permissions, toast) { open = false }
}

private sealed interface PoMode {
  data object Index : PoMode
  data class Detail(val id: String) : PoMode
  /** `base == null` ⇒ سفارشِ تازه */
  data class Edit(val base: PurchaseOrderDto?) : PoMode
  data class Receive(val order: PurchaseOrderDto) : PoMode
}

@Composable
private fun PurchaseOrdersDialog(
  store: ShopStore,
  d: ShopData,
  host: PurchaseOrderHost,
  perms: PoPermsDto,
  toast: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var mode by remember { mutableStateOf<PoMode>(PoMode.Index) }
  val c = Shop.colors
  val repo = host.repo
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    DialogEntry {
      Surface(
        color = c.surfaceSolid,
        shape = Shape.dialog,
        modifier = Modifier.padding(12.dp).widthIn(max = 920.dp).fillMaxWidth().fillMaxHeight(0.94f),
      ) {
        Column(Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (mode != PoMode.Index) TextButton(onClick = { mode = PoMode.Index }) { Text("فهرست") }
            Text("سفارش‌های خرید", style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("بستن") }
          }
          Spacer(Modifier.height(8.dp))
          when (val m = mode) {
            PoMode.Index -> PoList(repo, perms, onOpen = { mode = PoMode.Detail(it) }, onNew = { mode = PoMode.Edit(null) })
            is PoMode.Detail -> PoDetail(store, d, host, perms, m.id, toast,
              onEdit = { mode = PoMode.Edit(it) }, onReceive = { mode = PoMode.Receive(it) })
            is PoMode.Edit -> PoEditor(d, repo, m.base, toast, onSaved = { mode = PoMode.Detail(it.id) })
            is PoMode.Receive -> PoReceive(store, d, host, perms, m.order, toast, onDone = { mode = PoMode.Detail(m.order.id) })
          }
        }
      }
    }
  }
}

/* ══════════════════════════ فهرست ══════════════════════════ */

@Composable
private fun PoList(repo: PurchaseOrderRepository, perms: PoPermsDto, onOpen: (String) -> Unit, onNew: () -> Unit) {
  val c = Shop.colors
  var state by remember { mutableStateOf<String?>(null) }
  var orders by remember { mutableStateOf<List<PurchaseOrderDto>?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(state) {
    orders = null; error = null
    val r = repo.list(state)
    orders = r.valueOrNull()?.orders
    error = r.errorMessage()
  }
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    listOf(null to "همه", "draft" to "پیش‌نویس", "sent" to "فرستاده/باز", "cancelled" to "لغو").forEach { (k, label) ->
      FilterChip(selected = state == k, onClick = { state = k }, label = { Text(label) }, shape = Shape.chip)
    }
    Spacer(Modifier.width(12.dp))
    if (perms.manage) Button(onClick = onNew) { Text("+ سفارشِ خریدِ تازه") }
  }
  Spacer(Modifier.height(10.dp))
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
    when {
      error != null -> Text(error!!, color = c.danger, style = MaterialTheme.typography.bodySmall)
      orders == null -> Text("در حالِ خواندن…", color = c.muted, style = MaterialTheme.typography.bodySmall)
      orders!!.isEmpty() -> EmptyNote("هنوز سفارشِ خریدی نیست.")
      else -> orders!!.forEach { o ->
        val st = PurchaseOrderBook.status(o)
        Row(
          Modifier.fillMaxWidth().clip(Shape.button).clickable { onOpen(o.id) }.padding(horizontal = 8.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f)) {
            Text("#${o.no} · ${o.supplierName.ifBlank { "تأمین‌کننده" }}", color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
              "${o.lines.size} ردیف · سفارش ${money(PurchaseOrderBook.asAfn(o.totals.orderedMinor))} ؋ · باقی‌مانده ${money(PurchaseOrderBook.asAfn(o.totals.remainingMinor))} ؋" +
                (o.expectedDate?.let { " · رسیدن ${ir.vil3ntec.tohid.formatDate(it)}" } ?: ""),
              style = MaterialTheme.typography.labelSmall, color = c.muted,
            )
          }
          StatusPill(PurchaseOrderBook.label(st), poTone(st))
        }
        HorizontalDivider(color = c.border)
      }
    }
  }
}

private fun poTone(s: PurchaseOrderCore.Status): PillTone = when (s) {
  PurchaseOrderCore.Status.DRAFT -> PillTone.NEUTRAL
  PurchaseOrderCore.Status.SENT -> PillTone.INFO
  PurchaseOrderCore.Status.PARTIAL -> PillTone.WARNING
  PurchaseOrderCore.Status.COMPLETE -> PillTone.SUCCESS
  PurchaseOrderCore.Status.CANCELLED -> PillTone.DANGER
}

/* ══════════════════════════ جزئیات ══════════════════════════ */

@Composable
private fun PoDetail(
  store: ShopStore,
  d: ShopData,
  host: PurchaseOrderHost,
  perms: PoPermsDto,
  id: String,
  toast: (String) -> Unit,
  onEdit: (PurchaseOrderDto) -> Unit,
  onReceive: (PurchaseOrderDto) -> Unit,
) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  val repo = host.repo
  var order by remember(id) { mutableStateOf<PurchaseOrderDto?>(null) }
  var error by remember(id) { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var cancelAsk by remember { mutableStateOf(false) }
  LaunchedEffect(id) {
    val r = repo.get(id)
    order = r.valueOrNull(); error = r.errorMessage()
  }
  val o = order
  if (o == null) {
    Text(error ?: "در حالِ خواندن…", color = if (error != null) c.danger else c.muted, style = MaterialTheme.typography.bodySmall)
    return
  }
  val st = PurchaseOrderBook.status(o)
  val names = remember(d) { d.products.associate { it.id to it.name } }

  fun act(block: suspend () -> ir.vil3ntec.tohid.core.net.ApiResult<PurchaseOrderDto>, done: String) {
    if (busy) return
    busy = true
    scope.launch {
      val r = block()
      busy = false
      r.valueOrNull()?.let { order = it; toast(done) } ?: toast(r.errorMessage() ?: "نشد")
    }
  }

  fun postReceipt(r: PoReceiptDto) {
    scope.launch {
      var msg = "ثبت نشد"
      val wrote = store.update { cur ->
        when (val p = PurchaseOrderBook.post(cur, o, r, todayIso(), host.now())) {
          is PurchaseOrderBook.Posting.Ok -> { msg = "فاکتورِ خریدِ #${p.invoiceNo} در دفتر ثبت شد"; p.data }
          PurchaseOrderBook.Posting.Already -> { msg = "این رسید پیش‌تر در دفتر ثبت شده است"; null }
          is PurchaseOrderBook.Posting.Failed -> { msg = p.message; null }
        }
      }
      toast(if (wrote == false) "دفتر نوشته نشد (اشتراک یا دیسک)" else msg)
    }
  }

  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("#${o.no} · ${o.supplierName}", style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f))
      StatusPill(PurchaseOrderBook.label(st), poTone(st))
    }
    val meta = listOfNotNull(
      o.expectedDate?.let { "رسیدن: ${ir.vil3ntec.tohid.formatDate(it)}" },
      o.note.takeIf { it.isNotBlank() },
      o.cancelReason?.takeIf { st == PurchaseOrderCore.Status.CANCELLED }?.let { "دلیلِ لغو: $it" },
    )
    if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = c.muted)
    Spacer(Modifier.height(10.dp))

    //  ردیف‌ها: سفارش، پذیرفته، خراب، باقی‌مانده (PUR-04)
    Row(Modifier.fillMaxWidth()) {
      listOf("کالا" to 3f, "سفارش" to 1f, "پذیرفته" to 1f, "خراب" to 1f, "باقی" to 1f, "فی (؋)" to 1f).forEach { (h, w) ->
        Text(h, style = MaterialTheme.typography.labelSmall, color = c.muted, modifier = Modifier.weight(w))
      }
    }
    HorizontalDivider(color = c.border)
    val prog = o.progress.associateBy { it.lineId }
    o.lines.forEach { l ->
      val p = prog[l.id]
      Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(l.name.ifBlank { names[l.productId] ?: l.productId }, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(3f))
        Text("${qty(PurchaseOrderBook.units(l.qtyMilli))} ${l.unit}", color = c.text, modifier = Modifier.weight(1f))
        Text(qty(PurchaseOrderBook.units(p?.acceptedMilli ?: 0L)), color = c.success, modifier = Modifier.weight(1f))
        Text(qty(PurchaseOrderBook.units(p?.damagedMilli ?: 0L)), color = if ((p?.damagedMilli ?: 0L) > 0) c.danger else c.muted, modifier = Modifier.weight(1f))
        Text(qty(PurchaseOrderBook.units(p?.remainingMilli ?: 0L)), color = if ((p?.remainingMilli ?: 0L) > 0) c.warning else c.muted, modifier = Modifier.weight(1f))
        Text(money(PurchaseOrderBook.asAfn(l.priceMinor)), color = c.muted, modifier = Modifier.weight(1f))
      }
    }
    HorizontalDivider(color = c.border)
    Spacer(Modifier.height(6.dp))
    Text(
      "سفارش ${money(PurchaseOrderBook.asAfn(o.totals.orderedMinor))} ؋ · رسیده ${money(PurchaseOrderBook.asAfn(o.totals.acceptedMinor))} ؋ · باقی‌مانده ${money(PurchaseOrderBook.asAfn(o.totals.remainingMinor))} ؋",
      style = MaterialTheme.typography.bodySmall, color = c.text,
    )

    //  کارها
    Spacer(Modifier.height(12.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      if (perms.receive && PurchaseOrderBook.can(o, PurchaseOrderCore.Action.RECEIVE)) {
        Button(onClick = { onReceive(o) }, enabled = !busy) { Text("دریافتِ کالا") }
      }
      if (perms.manage && PurchaseOrderBook.can(o, PurchaseOrderCore.Action.SEND)) {
        Button(onClick = { act({ repo.send(o.id) }, "سفارش فرستاده شد") }, enabled = !busy) { Text("فرستادن به تأمین‌کننده") }
      }
      if (perms.manage && PurchaseOrderBook.can(o, PurchaseOrderCore.Action.EDIT)) {
        OutlinedButton(onClick = { onEdit(o) }, enabled = !busy) { Text("ویرایش") }
      }
      if (perms.manage && PurchaseOrderBook.can(o, PurchaseOrderCore.Action.CANCEL)) {
        OutlinedButton(onClick = { cancelAsk = true }, enabled = !busy) { Text("لغو", color = c.danger) }
      }
    }

    //  رسیدها و ثبت در دفترِ همین دستگاه
    if (o.receipts.isNotEmpty()) {
      Spacer(Modifier.height(14.dp))
      Text("رسیدها", style = MaterialTheme.typography.labelLarge, color = c.muted, fontWeight = FontWeight.SemiBold)
      o.receipts.forEach { r ->
        val posted = PurchaseOrderBook.isPosted(d, r)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(
              "${ir.vil3ntec.tohid.formatMillis(r.createdAt)} · ${money(PurchaseOrderBook.asAfn(r.totalMinor))} ؋",
              color = c.text, style = MaterialTheme.typography.bodySmall,
            )
            val dmg = r.posted.filter { it.damagedMilli > 0 }
            if (dmg.isNotEmpty()) Text(
              "خراب و برگشت در همان رسید: " + dmg.joinToString("، ") { p ->
                "${o.lines.find { it.id == p.lineId }?.name.orEmpty()} ${qty(PurchaseOrderBook.units(p.damagedMilli))}"
              },
              style = MaterialTheme.typography.labelSmall, color = c.danger,
            )
          }
          if (posted) Text("در دفتر", style = MaterialTheme.typography.labelSmall, color = c.success)
          else TextButton(onClick = { postReceipt(r) }) { Text("ثبت در دفتر") }
        }
      }
      //  PUR-14: سفارش ⇔ رسید ⇔ فاکتورِ دفتر
      val m = remember(d, o) { PurchaseOrderBook.match(d, o) }
      Spacer(Modifier.height(6.dp))
      if (m.matched) Text("تطبیقِ سفارش، رسید و فاکتور: درست", style = MaterialTheme.typography.labelSmall, color = c.success)
      else m.lines.filter { it.issues.isNotEmpty() }.forEach { ml ->
        Text(
          "${o.lines.find { it.id == ml.lineId }?.name ?: names[ml.productId] ?: ml.productId}: " + ml.issues.joinToString("، ") { issueText(it) },
          style = MaterialTheme.typography.labelSmall, color = c.warning,
        )
      }
    }
  }

  if (cancelAsk) {
    var why by remember { mutableStateOf("") }
    val needWhy = st != PurchaseOrderCore.Status.DRAFT
    AlertDialog(
      onDismissRequest = { cancelAsk = false },
      containerColor = c.surfaceSolid,
      title = { Text("لغوِ سفارشِ #${o.no}؟", color = c.text) },
      text = {
        Column {
          Text(
            if (needWhy) "باقی‌ماندهٔ سفارش بسته می‌شود؛ رسیدهای ثبت‌شده می‌مانند." else "پیش‌نویس کنار می‌رود.",
            style = MaterialTheme.typography.bodySmall, color = c.muted,
          )
          OutlinedTextField(value = why, onValueChange = { why = it.take(300) }, label = { Text(if (needWhy) "دلیل (لازم)" else "دلیل") }, singleLine = true)
        }
      },
      confirmButton = {
        TextButton(enabled = !needWhy || why.isNotBlank(), onClick = {
          cancelAsk = false
          act({ repo.cancel(o.id, why.trim()) }, "سفارش لغو شد")
        }) { Text("لغو کن", color = c.danger) }
      },
      dismissButton = { TextButton(onClick = { cancelAsk = false }) { Text("انصراف") } },
    )
  }
}

private fun issueText(i: PurchaseOrderCore.Issue): String = when (i) {
  PurchaseOrderCore.Issue.RECEIVED_OVER_ORDER -> "بیش از سفارش رسیده"
  PurchaseOrderCore.Issue.BILLED_OVER_RECEIVED -> "فاکتور بیش از رسید"
  PurchaseOrderCore.Issue.BILLED_UNDER_RECEIVED -> "رسیده ولی فاکتورش در دفتر نیست"
  PurchaseOrderCore.Issue.PRICE_OVER_ORDER -> "فی بالاتر از سفارش"
  PurchaseOrderCore.Issue.PRICE_UNDER_ORDER -> "فی پایین‌تر از سفارش"
  PurchaseOrderCore.Issue.NOT_ON_ORDER -> "بیرون از سفارش"
}

/* ══════════════════════════ ساختن و ویرایش ══════════════════════════ */

private data class EditRow(val key: Int, val productId: String, val name: String, val unit: String, val qty: String, val price: String)

@Composable
private fun PoEditor(
  d: ShopData,
  repo: PurchaseOrderRepository,
  base: PurchaseOrderDto?,
  toast: (String) -> Unit,
  onSaved: (PurchaseOrderDto) -> Unit,
) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  //  کلیدِ یکتای همین فرم: دو بار زدن یا قطعِ نت همان سفارش است، نه دومی
  val formKey = remember { newId() }
  var supplierId by remember { mutableStateOf(base?.supplierId ?: d.suppliers.firstOrNull()?.id.orEmpty()) }
  var seq by remember { mutableStateOf(base?.lines?.size ?: 0) }
  var rows by remember {
    mutableStateOf(base?.lines.orEmpty().mapIndexed { i, l ->
      EditRow(i, l.productId, l.name, l.unit, poNum(PurchaseOrderBook.units(l.qtyMilli)), poNum(PurchaseOrderBook.asAfn(l.priceMinor)))
    })
  }
  var expected by remember { mutableStateOf(base?.expectedDate.orEmpty()) }
  var note by remember { mutableStateOf(base?.note.orEmpty()) }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }

  fun addProduct(p: Product, qtyMilli: Long? = null, priceMinor: Long? = null) {
    if (rows.any { it.productId == p.id }) { error = "«${p.name}» پیش‌تر در سفارش هست"; return }
    val q = qtyMilli ?: PurchaseOrderBook.suggestedQtyMilli(d, p)
    val pr = priceMinor ?: PurchaseOrderBook.lastPriceMinor(d, supplierId, p)
    rows = rows + EditRow(seq++, p.id, p.name, p.unit, poNum(PurchaseOrderBook.units(q)), poNum(PurchaseOrderBook.asAfn(pr)))
    error = null
  }

  fun addPicks(onlyLow: Boolean) {
    val picks = PurchaseOrderBook.supplierPicks(d, supplierId, onlyLow).filter { pk -> rows.none { it.productId == pk.productId } }
    if (picks.isEmpty()) { toast(if (onlyLow) "کالای کم‌موجودی از این تأمین‌کننده نیست" else "کالای تازه‌ای از این تأمین‌کننده نیست"); return }
    rows = rows + picks.map { pk -> EditRow(seq++, pk.productId, pk.name, pk.unit, poNum(PurchaseOrderBook.units(pk.qtyMilli)), poNum(PurchaseOrderBook.asAfn(pk.priceMinor))) }
  }

  fun save() {
    val supplier = d.suppliers.find { it.id == supplierId } ?: run { error = "تأمین‌کننده را انتخاب کنید"; return }
    val lines = rows.mapIndexed { i, r ->
      val q = r.qty.toDoubleOrNull()
      val p = r.price.toDoubleOrNull()
      if (q == null || q <= 0) { error = "ردیفِ ${i + 1}: مقدار درست نیست"; return }
      if (p == null || p < 0) { error = "ردیفِ ${i + 1}: فی درست نیست"; return }
      PoLineDto(id = "l${i + 1}", productId = r.productId, name = r.name, unit = r.unit, qtyMilli = PurchaseOrderBook.milli(q), priceMinor = PurchaseOrderBook.minor(p))
    }
    val check = PurchaseOrderCore.checkOrder(PurchaseOrderCore.Order(supplier.id, PurchaseOrderCore.State.DRAFT,
      lines.map { PurchaseOrderCore.Line(it.id, it.productId, it.qtyMilli, it.priceMinor) }))
    if (check != null) { error = if (check.code == PurchaseOrderCore.Code.LINES_REQUIRED) "دست‌کم یک کالا لازم است" else "سفارش درست نیست (${check.code})"; return }
    //  تاریخِ ذخیره‌شده همیشه ASCII `YYYY-MM-DD` (LOC-05)
    val exp = ir.vil3ntec.tohid.latinDigits(expected).trim().ifBlank { null }
    if (exp != null && !ir.vil3ntec.tohid.core.stock.StockCore.isoDateOk(exp)) { error = "تاریخِ رسیدن را به شکلِ ۲۰۲۶-۱۱-۰۵ بنویسید"; return }
    val draft = PurchaseOrderRepository.Draft(supplier.id, supplier.name, lines, exp, note.trim())
    busy = true; error = null
    scope.launch {
      val r = if (base == null) repo.create(formKey, draft) else repo.update(base.id, base.version, draft)
      busy = false
      r.valueOrNull()?.let { toast(if (base == null) "پیش‌نویسِ سفارشِ #${it.no} ساخته شد" else "ذخیره شد"); onSaved(it) }
        ?: run { error = r.errorMessage() }
    }
  }

  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
    Text(if (base == null) "سفارشِ خریدِ تازه" else "ویرایشِ سفارشِ #${base.no}", style = MaterialTheme.typography.titleSmall, color = c.text)
    Spacer(Modifier.height(8.dp))
    if (d.suppliers.isEmpty()) EmptyNote("اول یک تأمین‌کننده بسازید.")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      d.suppliers.sortedBy { it.name }.forEach { s ->
        FilterChip(selected = supplierId == s.id, onClick = { supplierId = s.id }, label = { Text(s.name) }, shape = Shape.chip)
      }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = { addPicks(onlyLow = true) }, enabled = supplierId.isNotBlank()) { Text("کم‌موجودهای این تأمین‌کننده") }
      OutlinedButton(onClick = { addPicks(onlyLow = false) }, enabled = supplierId.isNotBlank()) { Text("همهٔ کالاهای این تأمین‌کننده") }
    }
    Spacer(Modifier.height(8.dp))
    rows.forEachIndexed { i, r ->
      Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${i + 1}. ${r.name}", color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        NumberField(r.qty, { v -> rows = rows.map { if (it.key == r.key) it.copy(qty = v) else it } }, "مقدار ${r.unit}", Modifier.width(120.dp))
        NumberField(r.price, { v -> rows = rows.map { if (it.key == r.key) it.copy(price = v) else it } }, "فی (؋)", Modifier.width(120.dp))
        TextButton(onClick = { rows = rows.filterNot { it.key == r.key } }) { Text("حذف") }
      }
    }
    PoProductPicker(d, "افزودنِ کالا — نام یا بارکد") { addProduct(it) }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(value = expected, onValueChange = { expected = it.take(10) }, label = { Text("تاریخِ رسیدن (اختیاری)") }, singleLine = true, modifier = Modifier.weight(1f))
      OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, label = { Text("یادداشت") }, singleLine = true, modifier = Modifier.weight(2f))
    }
    val total = rows.sumOf { (it.qty.toDoubleOrNull() ?: 0.0) * (it.price.toDoubleOrNull() ?: 0.0) }
    Spacer(Modifier.height(6.dp))
    Text("جمعِ سفارش: ${money(total)} ؋ · ${rows.size} ردیف", style = MaterialTheme.typography.bodySmall, color = c.text)
    error?.let { Text(it, color = c.danger, style = MaterialTheme.typography.bodySmall) }
    Spacer(Modifier.height(10.dp))
    Button(onClick = { save() }, enabled = !busy && rows.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
      Text(if (base == null) "ذخیرهٔ پیش‌نویس" else "ذخیره")
    }
  }
}

@Composable
private fun PoProductPicker(d: ShopData, hint: String, onPick: (Product) -> Unit) {
  var term by remember { mutableStateOf("") }
  val c = Shop.colors
  OutlinedTextField(value = term, onValueChange = { term = it }, placeholder = { Text(hint) }, singleLine = true, modifier = Modifier.fillMaxWidth())
  val t = ir.vil3ntec.tohid.latinDigits(term).trim()
  if (t.isNotEmpty()) {
    val hits = d.products.filter { it.active && (TextNormalizer.matches(it.name, t) || t in it.barcodes) }.take(6)
    if (hits.isEmpty()) Text("کالایی پیدا نشد", style = MaterialTheme.typography.labelSmall, color = c.muted, modifier = Modifier.padding(6.dp))
    hits.forEach { p ->
      Row(
        Modifier.fillMaxWidth().clip(Shape.button).clickable { onPick(p); term = "" }.padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(p.name, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("موجودی ${qty(ShopStore.stock(d, p.id))} ${p.unit}", style = MaterialTheme.typography.labelSmall, color = c.muted)
      }
    }
  }
}

/* ══════════════════════════ دریافت (PUR-04) ══════════════════════════ */

private data class RecvRow(val received: String = "", val damaged: String = "", val price: String = "")

@Composable
private fun PoReceive(
  store: ShopStore,
  d: ShopData,
  host: PurchaseOrderHost,
  perms: PoPermsDto,
  order: PurchaseOrderDto,
  toast: (String) -> Unit,
  onDone: () -> Unit,
) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  val repo = host.repo
  //  کلیدِ یکتای همین رسید: تلاشِ دوباره همان رسید است (سرور «تکراری» می‌گوید)
  val receiptId = remember { newId() }
  val prog = remember(order) { order.progress.associateBy { it.lineId } }
  var rows by remember {
    mutableStateOf(order.lines.associate { it.id to RecvRow(price = poNum(PurchaseOrderBook.asAfn(it.priceMinor))) })
  }
  var allowOver by remember { mutableStateOf(false) }
  var note by remember { mutableStateOf("") }
  var scan by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  val index = remember(d) { ShopStore.barcodeIndex(d) }

  /** اسکن: یک واحدِ کالای همان بارکد به «رسیده» */
  fun scanned(code: String): Boolean {
    val pid = index[code.trim()]
    val line = order.lines.find { it.productId == pid }
    if (line == null) { toast("«$code» در این سفارش نیست"); return true }
    val cur = rows[line.id] ?: RecvRow()
    val next = (cur.received.toDoubleOrNull() ?: 0.0) + 1.0
    rows = rows + (line.id to cur.copy(received = poNum(next)))
    error = null
    return true
  }

  val owner = remember { Any() }
  val latestScan by rememberUpdatedState<(String) -> Boolean> { code -> scanned(code) }
  DisposableEffect(Unit) {
    ir.vil3ntec.tohid.scan.ScanRouter.register(owner) { code -> latestScan(code) }
    onDispose { ir.vil3ntec.tohid.scan.ScanRouter.unregister(owner) }
  }

  fun submit() {
    val lines = ArrayList<PoReceiptLineDto>()
    for ((i, l) in order.lines.withIndex()) {
      val r = rows[l.id] ?: continue
      val rec = r.received.ifBlank { "0" }.toDoubleOrNull()
      val dmg = r.damaged.ifBlank { "0" }.toDoubleOrNull()
      val price = r.price.toDoubleOrNull()
      if (rec == null || rec < 0 || dmg == null || dmg < 0) { error = "ردیفِ ${i + 1}: مقدار درست نیست"; return }
      if (dmg > rec) { error = "ردیفِ ${i + 1}: خراب بیش از رسیده است"; return }
      if (price == null || price < 0) { error = "ردیفِ ${i + 1}: فی درست نیست"; return }
      if (rec == 0.0) continue
      lines += PoReceiptLineDto(l.id, PurchaseOrderBook.milli(rec), PurchaseOrderBook.milli(dmg), PurchaseOrderBook.minor(price))
    }
    if (lines.isEmpty()) { error = "هیچ مقداری وارد نشده است"; return }
    busy = true; error = null
    scope.launch {
      val res = repo.receive(order.id, receiptId, lines, allowOver, note.trim())
      val got = res.valueOrNull()
      if (got == null) { busy = false; error = res.errorMessage(); return@launch }
      //  رسید در سرور ثبت شد ⇒ فاکتورِ خریدِ دفتر (شناسه‌های قطعی؛ تکرار بی اثر)
      var msg = "رسید ثبت شد"
      val wrote = store.update { cur ->
        when (val p = PurchaseOrderBook.post(cur, got.order, got.receipt, todayIso(), host.now())) {
          is PurchaseOrderBook.Posting.Ok -> { msg = "رسید ثبت شد · فاکتورِ خریدِ #${p.invoiceNo} در دفتر"; p.data }
          PurchaseOrderBook.Posting.Already -> null
          is PurchaseOrderBook.Posting.Failed -> { msg = "رسید ثبت شد، ولی دفتر نه: ${p.message}"; null }
        }
      }
      busy = false
      toast(if (wrote == false) "رسید ثبت شد، ولی دفتر نوشته نشد (اشتراک یا دیسک) — از «ثبت در دفتر» دوباره" else msg)
      onDone()
    }
  }

  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
    Text("دریافتِ کالا — سفارشِ #${order.no} · ${order.supplierName}", style = MaterialTheme.typography.titleSmall, color = c.text)
    Text("کالا را اسکن کنید یا مقدار را بنویسید. خراب‌ها پذیرفته نمی‌شوند و باقی‌ماندهٔ سفارش باز می‌ماند.", style = MaterialTheme.typography.labelSmall, color = c.muted)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
      value = scan,
      onValueChange = { v -> if (v.contains('\n')) { scanned(v.trim()); scan = "" } else scan = v.take(64) },
      singleLine = false, maxLines = 1,
      label = { Text("بارکد") },
      keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (scan.isNotBlank()) scanned(scan.trim()); scan = "" }),
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    order.lines.forEach { l ->
      val r = rows[l.id] ?: RecvRow()
      val left = prog[l.id]?.remainingMilli ?: l.qtyMilli
      Column(Modifier.padding(vertical = 4.dp)) {
        Text("${l.name} — باقی‌مانده ${qty(PurchaseOrderBook.units(left))} ${l.unit}", color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          NumberField(r.received, { v -> rows = rows + (l.id to (rows[l.id] ?: RecvRow()).copy(received = v)); error = null }, "رسیده", Modifier.weight(1f))
          NumberField(r.damaged, { v -> rows = rows + (l.id to (rows[l.id] ?: RecvRow()).copy(damaged = v)); error = null }, "خراب", Modifier.weight(1f))
          if (perms.manage) NumberField(r.price, { v -> rows = rows + (l.id to (rows[l.id] ?: RecvRow()).copy(price = v)); error = null }, "فی (؋)", Modifier.weight(1f))
        }
      }
    }
    if (perms.manage) Row(verticalAlignment = Alignment.CenterVertically) {
      Checkbox(checked = allowOver, onCheckedChange = { allowOver = it })
      Text("بیش از سفارش را هم می‌پذیرم (اختلافِ «اضافه» ثبت می‌شود)", style = MaterialTheme.typography.bodySmall, color = c.text)
    }
    OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, label = { Text("یادداشت") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    error?.let { Text(it, color = c.danger, style = MaterialTheme.typography.bodySmall) }
    Spacer(Modifier.height(10.dp))
    Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "در حالِ ثبت…" else "ثبتِ رسید") }
  }
}

/** عدد برای کادرِ ورودی: بی اعشارِ بی‌مورد و با رقمِ لاتین (همان قاعدهٔ کادرهای انبار) */
private fun poNum(value: Double): String =
  if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()
