package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.model.PickLineDto
import ir.vil3ntec.tohid.core.model.PickSheetDto
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.orders.OrderStates
import ir.vil3ntec.tohid.core.orders.OrderText
import ir.vil3ntec.tohid.core.pick.PickText
import ir.vil3ntec.tohid.data.repo.DeliveryRepository
import ir.vil3ntec.tohid.data.repo.PickingRepository
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 *  برگهٔ جمع‌آوری و بسته‌بندی (PCK، §۶.۱۷؛ سناریوهای S31 و S37) — پشتِ پرچمِ `shop.orders.picking`.
 *
 *  - قلم‌ها به ترتیبِ قفسه (PCK-01)؛ اسکنِ بعدی روی «قلمِ جاری» می‌نشیند (برگزیده یا نخستینِ باز).
 *  - کادرِ اسکن بارکدخوانِ صفحه‌کلیدی (HID) و تایپ را می‌گیرد؛ Enter می‌فرستد. سرور بارکد را با همان کالا
 *    می‌سنجد و کالای اشتباه را رد می‌کند (PCK-02) — لرزش و پیامِ روشن.
 *  - «پیدا نکردم» روی هر قلمِ باز همیشه هست (PCK-03)؛ کالای وزنی وزنِ واقعی می‌گیرد با پیش‌سنجشِ حدِ مجاز
 *    (PCK-04)؛ «از نو» اشتباه را برمی‌گرداند.
 *  - «بسته‌بندی شد» فقط وقتی هیچ قلمِ بازی نیست؛ جمعِ تازه و کسرِ خودکار (PCK-07) و برچسبِ کیسه‌ها (PCK-09).
 *
 *  ⛔ هر کار یک `eventId` دارد؛ اگر پاسخ گم شد، همان کار با همان شناسه دوباره می‌رود (سرور یک بار حساب
 *  می‌کند). کارِ بعدی — حتی همان اسکن برای بوتلِ دوم — شناسهٔ تازه می‌گیرد.
 */
