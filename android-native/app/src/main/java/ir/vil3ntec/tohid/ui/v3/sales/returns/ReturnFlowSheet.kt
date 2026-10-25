package ir.vil3ntec.tohid.ui.v3.sales.returns

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.ReturnFlow
import ir.vil3ntec.tohid.data.ReturnSettings
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.sync.DocSeries
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.screens.ReturnApproval
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3IconButton
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.sales.history.ReceiptModel
import kotlinx.coroutines.launch

/**
 *  برگهٔ «برگشت و تعویض» (RET-01..08، 10، 12، 13؛ S13–S15) — جای `ReturnFlowDialog`ِ پیشین،
 *  همان حساب و همان ترتیب: با رسید ([sale]) یا بی رسید، مقدار و «به موجودی | ضایعات» برای هر
 *  قلم، دلیلِ اجباری، بازپرداخت (روشِ اصلی، نقد، اعتبارِ فروشگاه)، تعویضِ اختیاری، و سدِ تأیید
 *  (`ReturnFlow.gate`). شناسه‌های سند و فروشِ تعویض پیش از نخستین تلاش ساخته می‌شوند؛ «ثبت شد»
 *  فقط پس از نوشتنِ واقعیِ دفتر (`save`).
 */
@Composable
fun ReturnFlowSheet(
  d: ShopData,
  sale: Sale?,
  save: suspend (ShopData) -> Boolean,
  onPosted: (docId: String, note: String) -> Unit,
  onClose: () -> Unit,
  /** فقط عکسِ CI: یک واحد از قلمِ اول و دلیلِ اول از پیش پر */
  prefill: Boolean = false,
) {
  val context = LocalContext.current
  val c = PhoneV3.colors
  val scope = rememberCoroutineScope()
  val role = remember { ShopRole.current(context) }
  val layers = remember { ShopRole.layers(context) }
  val limit = remember { ReturnSettings.limit(context) }
  val me = remember { runCatching { SyncStore(context).accountName }.getOrDefault("").trim() }
  //  شناسه‌های سند و فروشِ تعویض پیش از نخستین تلاش (§۱۵.۲ بندِ ۱)
  val docId = remember { Ulid.next() }
  val saleKey = remember { Ulid.next() }
  val chosen = remember { booleanArrayOf(false) }

  val items = remember(d, sale) { if (sale == null) emptyList() else d.saleItems.filter { it.saleId == sale.id && SalesEngine.returnable(it) > 0 } }
  var qtys by remember { mutableStateOf<Map<String, String>>(if (prefill) items.take(1).associate { it.id to "1" } else mapOf()) }
  var disp by remember { mutableStateOf(mapOf<String, String>()) }
  var free by remember { mutableStateOf(listOf<ReturnFree>()) }
  var reason by remember { mutableStateOf<String?>(if (prefill) ReturnFlow.REASONS.first() else null) }
  var mode by remember { mutableStateOf(ReturnFlow.ORIGINAL) }
  var customerId by remember { mutableStateOf(sale?.debtorId) }
  var exchange by remember { mutableStateOf(false) }
  var cart by remember { mutableStateOf(listOf<SalesEngine.CartLine>()) }
  var approval by remember { mutableStateOf<ReturnFlow.Approval?>(null) }
  var asking by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  //  تا ذخیره تمام نشده، دکمه دوباره کار نمی‌کند (دو بار زدن = یک سند)
  var saving by remember { mutableStateOf(false) }

  val picks = items.associate { it.id to ReturnFlow.Pick(ReturnFlowModel.number(qtys[it.id]), disp[it.id] ?: ReturnFlow.RESTOCK) }
  val quote = remember(d, sale, qtys, disp, free) {
    if (sale != null) ReturnFlow.quote(d, sale.id, picks) else ReturnFlow.quoteNoReceipt(d, ReturnFlowModel.freeLines(free))
  }
  val q = (quote as? ReturnFlow.Outcome.Ok)?.value
  val total = q?.total ?: 0.0
  val damaged = q?.lines?.any { it.disposition == ReturnFlow.DAMAGED } == true
  val perm = remember(total) { ReturnFlow.permDecision(role, layers, ReturnFlow.minor(total)) }
  val gate = ReturnFlow.gate(role, perm, ReturnFlow.minor(total), sale == null, damaged, ReturnFlow.minor(limit))
  val creditTo = if (mode == ReturnFlow.STORE_CREDIT) customerId else null
  val prior = remember(d, sale) { sale?.let { s -> d.saleReturns.filter { it.saleId == s.id } }.orEmpty() }
  val split = (ReturnFlow.split(sale, total, mode, me, creditTo, prior) as? ReturnFlow.Outcome.Ok)?.value
  val cartTotal = remember(d, cart) { SalesEngine.cartTotal(d, cart) }
  val approved = gate.decision == "allow" || (approval != null && ReturnFlow.canApprove(gate, approval!!.role))
  val requester = ReturnApproval.requester

  fun submit(closer: ir.vil3ntec.tohid.ui.v3.V3SheetCloser) {
    val req = ReturnFlow.Request(
      docId = docId, saleId = sale?.id, picks = picks, free = ReturnFlowModel.freeLines(free),
      reason = reason.orEmpty(), mode = mode, customerId = creditTo,
      by = me, role = role, perm = perm, limit = limit, approval = approval,
    )
    val now = System.currentTimeMillis()
    //  دفترِ تازه و پیامِ موفقیت، یا خطا — «ثبت شد» فقط پس از نوشتنِ واقعی
    val built: Pair<ShopData, String>? = if (exchange) {
      when (val r = ReturnFlow.exchange(d, req, cart, SalesEngine.Checkout(seller = me), saleKey, todayIso(), now, Ulid::next, series = DocSeries.current(context))) {
        is ReturnFlow.Outcome.Bad -> { error = r.message; null }
        is ReturnFlow.Outcome.Ok -> r.value.data to ReturnFlowModel.exchangeNote(r.value.due)
      }
    } else {
      when (val r = ReturnFlow.post(d, req, todayIso(), now, Ulid::next)) {
        is ReturnFlow.Outcome.Bad -> { error = r.message; null }
        is ReturnFlow.Outcome.Ok -> r.value.data to StrSale.returnPosted(PhoneSaleText.amount(r.value.total))
      }
    }
    built ?: return
    saving = true
    scope.launch {
      //  قفلِ نرمِ اشتراک یا خطای دیسک ⇒ `false`: هیچ «ثبت شد»ی نیست و فرم باز می‌ماند
      if (save(built.first)) { chosen[0] = true; closer.close { onPosted(docId, built.second) } }
      else error = StrSale.returnNotSaved
      saving = false
    }
  }

  V3Sheet(
    onDismiss = { if (!chosen[0]) onClose() },
    title = if (sale != null) StrSale.returnTitle(ReceiptModel.number(sale)) else StrSale.noReceiptReturn,
    dismissible = !saving,
  ) { closer ->
    if (sale != null) Text(ReturnFlowModel.saleLine(d, sale), style = V3Type.footnote, color = c.inkMuted)
    Spacer(Modifier.height(12.dp))
    if (sale?.status == "cancelled") {
      //  RET-13 — فاکتورِ لغوشده کلاً برگشته است
      V3Message(V3Tone.ERROR, StrSale.cancelledNoReturn)
      return@V3Sheet
    }

    Label(StrSale.returnedItems)
    V3Card(radius = 16.dp, padding = PaddingValues(vertical = 4.dp)) {
      if (sale != null) {
        if (items.isEmpty()) Text(StrSale.allReturned, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(14.dp))
        items.forEachIndexed { i, item ->
          if (i > 0) V3Divider(start = 14.dp)
          val unit = d.products.find { it.id == item.productId }?.unit.orEmpty()
          Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text(SalesEngine.itemName(d, item), style = V3Type.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ReturnFlowModel.itemLine(item, unit), style = V3Type.footnote, color = c.inkMuted)
              }
              Spacer(Modifier.width(8.dp))
              V3TextField(qtys[item.id].orEmpty(), { qtys = qtys + (item.id to it); error = null }, StrSale.qtyLabel, Modifier.width(110.dp), kind = V3FieldKind.NUMBER)
            }
            if (ReturnFlowModel.number(qtys[item.id]) > 0 && item.productId.isNotBlank()) Disposition(disp[item.id] ?: ReturnFlow.RESTOCK) { disp = disp + (item.id to it) }
          }
        }
      } else {
        free.forEachIndexed { i, row ->
          if (i > 0) V3Divider(start = 14.dp)
          Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(d.products.find { it.id == row.productId }?.name ?: StrSale.itemWord, style = V3Type.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
              V3IconButton(Icons.Outlined.Close, StrSale.removeWord, size = 36.dp) { free = free.filterIndexed { k, _ -> k != i } }
            }
            Row {
              V3TextField(row.qty, { v -> free = free.mapIndexed { k, r -> if (k == i) r.copy(qty = v) else r } }, StrSale.qtyLabel, Modifier.weight(1f), kind = V3FieldKind.NUMBER)
              Spacer(Modifier.width(8.dp))
              V3TextField(row.price, { v -> free = free.mapIndexed { k, r -> if (k == i) r.copy(price = v) else r } }, StrSale.unitPrice, Modifier.weight(1f), kind = V3FieldKind.MONEY)
            }
            Disposition(row.disposition) { v -> free = free.mapIndexed { k, r -> if (k == i) r.copy(disposition = v) else r } }
          }
        }
        ProductSearch(d, StrSale.addReturnItem, Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) { p -> free = free + ReturnFlowModel.freeRow(p) }
      }
    }

    Label(StrSale.returnReasonRequired)
    V3ChipRow(contentPadding = PaddingValues(0.dp)) {
      ReturnFlow.REASONS.forEach { r -> V3Chip(r, reason == r, { reason = r }) }
    }

    Label(StrSale.refundWord)
    val modes = ReturnFlowModel.modes(sale != null)
    V3Segmented(modes.map { it.second }, modes.indexOfFirst { it.first == mode }.coerceAtLeast(0), { mode = modes[it].first })
    if (mode == ReturnFlow.STORE_CREDIT && sale?.debtorId == null) {
      Spacer(Modifier.height(8.dp))
      if (d.debtors.isEmpty()) Text(StrSale.makeCustomerFirst, style = V3Type.footnote, color = c.inkMuted)
      else V3ChipRow(contentPadding = PaddingValues(0.dp)) {
        d.debtors.sortedBy { it.name }.forEach { who -> V3Chip(who.name, customerId == who.id, { customerId = who.id }) }
      }
    }
    if (split != null && total > 0) {
      Spacer(Modifier.height(8.dp))
      ReturnFlowModel.splitRows(split).forEach { (label, amount) -> MoneyLine(label, amount) }
    }

    Spacer(Modifier.height(10.dp))
    V3Card(radius = 16.dp, padding = PaddingValues(0.dp)) {
      V3ToggleRow(StrSale.exchangeToggle, exchange, { exchange = it })
    }
    AnimatedVisibility(exchange) {
      Column {
        Label(StrSale.newItems)
        V3Card(radius = 16.dp, padding = PaddingValues(vertical = 4.dp)) {
          cart.forEachIndexed { i, line ->
            if (i > 0) V3Divider(start = 14.dp)
            val p = d.products.find { it.id == line.productId }
            val step = SalesEngine.cartStep(p?.unit.orEmpty())
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text(p?.name ?: StrSale.itemWord, style = V3Type.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(PhoneSaleText.amount(SalesEngine.linePrice(d, line)), style = V3Type.footnote, color = c.inkMuted)
              }
              V3IconButton(Icons.Outlined.Remove, StrSale.oneLess(p?.name.orEmpty()), size = 36.dp) { cart = SalesEngine.setCartQty(cart, line.productId, line.quantity - step) }
              Text(qty(line.quantity), style = V3Type.headline, color = c.ink, modifier = Modifier.padding(horizontal = 10.dp))
              V3IconButton(Icons.Outlined.Add, StrSale.oneMore(p?.name.orEmpty()), size = 36.dp) { cart = SalesEngine.setCartQty(cart, line.productId, line.quantity + step) }
            }
          }
          ProductSearch(d, StrSale.newItemSearch, Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) { p -> cart = SalesEngine.addToCart(cart, p.id, 1.0) }
        }
      }
    }

    Spacer(Modifier.height(12.dp))
    V3Card(radius = 16.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
      MoneyLine(StrSale.returnAmount, total, strong = !exchange)
      if (exchange) {
        MoneyLine(StrSale.newPurchase, cartTotal)
        val due = cartTotal - (split?.cash ?: 0.0)
        MoneyLine(if (due >= 0) StrSale.receiveFromCustomer else StrSale.payToCustomer, kotlin.math.abs(due), strong = true)
      }
      if (gate.decision != "allow") {
        Spacer(Modifier.height(8.dp))
        V3StatusPill(
          when { gate.decision == "deny" -> V3Status.CANCELLED; approved -> V3Status.DELIVERED; else -> V3Status.AWAITING },
          ReturnFlowModel.gateText(gate, approved, approval),
        )
        if (gate.decision == "approve" && !approved) {
          Spacer(Modifier.height(6.dp))
          if (requester != null) V3Button(StrSale.askApproval, { asking = true }, kind = V3ButtonKind.SECONDARY, height = 40.dp)
          else Text(StrSale.approverMustPost(ReturnFlowModel.approverText(gate.approver)), style = V3Type.footnote, color = c.inkMuted)
        }
      }
    }
    (quote as? ReturnFlow.Outcome.Bad)?.takeIf { it.code != ReturnFlow.Code.NOTHING_TO_RETURN }?.let {
      Spacer(Modifier.height(8.dp)); V3Message(V3Tone.ERROR, it.message)
    }
    error?.let { Spacer(Modifier.height(8.dp)); V3Message(V3Tone.ERROR, it) }

    Spacer(Modifier.height(16.dp))
    V3Button(
      if (exchange) StrSale.recordExchange else StrSale.recordReturnV2,
      { submit(closer) },
      Modifier.fillMaxWidth(),
      enabled = !saving && q != null && reason != null && approved && (!exchange || cart.isNotEmpty()),
      loading = saving,
      haptic = true,
    )
  }

  if (asking && requester != null) {
    requester(
      ReturnApproval.Need(doc = "returns/$docId", amountMinor = ReturnFlow.minor(total), reasons = gate.reasons, approver = gate.approver),
    ) { got -> asking = false; approval = got }
  }
}

