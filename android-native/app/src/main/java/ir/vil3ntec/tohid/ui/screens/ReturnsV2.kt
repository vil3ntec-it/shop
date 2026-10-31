package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.vil3ntec.tohid.core.text.TextNormalizer
import ir.vil3ntec.tohid.data.ReturnFlow
import ir.vil3ntec.tohid.data.ReturnSettings
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.SegmentTab
import ir.vil3ntec.tohid.ui.components.common.SegmentedTabs
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/*
 *  برگشت و تعویض — صفحهٔ گوشی و کامپیوتر (RET-01..08، 10، 12، 13؛ S13–S15).
 *
 *  فقط با `ReturnSettings.enabled`؛ خاموش ⇒ `SalesHistory` همان `ReturnDialog`ِ
 *  امروز را باز می‌کند. حساب همه در `ReturnFlow` است؛ این‌جا فقط فرم و رسید.
 */

/**
 *  ⛔ قلابِ تأیید (STF-02، RET-04/07/10).
 *
 *  وقتی برگشت تأیید لازم دارد (بی رسید یا ضایعات برای شاگرد، بالای سقف برای همه
 *  جز صاحب) و نقشِ همین کاربر بس نیست، این‌جا صدا زده می‌شود: پنجرهٔ «درخواستِ
 *  تأیید» با PINِ مدیر یا صاحب (`ReturnApprovalRequest`، همان `ApprovalDialog` ِ تخفیف)
 *  و جوابِ `ReturnFlow.Approval(by, role, slip)` با «برگهٔ امضاشده»ی همین سند. فرمِ
 *  قدیم و برگهٔ نسخهٔ ۳ هر دو همین را می‌خوانند. `null` (فقط آزمون/عکس) ⇒ فرم به‌جای
 *  دکمه می‌گوید چه کسی باید ثبت کند.
 */
object ReturnApproval {
  data class Need(
    val perm: String = "returns.create",
    /** همان «سند»ِ برگهٔ امضاشده: `returns/<docId>` */
    val doc: String,
    val amountMinor: Long,
    val reasons: List<String>,
    /** `manager` یا `owner` */
    val approver: String,
  )

  var requester: (@Composable (need: Need, onResult: (ReturnFlow.Approval?) -> Unit) -> Unit)? =
    { need, onResult -> ir.vil3ntec.tohid.ui.components.staff.ReturnApprovalRequest(need, onResult) }

  fun whyText(reasons: List<String>): String = reasons.joinToString("، ") {
    when (it) {
      ReturnFlow.WHY_NO_RECEIPT -> "بی رسید"
      ReturnFlow.WHY_DAMAGED -> "ضایعات"
      ReturnFlow.WHY_OVER_LIMIT -> "بالای سقف"
      else -> "مجوز"
    }
  }

  fun approverText(role: String): String = if (role == "owner") "صاحبِ دکان" else "مدیر یا صاحبِ دکان"
}

/**
 *  فقط برای عکس‌های CI (`DevShot`، `ret:first|none|close`) — مثلِ `DeskCustomersShot`.
 *  فرمِ تازه را روی همان دفترِ نمایشی باز می‌کند؛ تنظیمِ واقعیِ دکان دست نمی‌خورد.
 */
object ReturnsShot {
  val open = mutableStateOf<String?>(null)
}

private data class FreeRow(val productId: String, val qty: String = "1", val price: String, val disposition: String = ReturnFlow.RESTOCK)

/**
 *  فرمِ برگشت: با رسید ([sale]) یا بی رسید (`sale == null`)، با تعویضِ اختیاری.
 *  `onPosted(docId)` پس از ذخیره — صدازننده رسیدِ برگشت را نشان می‌دهد.
 */