@Composable
internal fun PickSheet(
  orderId: String,
  orderNo: Long,
  repo: PickingRepository,
  /** لرزش: true = موفق، false = رد (کالای اشتباه و …) — صفحه خودش به دستگاه دست نمی‌زند */
  buzz: (Boolean) -> Unit,
  onClose: () -> Unit,
  onChanged: () -> Unit,
) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  var sheet by remember(orderId) { mutableStateOf<PickSheetDto?>(null) }
  var loadError by remember(orderId) { mutableStateOf<String?>(null) }
  var busy by remember(orderId) { mutableStateOf(false) }
  var note by remember(orderId) { mutableStateOf<Pair<Boolean, String>?>(null) }
  var selected by remember(orderId) { mutableStateOf<Int?>(null) }
  var scan by remember(orderId) { mutableStateOf("") }
  val weights = remember(orderId) { mutableStateMapOf<Int, String>() }
  var bags by remember(orderId) { mutableStateOf(1) }
  //  کارِ در راه: امضای کار ⇒ شناسهٔ رویداد (پاسخِ گم‌شده ⇒ همان شناسه)
  var inflight by remember(orderId) { mutableStateOf<Pair<String, String>?>(null) }
  val packKey = remember(orderId) { DeliveryRepository.OnceKey { PickingRepository.newEventId() } }

  fun load() {
    loadError = null
    scope.launch {
      //  «شروع» همان برگهٔ موجود را هم برمی‌گرداند (سفارشِ «قبول‌شده» ⇒ «در حال جمع‌آوری»)
      repo.start(orderId)
        .onSuccess { sheet = it; onChanged() }
        .onFailure { loadError = it.userMessage }
    }
  }
  LaunchedEffect(orderId) { load() }

  /** یک کارِ جمع‌آور؛ `sig` امضای کار بی شناسه (برای فرستادنِ دوباره پس از خطای شبکه) */
  fun act(sig: String, build: (String) -> JsonObject) {
    if (busy) return
    val id = inflight?.takeIf { it.first == sig }?.second ?: PickingRepository.newEventId()
    inflight = sig to id
    busy = true
    note = null
    scope.launch {
      repo.send(orderId, listOf(build(id)))
        .onSuccess { out ->
          inflight = null
          out.pick?.let { sheet = it }
          val r = out.results.firstOrNull()
          if (r != null && r.status != "accepted") {
            buzz(false)
            note = false to r.message.ifBlank { PickText.reject(r.code) }
          }
        }
        .onFailure { note = false to "${it.userMessage} — دوباره بزنید؛ همان کار یک بار حساب می‌شود" }
      busy = false
    }
  }

  TohidBottomSheet(title = "جمع‌آوری — سفارش #$orderNo", onDismiss = onClose) {
    SheetBody {
      val s = sheet
      if (s == null) {
        if (loadError != null) {
          ErrorLine(loadError)
          TohidButton("دوباره", onClick = { load() })
        } else {
          TohidLoadingState(rows = 3)
        }
        return@SheetBody
      }
      val open = s.status == "picking" && s.orderStatus == "picking"
      val current = if (open) PickText.current(s.lines, selected, { it.lineNo }, { it.status == "pending" }) else null

      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          "${s.progress.done} از ${s.progress.total} قلم",
          Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = c.text,
        )
        StatusPill(
          when (s.status) { "packed" -> "بسته‌بندی‌شده"; "picking" -> "در حال جمع‌آوری"; else -> OrderText.status(s.orderStatus) },
          if (s.status == "packed") PillTone.SUCCESS else PillTone.PREPARING,
        )
      }
      if (!open && s.status == "picking") {
        Text("سفارش دیگر «در حال جمع‌آوری» نیست (${OrderText.status(s.orderStatus)}).", style = MaterialTheme.typography.bodySmall, color = c.warning)
      }

      if (open && current != null) {
        val line = s.lines.first { it.lineNo == current }
        //  بارکدخوانِ صفحه‌کلیدی پس از بارکد Enter می‌زند ⇒ همان «ثبت»
        fun submitScan() {
          val code = scan.trim()
          if (code.isEmpty()) return
          scan = ""
          act("scan:${line.lineNo}:$code:${line.pickedQtyMilli}") { PickingRepository.event(it, "scan", line.lineNo, barcode = code) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
            value = scan,
            onValueChange = { scan = it.take(64) },
            label = { Text("اسکن یا بارکد — «${line.name}»") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submitScan() }, onDone = { submitScan() }, onGo = { submitScan() }),
            modifier = Modifier.weight(1f),
          )
          Spacer(Modifier.width(8.dp))
          TohidSecondaryButton("ثبت", onClick = { submitScan() }, enabled = !busy && scan.isNotBlank())
        }
      }
      note?.let { (ok, text) -> Text(text, style = MaterialTheme.typography.bodySmall, color = if (ok) c.success else c.danger) }

      for (l in s.lines) {
        PickLineCard(
          l = l, current = l.lineNo == current, open = open, busy = busy, toleranceBp = s.toleranceBp,
          weight = weights[l.lineNo] ?: "",
          onWeight = { weights[l.lineNo] = it.take(12) },
          onSelect = { selected = l.lineNo },
          onPick = {
            val rest = (l.orderedQtyMilli - l.pickedQtyMilli).coerceAtLeast(0)
            act("pick:${l.lineNo}:${l.pickedQtyMilli}") { PickingRepository.event(it, "pick", l.lineNo, qtyMilli = rest) }
          },
          onWeigh = { milli ->
            act("weigh:${l.lineNo}:$milli") { PickingRepository.event(it, "weigh", l.lineNo, qtyMilli = milli) }
          },
          onNotFound = { act("not_found:${l.lineNo}") { PickingRepository.event(it, "not_found", l.lineNo) } },
          onLocation = if (l.productId.isBlank()) null else { a, sh, lv ->
            scope.launch {
              repo.setLocation(l.productId, a, sh, lv)
                .onSuccess { note = true to "جای «${l.name}» برای جمع‌آوری‌های بعدی ثبت شد" }
                .onFailure { note = false to it.userMessage }
            }
          },
          onReset = { act("reset:${l.lineNo}:${l.status}:${l.pickedQtyMilli}") { PickingRepository.event(it, "reset", l.lineNo) } },
        )
      }

      Spacer(Modifier.height(4.dp))
      val t = s.totals
      Text(
        if (s.status == "packed") "جمعِ نهایی: ${afn(t.totalMinor)}" else "جمعِ تا این‌جا: ${afn(t.totalMinor)}",
        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = c.text,
      )
      if (t.deductedMinor > 0) {
        Text(
          "از ${afn(t.originalTotalMinor)} ِ سفارش ${afn(t.deductedMinor)} کم می‌شود (قلمِ پیدانشده یا کم) — نقدِ صندوق و پیک همین جمعِ تازه است.",
          style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
      }

      if (open) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("کیسه‌ها", Modifier.weight(1f), color = c.text)
          TohidSecondaryButton("−", onClick = { if (bags > 1) bags-- }, enabled = bags > 1 && !busy)
          Text("$bags", Modifier.width(40.dp), color = c.text, fontWeight = FontWeight.Bold)
          TohidSecondaryButton("+", onClick = { if (bags < 50) bags++ }, enabled = bags < 50 && !busy)
        }
        TohidButton(
          if (s.progress.pending > 0) "${s.progress.pending} قلم مانده" else "بسته‌بندی شد",
          busy = busy,
          enabled = !busy && s.progress.pending == 0,
          modifier = Modifier.fillMaxWidth(),
          onClick = {
            busy = true
            note = null
            scope.launch {
              repo.pack(orderId, bags, packKey.of(PickingRepository.packJson(bags)))
                .onSuccess { sheet = it; buzz(true); note = true to "بسته‌بندی شد؛ برچسبِ کیسه‌ها پایین است"; onChanged() }
                .onFailure { note = false to it.userMessage }
              busy = false
            }
          },
        )
      }

      if (s.labels.isNotEmpty()) {
        Text("برچسبِ کیسه‌ها", style = MaterialTheme.typography.labelLarge, color = c.text)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          for (lb in s.labels) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              QrBox(lb.qr, side = 120.dp)
              Text("#${s.orderNo} · کیسهٔ ${lb.bag} از ${lb.of}", style = MaterialTheme.typography.labelSmall, color = c.text)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PickLineCard(
  l: PickLineDto,
  current: Boolean,
  open: Boolean,
  busy: Boolean,
  toleranceBp: Int,
  weight: String,
  onWeight: (String) -> Unit,
  onSelect: () -> Unit,
  onPick: () -> Unit,
  onWeigh: (Long) -> Unit,
  onNotFound: () -> Unit,
  onReset: () -> Unit,
  /** جای قفسه برای برگه‌های بعدی (PCK-01)؛ قلمِ دستیِ بی‌کالا ⇒ null */
  onLocation: ((String, String, String) -> Unit)? = null,
) {
  val c = Shop.colors
  val pending = l.status == "pending"
  var editLoc by remember(l.lineNo) { mutableStateOf(false) }
  var aisle by remember(l.lineNo) { mutableStateOf(l.location.aisle) }
  var shelf by remember(l.lineNo) { mutableStateOf(l.location.shelf) }
  var level by remember(l.lineNo) { mutableStateOf(l.location.level) }
  TohidCard(onClick = if (open && pending) onSelect else null, glow = current) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(l.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = c.text)
      StatusPill(
        PickText.lineStatus(l.status),
        when (l.status) {
          "picked" -> PillTone.SUCCESS
          "short" -> PillTone.WARNING
          "not_found", "removed" -> PillTone.DANGER
          else -> if (current) PillTone.INFO else PillTone.NEUTRAL
        },
      )
    }
    Text(PickText.location(l.location.aisle, l.location.shelf, l.location.level), style = MaterialTheme.typography.bodySmall, color = c.muted)
    Text(
      "سفارش: ${PickText.qty(l.orderedQtyMilli, l.unit)} · برداشته: ${PickText.qty(l.pickedQtyMilli, l.unit)}" +
        if (l.wrongScans > 0) " · اسکنِ اشتباه: ${l.wrongScans}" else "",
      style = MaterialTheme.typography.bodySmall, color = c.text,
    )
    if (!open) return@TohidCard
    if (pending && l.weighed) {
      val milli = PickText.weightMilli(weight)
      val hint = milli?.let { PickText.weighHint(l.orderedQtyMilli, it, l.unit, toleranceBp) }
      TohidTextField(
        weight, onWeight, label = "وزنِ واقعی (${l.unit.ifBlank { "کیلو" }})",
        supporting = hint ?: "اول کالا را اسکن کنید، سپس وزنِ ترازو",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      )
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      if (pending && l.weighed) {
        val milli = PickText.weightMilli(weight)
        TohidButton("ثبتِ وزن", onClick = { if (milli != null) onWeigh(milli) }, enabled = !busy && milli != null)
      } else if (pending) {
        TohidSecondaryButton("برداشتم (بی بارکد)", onClick = onPick, enabled = !busy)
      }
      //  PCK-03: «پیدا نکردم» همیشه روی قلمِ باز
      if (pending) TohidDangerButton("پیدا نکردم", onClick = onNotFound, enabled = !busy)
      if (!pending || l.pickedQtyMilli > 0 || l.scans > 0) TohidSecondaryButton("از نو", onClick = onReset, enabled = !busy)
      if (onLocation != null) TohidSecondaryButton(if (editLoc) "بستنِ جا" else "جای قفسه", onClick = { editLoc = !editLoc })
    }
    if (editLoc && onLocation != null) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TohidTextField(aisle, { aisle = it.take(20) }, label = "راهرو", modifier = Modifier.weight(1f))
        TohidTextField(shelf, { shelf = it.take(20) }, label = "قفسه", modifier = Modifier.weight(1f))
        TohidTextField(level, { level = it.take(20) }, label = "طبقه", modifier = Modifier.weight(1f))
      }
      TohidButton("ثبتِ جا", onClick = { onLocation(aisle, shelf, level); editLoc = false }, enabled = !busy)
    }
  }
}