@Composable
private fun Label(text: String) {
  Spacer(Modifier.height(14.dp))
  Text(text, style = V3Type.subhead, color = PhoneV3.colors.inkMuted)
  Spacer(Modifier.height(6.dp))
}

@Composable
private fun Disposition(value: String, onChange: (String) -> Unit) {
  Spacer(Modifier.height(8.dp))
  val options = listOf(ReturnFlow.RESTOCK to StrSale.toStock, ReturnFlow.DAMAGED to StrSale.damagedWord)
  V3Segmented(options.map { it.second }, if (value == ReturnFlow.DAMAGED) 1 else 0, { onChange(options[it].first) })
}

@Composable
private fun MoneyLine(label: String, value: Double, strong: Boolean = false) {
  val c = PhoneV3.colors
  Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.weight(1f))
    Text(PhoneSaleText.amount(value), style = if (strong) V3Type.headline else V3Type.callout, color = c.ink)
  }
}

/** جست‌وجوی کالا برای افزودن — بی رسید یا کالای تازهٔ تعویض */
@Composable
private fun ProductSearch(d: ShopData, hint: String, modifier: Modifier, onPick: (ir.vil3ntec.tohid.data.Product) -> Unit) {
  val c = PhoneV3.colors
  var term by remember { mutableStateOf("") }
  Column(modifier) {
    ir.vil3ntec.tohid.ui.v3.V3SearchField(term, { term = it }, hint)
    val hits = ReturnFlowModel.hits(d, term)
    if (term.isNotBlank() && hits.isEmpty()) Text(StrSale.noProductFound, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(6.dp))
    hits.forEach { p ->
      ir.vil3ntec.tohid.ui.v3.V3ListRow(
        p.name,
        value = PhoneSaleText.amount(p.salePrice),
        chevron = false,
        minHeight = 48.dp,
        onClick = { onPick(p); term = "" },
      )
    }
  }
}