@Composable
fun ReturnFlowDialog(
  d: ShopData,
  sale: Sale?,
  store: ShopStore,
  onDismiss: () -> Unit,
  onPosted: (docId: String, note: String) -> Unit,
  /** فقط عکسِ CI: یک واحد از قلمِ اول و دلیلِ اول از پیش پر */
  prefill: Boolean = false,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val role = remember { ShopRole.current(context) }
  val layers = remember { ShopRole.layers(context) }
  val limit = remember { ReturnSettings.limit(context) }
  //  RET-08 — فهرستِ دلیل‌های همین دکان (تنظیمات)؛ ثبت فقط با یکی از همین‌ها
  val reasons = remember { ReturnSettings.reasons(context) }
  val me = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("").trim() }
  //  شناسه‌های سند و فروشِ تعویض پیش از نخستین تلاش (§۱۵.۲ بندِ ۱)
  val docId = remember { newId() }
  val saleKey = remember { newId() }

  val items = remember(d, sale) { if (sale == null) emptyList() else d.saleItems.filter { it.saleId == sale.id && SalesEngine.returnable(it) > 0 } }
  var qtys by remember { mutableStateOf<Map<String, String>>(if (prefill) items.take(1).associate { it.id to "1" } else mapOf()) }
  var disp by remember { mutableStateOf(mapOf<String, String>()) }
  var free by remember { mutableStateOf(listOf<FreeRow>()) }
  var reason by remember { mutableStateOf<String?>(if (prefill) reasons.first() else null) }
  var mode by remember { mutableStateOf(ReturnFlow.ORIGINAL) }
  var customerId by remember { mutableStateOf(sale?.debtorId) }
  var exchange by remember { mutableStateOf(false) }
  var cart by remember { mutableStateOf(listOf<SalesEngine.CartLine>()) }
  var approval by remember { mutableStateOf<ReturnFlow.Approval?>(null) }
  var asking by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  //  تا ذخیره تمام نشده، دکمه دوباره کار نمی‌کند (دو بار زدن = یک سند)
  var saving by remember { mutableStateOf(false) }

  fun number(text: String?): Double = text?.let { ir.vil3ntec.tohid.latinDigits(it).toDoubleOrNull() } ?: 0.0

  val quote = remember(d, sale, qtys, disp, free) {
    if (sale != null) ReturnFlow.quote(d, sale.id, items.associate { it.id to ReturnFlow.Pick(number(qtys[it.id]), disp[it.id] ?: ReturnFlow.RESTOCK) })
    else ReturnFlow.quoteNoReceipt(d, free.map { ReturnFlow.FreeLine(it.productId, number(it.qty), number(it.price), it.disposition) })
  }
  val q = (quote as? ReturnFlow.Outcome.Ok)?.value
  val total = q?.total ?: 0.0
  val damaged = q?.lines?.any { it.disposition == ReturnFlow.DAMAGED } == true
  //  RET-10: سقفِ نقش تجمعیِ هر فاکتور است، همان سرور — برگشت‌های پیشینِ همین فاکتور + همین سند
  val priorMinor = remember(d, sale) { ReturnFlow.priorMinor(d, sale?.id) }
  val perm = remember(total, priorMinor) { ReturnFlow.permDecision(role, layers, ReturnFlow.minor(total), priorMinor) }
  val gate = ReturnFlow.gate(role, perm, ReturnFlow.minor(total), sale == null, damaged, ReturnFlow.minor(limit))
  val creditTo = if (mode == ReturnFlow.STORE_CREDIT) customerId else null
  val prior = remember(d, sale) { sale?.let { s -> d.saleReturns.filter { it.saleId == s.id } }.orEmpty() }
  val split = (ReturnFlow.split(sale, total, mode, me, creditTo, prior) as? ReturnFlow.Outcome.Ok)?.value
  val cartTotal = remember(d, cart) { SalesEngine.cartTotal(d, cart) }
  val approved = gate.decision == "allow" || (approval != null && ReturnFlow.canApprove(gate, approval!!.role))

  val c = Shop.colors
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    DialogEntry {
      Surface(
        color = c.surfaceSolid,
        shape = Shape.dialog,
        modifier = Modifier.padding(16.dp).widthIn(max = 640.dp).fillMaxWidth(),
      ) {
        Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
          Text(
            if (sale != null) "برگشت — فاکتور #${sale.invoiceLabel() ?: "0"}" else "برگشتِ بی رسید",
            style = MaterialTheme.typography.titleMedium, color = c.text,
          )
          if (sale != null) Text(
            "${ir.vil3ntec.tohid.formatDate(sale.date)} · ${money(sale.finalTotal)} ؋" +
              (sale.debtorId?.let { id -> d.debtors.find { it.id == id }?.name }?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.labelSmall, color = c.muted,
          )
          Spacer(Modifier.height(12.dp))

          if (sale?.status == "cancelled") {
            //  RET-13 — فاکتورِ لغوشده کلاً برگشته است
            StatusPill("این فاکتور لغو شده است؛ برگشت نمی‌پذیرد", PillTone.DANGER)
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("بستن") }
            return@Column
          }

          /* ----------------------------- اقلام ----------------------------- */
          Section("کالاهای برگشتی") {
            if (sale != null) {
              if (items.isEmpty()) EmptyNote("همهٔ اقلامِ این فاکتور برگشته است.")
              items.forEach { item ->
                val left = SalesEngine.returnable(item)
                val unit = d.products.find { it.id == item.productId }?.unit.orEmpty()
                Column(Modifier.padding(vertical = 6.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                      Text(SalesEngine.itemName(d, item), style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                      Text("قابلِ برگشت: ${qty(left)} $unit · فی ${money(item.totalPrice / item.quantity.coerceAtLeast(1e-9))}", style = MaterialTheme.typography.labelSmall, color = c.muted)
                    }
                    NumberField(
                      value = qtys[item.id].orEmpty(),
                      onValueChange = { qtys = qtys + (item.id to it); error = null },
                      label = "مقدار",
                      modifier = Modifier.width(110.dp),
                    )
                  }
                  if (number(qtys[item.id]) > 0 && item.productId.isNotBlank()) DispositionTabs(disp[item.id] ?: ReturnFlow.RESTOCK) { disp = disp + (item.id to it) }
                }
              }
            } else {
              free.forEachIndexed { i, row ->
                val p = d.products.find { it.id == row.productId }
                Column(Modifier.padding(vertical = 6.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p?.name ?: "(کالا)", style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { free = free.filterIndexed { k, _ -> k != i } }) { Text("حذف") }
                  }
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(row.qty, { v -> free = free.mapIndexed { k, r -> if (k == i) r.copy(qty = v) else r } }, "مقدار", Modifier.weight(1f))
                    NumberField(row.price, { v -> free = free.mapIndexed { k, r -> if (k == i) r.copy(price = v) else r } }, "فی (؋)", Modifier.weight(1f))
                  }
                  DispositionTabs(row.disposition) { v -> free = free.mapIndexed { k, r -> if (k == i) r.copy(disposition = v) else r } }
                }
              }
              ProductPicker(d, hint = "افزودنِ کالا — نام یا بارکد") { p ->
                free = free + FreeRow(p.id, price = money(p.salePrice).replace(",", ""))
              }
            }
          }

          /* ----------------------------- دلیل ----------------------------- */
          Section("دلیلِ برگشت (اجباری)") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              reasons.forEach { r ->
                FilterChip(selected = reason == r, onClick = { reason = r }, label = { Text(r) }, shape = Shape.chip)
              }
            }
          }

          /* -------------------------- بازپرداخت -------------------------- */
          Section("بازپرداخت") {
            SegmentedTabs(
              tabs = buildList {
                add(SegmentTab(ReturnFlow.ORIGINAL, if (sale != null) "روشِ اصلی" else "نقد"))
                if (sale != null) add(SegmentTab(ReturnFlow.CASH, "نقد"))
                add(SegmentTab(ReturnFlow.STORE_CREDIT, "اعتبارِ فروشگاه"))
              },
              selected = mode,
              onSelect = { mode = it },
            )
            if (mode == ReturnFlow.STORE_CREDIT && sale?.debtorId == null) {
              Spacer(Modifier.height(8.dp))
              CustomerPicker(d, customerId) { customerId = it }
            }
            if (split != null && total > 0) {
              Spacer(Modifier.height(8.dp))
              if (split.account > 0) MoneyLine("کم از بدهیِ همین فاکتور", split.account)
              if (split.credit > 0) MoneyLine("اعتبارِ فروشگاه (طلبِ مشتری)", split.credit)
              split.methods.forEach { (m, a) -> MoneyLine("پس به ${ReturnFlow.methodName(m)} (نه از دخل)", a) }
              if (split.cash > 0) MoneyLine("نقد از دخل", split.cash)
            }
          }

          /* ----------------------------- تعویض ----------------------------- */
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Text("تعویض با کالای دیگر", style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f))
            Switch(checked = exchange, onCheckedChange = { exchange = it })
          }
          if (exchange) Section("کالاهای تازه") {
            cart.forEach { line ->
              val p = d.products.find { it.id == line.productId }
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Column(Modifier.weight(1f)) {
                  Text(p?.name ?: "(کالا)", style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text("${money(SalesEngine.linePrice(d, line))} ؋", style = MaterialTheme.typography.labelSmall, color = c.muted)
                }
                val step = SalesEngine.cartStep(p?.unit.orEmpty())
                OutlinedButton(onClick = { cart = SalesEngine.setCartQty(cart, line.productId, line.quantity - step) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("−") }
                Text(qty(line.quantity), modifier = Modifier.padding(horizontal = 10.dp), color = c.text)
                OutlinedButton(onClick = { cart = SalesEngine.setCartQty(cart, line.productId, line.quantity + step) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("+") }
              }
            }
            ProductPicker(d, hint = "کالای تازه — نام یا بارکد") { p -> cart = SalesEngine.addToCart(cart, p.id, 1.0) }
          }

          /* ----------------------------- جمع ----------------------------- */
          Spacer(Modifier.height(8.dp))
          Column(
            Modifier.fillMaxWidth().clip(Shape.card).background(c.bg).padding(12.dp),
          ) {
            MoneyLine("مبلغِ برگشت", total, strong = !exchange)
            if (exchange) {
              MoneyLine("خریدِ تازه", cartTotal)
              val due = cartTotal - (split?.cash ?: 0.0)
              MoneyLine(if (due >= 0) "دریافت از مشتری" else "پرداخت به مشتری", kotlin.math.abs(due), strong = true)
            }
            if (gate.decision != "allow") {
              Spacer(Modifier.height(8.dp))
              val tone = if (gate.decision == "deny") PillTone.DANGER else if (approved) PillTone.SUCCESS else PillTone.AWAITING
              StatusPill(
                when {
                  gate.decision == "deny" -> "این برگشت برای شما مجاز نیست"
                  approved -> "تأیید شد — ${approval?.by}"
                  else -> "تأییدِ ${ReturnApproval.approverText(gate.approver)} لازم است (${ReturnApproval.whyText(gate.reasons)})"
                },
                tone,
              )
              if (gate.decision == "approve" && !approved) {
                Spacer(Modifier.height(6.dp))
                if (ReturnApproval.requester != null) {
                  OutlinedButton(onClick = { asking = true }) { Text("درخواستِ تأیید") }
                } else {
                  Text(
                    "این برگشت را ${ReturnApproval.approverText(gate.approver)} با حسابِ خودش ثبت کند.",
                    style = MaterialTheme.typography.labelSmall, color = c.muted,
                  )
                }
              }
            }
          }

          (quote as? ReturnFlow.Outcome.Bad)?.takeIf { it.code != ReturnFlow.Code.NOTHING_TO_RETURN }?.let {
            Spacer(Modifier.height(8.dp)); StatusPill(it.message, PillTone.DANGER)
          }
          error?.let { Spacer(Modifier.height(8.dp)); StatusPill(it, PillTone.DANGER) }

          Spacer(Modifier.height(16.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("انصراف") }
            Button(
              enabled = !saving && q != null && reason != null && approved && (!exchange || cart.isNotEmpty()),
              onClick = {
                val req = ReturnFlow.Request(
                  docId = docId, saleId = sale?.id,
                  picks = items.associate { it.id to ReturnFlow.Pick(number(qtys[it.id]), disp[it.id] ?: ReturnFlow.RESTOCK) },
                  free = free.map { ReturnFlow.FreeLine(it.productId, number(it.qty), number(it.price), it.disposition) },
                  reason = reason.orEmpty(), reasons = reasons, mode = mode, customerId = creditTo,
                  by = me, role = role, perm = perm, limit = limit, approval = approval,
                )
                val now = System.currentTimeMillis()
                //  دفترِ تازه و پیامِ موفقیت، یا خطا — «ثبت شد» فقط پس از نوشتنِ واقعی
                val built: Pair<ShopData, String>? = if (exchange) {
                  when (val r = ReturnFlow.exchange(
                    d, req, cart, SalesEngine.Checkout(seller = me), saleKey, todayIso(), now, ::newId,
                    series = ir.vil3ntec.tohid.sync.DocSeries.current(context),
                  )) {
                    is ReturnFlow.Outcome.Bad -> { error = r.message; null }
                    is ReturnFlow.Outcome.Ok -> {
                      val due = r.value.due
                      r.value.data to (if (due >= 0) "تعویض ثبت شد — دریافت ${money(due)} ؋" else "تعویض ثبت شد — پرداخت ${money(-due)} ؋")
                    }
                  }
                } else {
                  when (val r = ReturnFlow.post(d, req, todayIso(), now, ::newId)) {
                    is ReturnFlow.Outcome.Bad -> { error = r.message; null }
                    is ReturnFlow.Outcome.Ok -> r.value.data to "برگشت ثبت شد — ${money(r.value.total)} ؋"
                  }
                }
                if (built != null) {
                  saving = true
                  scope.launch {
                    //  قفلِ نرمِ اشتراک یا خطای دیسک ⇒ `false`: هیچ «ثبت شد»ی نیست و فرم باز می‌ماند
                    if (store.save(built.first)) onPosted(docId, built.second)
                    else error = "ثبت نشد — دفتر نوشته نشد (اشتراک یا حافظه). چیزی ثبت نشده است."
                    saving = false
                  }
                }
              },
              modifier = Modifier.weight(1f),
            ) { Text(if (exchange) "ثبتِ تعویض" else "ثبتِ برگشت") }
          }
        }
      }
    }
  }

  val requester = ReturnApproval.requester
  if (asking && requester != null) {
    requester(
      ReturnApproval.Need(doc = "returns/$docId", amountMinor = ReturnFlow.minor(total), reasons = gate.reasons, approver = gate.approver),
    ) { got -> asking = false; approval = got }
  }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
  val c = Shop.colors
  Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = c.muted, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    content()
  }
}