// ── اتصال به صفحهٔ «سفارش‌ها» (این‌جا، تا `Orders.kt` که بالای سقفِ ۸۰۰ خط است بزرگ‌تر نشود) ──────

/** میزبانِ برگهٔ جمع‌آوری روی صفحهٔ سفارش‌ها؛ `api` و لرزش از خودِ صفحه می‌آیند (این فایل بی Context می‌ماند). */
@Composable
internal fun OrderPickHost(o: OrderDto, api: ApiClient, buzz: (Boolean) -> Unit, onClose: () -> Unit, onChanged: () -> Unit) {
  PickSheet(
    orderId = o.id, orderNo = o.orderNo, repo = remember(api) { PickingRepository(api) },
    buzz = buzz, onClose = onClose, onChanged = onChanged,
  )
}

/** کارهای میزِ سفارش با پرچمِ جمع‌آوری: «جمع‌آوری» و «بسته‌بندی» فقط از برگه (کسرِ خودکار، برچسب)؛ دومی = دکمهٔ برگه. */
internal fun orderPickActions(o: OrderDto, cfg: OrdersConfigDto): Pair<List<String>, Boolean> = Pair(
  PickText.orderActions(OrderStates.actions(o.status, o.fulfillment, cfg.permissions.manage, cfg.permissions.refund), cfg.picking),
  cfg.picking && PickText.canPick(o.status, cfg.permissions.manage),
)

/** دکمهٔ «جمع‌آوری» / «ادامهٔ جمع‌آوری» در جزئیاتِ سفارش. */
@Composable
internal fun OrderPickButton(o: OrderDto, busy: Boolean, onPick: () -> Unit) {
  TohidButton(PickText.pickAction(o.status), onClick = onPick, enabled = !busy)
}