@Composable
private fun DispositionTabs(value: String, onChange: (String) -> Unit) {
  Spacer(Modifier.height(6.dp))
  SegmentedTabs(
    tabs = listOf(SegmentTab(ReturnFlow.RESTOCK, "به موجودی"), SegmentTab(ReturnFlow.DAMAGED, "ضایعات")),
    selected = value,
    onSelect = onChange,
  )
}

@Composable
private fun MoneyLine(label: String, value: Double, strong: Boolean = false) {
  val c = Shop.colors
  Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
    Text("${money(value)} ؋", style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium, color = c.text)
  }
}

/** یک کادرِ جست‌وجو برای افزودنِ کالا — بی رسید یا کالای تازهٔ تعویض */
@Composable
private fun ProductPicker(d: ShopData, hint: String, onPick: (ir.vil3ntec.tohid.data.Product) -> Unit) {
  var term by remember { mutableStateOf("") }
  val c = Shop.colors
  OutlinedTextField(
    value = term,
    onValueChange = { term = it },
    placeholder = { Text(hint) },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
  )
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
        Text("${money(p.salePrice)} ؋", style = MaterialTheme.typography.labelSmall, color = c.muted)
      }
    }
  }
}

@Composable
private fun CustomerPicker(d: ShopData, selected: String?, onPick: (String) -> Unit) {
  val c = Shop.colors
  if (d.debtors.isEmpty()) {
    Text("اول مشتری را در «قرض‌داران» بسازید.", style = MaterialTheme.typography.labelSmall, color = c.muted)
    return
  }
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    d.debtors.sortedBy { it.name }.forEach { who ->
      FilterChip(selected = selected == who.id, onClick = { onPick(who.id) }, label = { Text(who.name) }, shape = Shape.chip)
    }
  }
}

/* ============================ تنظیمات ============================ */

/** عنوانِ بخش — همان که ستونِ «تنظیمات»ِ کامپیوتر (`AppRoot`) فهرست می‌کند */
const val RETURN_SETTINGS_TITLE = "برگشت و تعویض"

/**
 *  کلیدِ «برگشت و تعویضِ تازه» و سقفِ بی تأیید — فقط صاحب و مدیر (صفحهٔ تنظیمات
 *  برای شاگرد باز نمی‌شود). پیش‌فرض خاموش = همان مرجوعیِ امروز.
 */
@Composable
fun ReturnSettingsSection() {
  val context = LocalContext.current
  var on by remember { mutableStateOf(ReturnSettings.enabled(context)) }
  var limit by remember { mutableStateOf(ReturnSettings.limit(context).let { if (it > 0) money(it).replace(",", "") else "" }) }
  //  RET-08 — متنِ کادر همان نوشتهٔ کاربر می‌ماند (خطِ خالیِ وسطِ نوشتن پاک نمی‌شود)؛ ذخیره پاک‌شده است
  var reasonsText by remember { mutableStateOf(ir.vil3ntec.tohid.core.returns.ReturnReasons.format(ReturnSettings.reasons(context))) }
  val c = Shop.colors
  SettingsSection(
    icon = Icons.Filled.SwapHoriz,
    title = RETURN_SETTINGS_TITLE,
    subtitle = if (on) "جریانِ تازه روشن است" else "همان مرجوعیِ ساده",
    tint = if (on) c.success else c.muted,
  ) {
    Text(
      "روشن: برگشت با اسکنِ QRِ رسید یا جست‌وجو، دلیلِ اجباری، «به موجودی» یا «ضایعات»، بازپرداخت به روشِ اصلی، نقد یا اعتبارِ فروشگاه، تعویض در یک سند، برگشتِ بی رسید با تأیید، و رسیدِ چاپیِ برگشت. رسیدِ فروش هم QR می‌گیرد. خاموش: همان پنجرهٔ مرجوعیِ امروز.",
      style = MaterialTheme.typography.labelSmall,
      color = c.muted,
    )
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to "خاموش", "on" to "روشن"),
      selected = if (on) "on" else "off",
      onSelect = { on = it == "on"; ReturnSettings.setEnabled(context, on) },
    )
    if (on) {
      Spacer(Modifier.height(12.dp))
      Text(
        "سقفِ برگشت بی تأییدِ صاحبِ دکان (برای مدیر و شاگرد)؛ خالی = بی سقف. شاگرد برای برگشتِ بی رسید و ضایعات همیشه تأییدِ مدیر می‌خواهد.",
        style = MaterialTheme.typography.labelSmall,
        color = c.muted,
      )
      Spacer(Modifier.height(6.dp))
      NumberField(
        value = limit,
        onValueChange = { v ->
          limit = v
          ReturnSettings.setLimit(context, v.toDoubleOrNull() ?: 0.0)
        },
        label = "سقف (؋)",
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(12.dp))
      Text(
        "دلیل‌های برگشت — هر خط یک دلیل (حداکثر ${ir.vil3ntec.tohid.core.returns.ReturnReasons.MAX_COUNT}، هر کدام تا ${ir.vil3ntec.tohid.core.returns.ReturnReasons.MAX_LENGTH} نویسه). صندوق فقط از همین فهرست دلیل می‌پذیرد؛ خالی = فهرستِ پیش‌فرض.",
        style = MaterialTheme.typography.labelSmall,
        color = c.muted,
      )
      Spacer(Modifier.height(6.dp))
      OutlinedTextField(
        value = reasonsText,
        onValueChange = { v -> reasonsText = v; ReturnSettings.setReasons(context, v) },
        label = { Text("دلیل‌ها") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

/* ============================ رسیدِ برگشت (RET-12) ============================ */

@Composable
fun ReturnReceiptDialog(d: ShopData, docId: String, onDismiss: () -> Unit, onMessage: (String) -> Unit) {
  val doc = remember(d, docId) { ReturnFlow.doc(d, docId) }
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var printer by remember { mutableStateOf(false) }
  val c = Shop.colors
  if (doc == null) { onDismiss(); return }

  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
      Surface(color = c.surfaceSolid, shape = Shape.dialog, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
          Text("رسیدِ برگشت ${doc.label}", style = MaterialTheme.typography.titleMedium, color = c.text)
          Text(
            (doc.sale?.let { "از فاکتور #${it.invoiceLabel() ?: "0"}" } ?: "بی رسید") + " · " + ir.vil3ntec.tohid.formatDate(doc.date),
            style = MaterialTheme.typography.labelSmall, color = c.muted,
          )
          Spacer(Modifier.height(10.dp))
          doc.rows.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text(ReturnFlow.rowName(d, r), style = MaterialTheme.typography.bodyMedium, color = c.text)
                Text("${qty(r.quantity)} · ${ReturnFlow.dispositionName(r.disposition)}", style = MaterialTheme.typography.labelSmall, color = c.muted)
              }
              Text(money(r.amount), style = MaterialTheme.typography.bodyMedium, color = c.text)
            }
          }
          doc.exchange?.let { ex ->
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = c.border)
            Text("خریدِ تازه — فاکتور #${ex.invoiceLabel() ?: "0"}", style = MaterialTheme.typography.labelLarge, color = c.muted)
            d.saleItems.filter { it.saleId == ex.id }.forEach { si ->
              Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text("${SalesEngine.itemName(d, si)} × ${qty(si.quantity)}", color = c.text, modifier = Modifier.weight(1f))
                Text(money(si.totalPrice), color = c.text)
              }
            }
          }
          Spacer(Modifier.height(8.dp))
          Column(Modifier.fillMaxWidth().clip(Shape.card).background(c.bg).padding(12.dp)) {
            MoneyLine("مبلغِ برگشت", doc.total, strong = doc.exchange == null)
            if (doc.split.account > 0) MoneyLine("کم از بدهی", doc.split.account)
            if (doc.split.credit > 0) MoneyLine("اعتبارِ فروشگاه", doc.split.credit)
            doc.split.methods.forEach { (m, a) -> MoneyLine("پس به ${ReturnFlow.methodName(m)}", a) }
            if (doc.split.cash > 0) MoneyLine("نقد", doc.split.cash)
            doc.due?.let { due -> MoneyLine(if (due >= 0) "دریافت از مشتری" else "پرداخت به مشتری", kotlin.math.abs(due), strong = true) }
          }
          Spacer(Modifier.height(6.dp))
          Text("دلیل: ${doc.reason}", style = MaterialTheme.typography.labelSmall, color = c.muted)
          if (doc.approvedBy.isNotBlank()) Text("تأیید: ${doc.approvedBy}", style = MaterialTheme.typography.labelSmall, color = c.muted)
          Spacer(Modifier.height(14.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("بستن") }
            if (!ir.vil3ntec.tohid.ui.Platform.DESKTOP) {
              OutlinedButton(
                onClick = { scope.launch { ir.vil3ntec.tohid.print.PrintJob.shareReturn(context, d, docId)?.let(onMessage) } },
                modifier = Modifier.weight(1f),
              ) { Text("فرستادن") }
            }
            Button(onClick = { printer = true }, modifier = Modifier.weight(1f)) { Text("چاپ") }
          }
        }
      }
    }
  }

  if (printer) {
    PrinterDialog(
      onDismiss = { printer = false },
      onPrint = { address, width ->
        printer = false
        onMessage("در حال فرستادن به چاپگر…")
        scope.launch {
          val error = ir.vil3ntec.tohid.print.PrintJob.printReturn(context, d, docId, address, width)
          onMessage(error ?: "رسیدِ برگشت به چاپگر فرستاده شد")
        }
      },
    )
  }
}
