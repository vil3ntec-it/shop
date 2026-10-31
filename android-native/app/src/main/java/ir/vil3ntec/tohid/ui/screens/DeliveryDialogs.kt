package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.core.delivery.AutoAssign
import ir.vil3ntec.tohid.core.delivery.CourierCash
import ir.vil3ntec.tohid.core.delivery.DeliveryText
import ir.vil3ntec.tohid.core.delivery.DeskActions
import ir.vil3ntec.tohid.core.delivery.DeskConfig
import ir.vil3ntec.tohid.core.delivery.HandedOverLine
import ir.vil3ntec.tohid.core.delivery.HandedOverOrder
import ir.vil3ntec.tohid.core.delivery.HandoverKind
import ir.vil3ntec.tohid.core.delivery.HandoverSale
import ir.vil3ntec.tohid.core.delivery.HandoverSaleResult
import ir.vil3ntec.tohid.core.delivery.Mission
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderDraft
import ir.vil3ntec.tohid.core.pick.HandoverCode
import ir.vil3ntec.tohid.data.repo.DeliveryRepository
import ir.vil3ntec.tohid.data.repo.DeliveryWire
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.components.common.SearchBox
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/* ============================ ابزارهای فرم ============================ */

/** «۱۲٬۵۰۰» یا «12500.5» ⇐ واحدِ کوچک؛ خالی ⇐ ۰؛ نامعتبر ⇐ null */
internal fun minorOf(text: String): Long? = if (text.isBlank()) 0L else OrderDraft.scaled(text, 2)

/** واحدِ کوچک ⇐ متنِ فیلد (بی جداکننده تا ویرایش ساده بماند) */
internal fun fieldOf(minor: Long): String =
  if (minor % 100 == 0L) (minor / 100).toString() else "${minor / 100}.${(minor % 100).toString().padStart(2, '0')}"

internal val MONEY_KEYS = KeyboardOptions(keyboardType = KeyboardType.Decimal)

/** بدنهٔ برگه: روی گوشی خودش می‌لغزد؛ کامپیوتر را `AdaptiveSheet` می‌لغزاند */
@Composable
internal fun SheetBody(content: @Composable ColumnScope.() -> Unit) {
  val scroll = if (Platform.DESKTOP) Modifier else Modifier.verticalScroll(rememberScrollState())
  Column(Modifier.fillMaxWidth().then(scroll), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** ردیفِ دلیل‌ها — یکی انتخاب می‌شود */
@Composable
internal fun ReasonChips(reasons: List<String>, selected: String, onPick: (String) -> Unit) {
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    reasons.forEach { r -> TohidFilterChip(DeliveryText.reason(r), selected = selected == r, onClick = { onPick(r) }) }
  }
}

@Composable
internal fun ErrorLine(text: String?) {
  if (text != null) Text(text, style = MaterialTheme.typography.bodySmall, color = Shop.colors.danger)
}

internal fun reasonsOf(cfg: DeskConfig, key: String): List<String> =
  cfg.reasons[key]?.takeIf { it.isNotEmpty() } ?: DeliveryText.FALLBACK_REASONS[key].orEmpty()

/* ============================ سپردنِ دستی ============================ */

/**
 *  سپردنِ دستی (DLV-03): پیکِ دکان با نمایشِ جای خالیِ نقد (DLV-11) یا
 *  شرکتِ بیرونی (DLV-19)، با کرایهٔ همین تحویل (DLV-13). پیکی که با این
 *  سفارش از سقف می‌گذرد فقط با تیکِ آگاهانه سپرده می‌شود؛ سرور هم همان را
 *  می‌خواهد (`overrideCashCap`).
 */
@Composable
internal fun AssignDialog(
  order: OrderDto,
  d: DeskData,
  cfg: DeskConfig,
  repo: DeliveryRepository,
  onDismiss: () -> Unit,
  onDone: (Mission, String?) -> Unit,
) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  val expected = AutoAssign.expectedOf(order.paymentMethod, order.paymentStatus, order.totalMinor)
  val suggested = remember(order.id, d.couriers) {
    AutoAssign.plan(listOf(AutoAssign.Candidate(order.id, order.orderNo, expected, order.createdAt)), d.couriers).picks.firstOrNull()?.courierId
  }
  val modes = buildList { if (cfg.shopCourier) add("shop_courier"); if (cfg.external) add("external") }
  var mode by remember { mutableStateOf(if (cfg.shopCourier && d.couriers.any { it.active }) "shop_courier" else modes.lastOrNull() ?: "external") }
  var courierId by remember { mutableStateOf(suggested ?: "") }
  var override by remember { mutableStateOf(false) }
  var company by remember { mutableStateOf("") }
  var ref by remember { mutableStateOf("") }
  var fare by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var err by remember { mutableStateOf<String?>(null) }
  //  یک کلیدِ یکتایی برای همین فرم — پاسخِ گم‌شده پس از ثبت، با دوباره زدن همان کار است
  val keys = remember { DeliveryRepository.OnceKey() }

  val chosen = d.couriers.firstOrNull { it.id == courierId }
  val fits = chosen?.let { CourierCash.fits(it, expected) } ?: true
  val fareMinor = minorOf(fare)
  val valid = fareMinor != null && when (mode) {
    "shop_courier" -> chosen != null && chosen.active && (fits || override)
    else -> company.isNotBlank()
  }

  TohidBottomSheet(title = "سپردنِ سفارش #${order.orderNo}", onDismiss = onDismiss) {
    SheetBody {
      Text(
        "${order.customer.name} · " + (if (expected > 0) "نقدِ گرفتنی ${afn(expected)}" else "پرداخت‌شده"),
        style = MaterialTheme.typography.bodyMedium, color = c.text,
      )
      val landmark = order.address.landmark.ifEmpty { order.address.text }
      if (landmark.isNotEmpty()) Text(landmark, style = MaterialTheme.typography.bodySmall, color = c.muted)
      if (modes.size > 1) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          modes.forEach { m -> TohidFilterChip(DeliveryText.mode(m), selected = mode == m, onClick = { mode = m; err = null }) }
        }
      }
      if (mode == "shop_courier") {
        if (d.couriers.isEmpty()) {
          Text("پیکی ثبت نشده — از «کارمندان» کدِ دعوت با نقشِ پیک بسازید.", style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        d.couriers.forEach { cr ->
          val ok = CourierCash.fits(cr, expected)
          PickRow(selected = cr.id == courierId, enabled = cr.active, onClick = { courierId = cr.id; override = false }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(cr.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = if (cr.active) c.text else c.muted)
              if (cr.id == suggested) Text("پیشنهاد", style = MaterialTheme.typography.labelSmall, color = c.primary)
            }
            Text(
              if (cr.active) "مأموریتِ زنده ${cr.liveCount} · نقدِ درگیر ${afn(CourierCash.committed(cr))} از ${afn(cr.cashCapMinor)} · جای خالی ${afn(CourierCash.headroom(cr))}"
              else "غیرفعال",
              style = MaterialTheme.typography.bodySmall, color = c.muted,
            )
            if (cr.active && !ok) {
              Text("با این سفارش از سقفِ نقدش می‌گذرد", style = MaterialTheme.typography.labelSmall, color = c.warning)
            }
          }
        }
        if (chosen != null && !fits) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = override, onCheckedChange = { override = it })
            Text(
              "آگاهانه بالای سقف بسپار — تا تسویه، این پیک سفارشِ خودکار نمی‌گیرد",
              style = MaterialTheme.typography.bodySmall, color = c.text,
            )
          }
        }
      } else {
        if (d.companies.isNotEmpty()) {
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            d.companies.forEach { co ->
              TohidFilterChip(co.name, selected = company == co.name, onClick = {
                company = co.name
                if (fare.isBlank() && co.lastFareMinor > 0) fare = fieldOf(co.lastFareMinor)
              })
            }
          }
        }
        TohidTextField(company, { company = it.take(80) }, label = "نامِ شرکتِ پیک")
        TohidTextField(ref, { ref = it.take(80) }, label = "شمارهٔ رهگیریِ شرکت (اختیاری)")
      }
      TohidTextField(
        fare, { fare = it.take(14) }, label = "کرایهٔ این تحویل (افغانی)",
        supporting = if (mode == "external") "آن‌چه شرکت برای همین سفارش می‌گیرد" else "دستمزدِ پیک برای همین تحویل؛ جدا از نقدِ مشتری",
        error = if (fareMinor == null) "عدد درست نیست" else null,
        keyboardOptions = MONEY_KEYS,
      )
      ErrorLine(err)
      TohidButton(
        "سپردن",
        busy = busy,
        enabled = valid && !busy,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
          busy = true
          err = null
          scope.launch {
            val body = DeliveryWire.assignJson(
              mode = mode, courierId = courierId, externalName = company, externalRef = ref,
              fareMinor = fareMinor ?: 0L, overrideCashCap = override,
            )
            repo.assign(order.id, body, keys.of(body))
              .onSuccess { onDone(it.mission, it.code) }
              .onFailure { err = it.userMessage }
            busy = false
          }
        },
      )
    }
  }
}

/* ============================ سپردنِ خودکار ============================ */

/**
 *  سپردنِ خودکار (DLV-03 خودکار): پیش‌نمایشِ نقشه — چه سفارشی به چه پیکی،
 *  چه سفارشی جا نشد و چرا، کدام پیک دیگر خودکار نمی‌گیرد و چرا (DLV-11) —
 *  و فقط با «تأیید» فرستاده می‌شود. اگر سرور میانِ کار «سقف پر شد» گفت،
 *  پیک‌ها تازه و باقی دوباره نقشه می‌شود.
 */
@Composable
internal fun AutoAssignDialog(
  orders: List<OrderDto>,
  d: DeskData,
  repo: DeliveryRepository,
  onDismiss: () -> Unit,
  onFinished: () -> Unit,
) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  val cands = remember(orders) {
    orders.map { AutoAssign.Candidate(it.id, it.orderNo, AutoAssign.expectedOf(it.paymentMethod, it.paymentStatus, it.totalMinor), it.createdAt) }
  }
  var maxLive by remember { mutableStateOf<Int?>(null) }
  var fare by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var done by remember { mutableStateOf<List<String>?>(null) }
  var failed by remember { mutableStateOf<List<String>>(emptyList()) }
  var left by remember { mutableStateOf<AutoAssign.Plan?>(null) }
  val plan = remember(cands, d.couriers, maxLive) { AutoAssign.plan(cands, d.couriers, maxLive) }
  val fareMinor = minorOf(fare)

  suspend fun run() {
    var couriers = d.couriers
    var remaining = cands
    val ok = ArrayList<String>()
    val bad = ArrayList<String>()
    var rounds = 0
    rounds@ while (remaining.isNotEmpty() && rounds < 3) {
      rounds++
      val p = AutoAssign.plan(remaining, couriers, maxLive)
      if (p.picks.isEmpty()) break@rounds
      var replan = false
      picks@ for (pick in p.picks) {
        val body = DeliveryWire.assignJson("shop_courier", courierId = pick.courierId, fareMinor = fareMinor ?: 0L)
        when (val r = repo.assign(pick.orderId, body, DeliveryRepository.newKey())) {
          is ApiResult.Success -> {
            ok += "#${pick.orderNo} ⇐ ${pick.courierName}"
            remaining = remaining.filter { it.orderId != pick.orderId }
          }
          is ApiResult.Failure -> {
            if (r.error.code == "cash_cap_exceeded") { replan = true; break@picks }
            bad += "#${pick.orderNo}: ${r.error.userMessage}"
            remaining = remaining.filter { it.orderId != pick.orderId }
          }
        }
      }
      if (!replan) break@rounds
      couriers = repo.couriers().valueOrNull() ?: break@rounds
    }
    val fresh = repo.couriers().valueOrNull() ?: couriers
    done = ok
    failed = bad
    left = AutoAssign.plan(remaining, fresh, maxLive)
  }

  TohidBottomSheet(title = "سپردنِ خودکار", onDismiss = { if (done != null) onFinished(); onDismiss() }) {
    SheetBody {
      val result = done
      if (result == null) {
        Text(
          "کم‌کارترین پیک انتخاب می‌شود (موقعیتِ پیک هنوز ثبت نمی‌شود). پیکی که با سفارش از سقفِ نقدش بگذرد هرگز خودکار انتخاب نمی‌شود.",
          style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
        Text("سقفِ کارِ هم‌زمانِ هر پیک", style = MaterialTheme.typography.labelLarge, color = c.text)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf<Int?>(null, 1, 2, 3, 5).forEach { n ->
            TohidFilterChip(n?.toString() ?: "بی‌سقف", selected = maxLive == n, onClick = { maxLive = n })
          }
        }
        TohidTextField(
          fare, { fare = it.take(14) }, label = "کرایهٔ هر تحویل (افغانی)",
          supporting = "دستمزدِ پیک برای هر کدام از این سفارش‌ها",
          error = if (fareMinor == null) "عدد درست نیست" else null,
          keyboardOptions = MONEY_KEYS,
        )
        PlanView(plan)
        TohidButton(
          if (plan.picks.isEmpty()) "سفارشی برای سپردن نیست" else "تأیید و سپردنِ ${plan.picks.size} سفارش",
          busy = busy,
          enabled = plan.picks.isNotEmpty() && fareMinor != null && !busy,
          modifier = Modifier.fillMaxWidth(),
          onClick = { busy = true; scope.launch { run(); busy = false } },
        )
      } else {
        Text("${result.size} سفارش سپرده شد", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = c.success)
        result.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = c.text) }
        if (failed.isNotEmpty()) {
          Text("سپرده نشد", style = MaterialTheme.typography.titleSmall, color = c.danger)
          failed.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = c.text) }
        }
        left?.let { p -> if (p.unplaced.isNotEmpty() || p.blocked.isNotEmpty()) PlanView(p.copy(picks = emptyList())) }
        TohidButton("بستن", onClick = { onFinished(); onDismiss() }, modifier = Modifier.fillMaxWidth())
      }
    }
  }
}

@Composable
private fun PlanView(p: AutoAssign.Plan) {
  val c = Shop.colors
  if (p.picks.isNotEmpty()) {
    DeskSection("پیشنهاد")
    p.picks.forEach { Text("#${it.orderNo} ⇐ ${it.courierName}", style = MaterialTheme.typography.bodyMedium, color = c.text) }
  }
  if (p.unplaced.isNotEmpty()) {
    DeskSection("جا نشد")
    p.unplaced.forEach { Text("#${it.orderNo}: ${it.reason}", style = MaterialTheme.typography.bodySmall, color = c.warning) }
  }
  if (p.blocked.isNotEmpty()) {
    DeskSection("پیک‌هایی که خودکار نمی‌گیرند")
    p.blocked.forEach { Text("${it.name}: ${it.reason}", style = MaterialTheme.typography.bodySmall, color = c.muted) }
  }
}

/* ============================ کارهای مأموریت ============================ */

/**
 *  یک کارِ کارمند روی مأموریت — با همان دلیل‌ها و همان شرط‌های سرور
 *  (`DeskActions`). «تحویل شد» نقدِ گرفته را می‌خواهد؛ اگر با مبلغِ سفارش
 *  فرق کند دلیل لازم است و اختلاف در تسویه دیده می‌شود (DEL-02).
 */
@Composable
internal fun MissionActionDialog(
  m: Mission,
  act: DeskActions.Act,
  cfg: DeskConfig,
  repo: DeliveryRepository,
  onDismiss: () -> Unit,
  onCode: (String) -> Unit,
  onDone: (String) -> Unit,
) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  var reason by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  var cash by remember { mutableStateOf(if (m.expectedCollectMinor > 0) fieldOf(m.expectedCollectMinor) else "") }
  var cashReason by remember { mutableStateOf("") }
  var code by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var err by remember { mutableStateOf<String?>(null) }
  //  یک کلیدِ یکتایی برای همین فرم — پاسخِ گم‌شده پس از ثبت، با دوباره زدن همان کار است
  val keys = remember { DeliveryRepository.OnceKey() }

  val reasonKey = when (act) {
    DeskActions.Act.WITHDRAW -> "withdrawn"
    DeskActions.Act.FAIL -> "failed"
    DeskActions.Act.RECALL -> "recall"
    else -> null
  }
  val reasons = reasonKey?.let { reasonsOf(cfg, it) }.orEmpty()
  val deliver = act == DeskActions.Act.DELIVER
  val needCode = deliver && m.hasCode && !m.codeUsed
  val cashMinor = if (deliver) minorOf(cash) else 0L
  val cashDiffers = deliver && cashMinor != null && cashMinor != m.expectedCollectMinor
  val noteNeeded = DeskActions.needsNote(m, act) || reason == "other" || cashReason == "other" ||
    (needCode && code.isBlank())
  val valid = (reasonKey == null || reason.isNotEmpty()) &&
    (!noteNeeded || note.isNotBlank()) &&
    cashMinor != null && (!cashDiffers || cashReason.isNotEmpty()) &&
    (!needCode || code.isNotBlank() || cfg.perms.assign)

  fun send() {
    busy = true
    err = null
    scope.launch {
      when (act) {
        DeskActions.Act.NEW_CODE -> repo.newCode(m.id).onSuccess { onCode(it) }.onFailure { err = it.userMessage }
        DeskActions.Act.RECALL -> repo.recall(m.id, reason, note, keys.of(DeliveryWire.recallJson(reason, note)))
          .onSuccess { onDone("سفارش #${m.orderNo}: فراخوانی فرستاده شد؛ پیک بسته را برمی‌گرداند") }
          .onFailure { err = it.userMessage }
        else -> {
          val to = DeskActions.target(act) ?: return@launch
          val body = DeliveryWire.statusJson(
            to = to, from = m.status, reason = reason, note = note, code = code.trim(),
            cashCollectedMinor = if (deliver) cashMinor else null, cashReason = if (cashDiffers) cashReason else "",
          )
          repo.status(m.id, body, keys.of(body))
            .onSuccess { onDone("سفارش #${m.orderNo}: ${DeliveryText.status(it.status)}") }
            .onFailure { f ->
              if (f.code == "status_changed") onDone("وضعیتِ این مأموریت همین حالا جای دیگری عوض شد؛ میز تازه شد.")
              else err = f.userMessage
            }
        }
      }
      busy = false
    }
  }

  TohidBottomSheet(title = "${DeskActions.label(act)} — سفارش #${m.orderNo}", onDismiss = onDismiss) {
    SheetBody {
      Text("${DeliveryText.mode(m.mode)} · ${DeliveryText.status(m.status)}", style = MaterialTheme.typography.bodySmall, color = c.muted)
      when (act) {
        DeskActions.Act.NEW_CODE -> Text("کدِ قبلی باطل می‌شود و کدِ تازه فقط یک بار نشان داده می‌شود.", color = c.text)
        DeskActions.Act.RECALL -> Text(
          "پیک خبر می‌گیرد که بسته را برگرداند. اگر پیش از رسیدنِ خبر تحویل داده باشد، همان «تحویل شد» ثبت می‌ماند.",
          style = MaterialTheme.typography.bodySmall, color = c.text,
        )
        DeskActions.Act.RETURN -> Text("فقط وقتی بسته واقعاً به دکان رسیده بزنید.", style = MaterialTheme.typography.bodySmall, color = c.text)
        else -> {}
      }
      if (reasons.isNotEmpty()) {
        Text("دلیل", style = MaterialTheme.typography.labelLarge, color = c.text)
        ReasonChips(reasons, reason) { reason = it }
      }
      if (deliver) {
        TohidTextField(
          cash, { cash = it.take(14) }, label = "نقدِ گرفته‌شده از مشتری (افغانی)",
          supporting = if (m.expectedCollectMinor > 0) "موردِ انتظار: ${afn(m.expectedCollectMinor)}" else "پرداخت‌شده — معمولاً صفر",
          error = if (cashMinor == null) "عدد درست نیست" else null,
          keyboardOptions = MONEY_KEYS,
        )
        if (cashDiffers) {
          Text("مبلغ با سفارش فرق دارد — دلیل:", style = MaterialTheme.typography.labelLarge, color = c.warning)
          ReasonChips(reasonsOf(cfg, "cash"), cashReason) { cashReason = it }
        }
        if (needCode) {
          TohidTextField(
            code, { raw -> code = scannedCode(raw, m.orderNo, cfg.picking) { err = it } }, label = "کدِ تحویلِ مشتری",
            supporting = if (cfg.perms.assign) "مشتری کد ندارد؟ خالی بگذارید و توضیح بنویسید (تأییدِ دستیِ مدیر)" else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          )
        }
      }
      if (act != DeskActions.Act.NEW_CODE) {
        TohidTextField(
          note, { note = it.take(300) }, label = if (noteNeeded) "توضیح (لازم)" else "توضیح (اختیاری)", singleLine = false,
        )
      }
      ErrorLine(err)
      val danger = act == DeskActions.Act.FAIL || act == DeskActions.Act.WITHDRAW || act == DeskActions.Act.RECALL
      if (danger) {
        TohidDangerButton(DeskActions.label(act), onClick = { send() }, enabled = valid && !busy, modifier = Modifier.fillMaxWidth())
      } else {
        TohidButton(DeskActions.label(act), onClick = { send() }, busy = busy, enabled = valid && !busy, modifier = Modifier.fillMaxWidth())
      }
    }
  }
}

/**
 *  کدِ دریافت/تحویل فقط همین یک بار دیده می‌شود (سرور فقط HMACش را دارد). با پرچمِ جمع‌آوری
 *  (`qr`، PCK-11) QRِ همان کد هم هست تا مشتری عکسش را بگیرد و دکان با اسکن سفارش را پیدا کند.
 */
@Composable
internal fun CodeOnceDialog(orderNo: Long, code: String, qr: Boolean = false, onDismiss: () -> Unit) {
  val c = Shop.colors
  val clipboard = LocalClipboardManager.current
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = c.surfaceSolid,
    title = { Text("کدِ سفارش #$orderNo", color = c.text) },
    text = {
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(code, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = c.text, letterSpacing = 6.sp)
        if (qr) {
          Spacer(Modifier.height(8.dp))
          HandoverQr(orderNo, code)
        }
        Spacer(Modifier.height(8.dp))
        Text(
          if (qr) "کد یا QR را به مشتری بدهید (عکس، پیامک یا تلفن). در دکان QR را اسکن کنید. دوباره نشان داده نمی‌شود؛ اگر گم شد «کدِ تازه» بزنید."
          else "این کد را به مشتری بدهید (پیامک یا تلفن). دوباره نشان داده نمی‌شود؛ اگر گم شد «کدِ تازه» بزنید.",
          style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("دیدم", color = c.primary) } },
    dismissButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text("کپی") } },
  )
}

/* ============================ دریافتِ حضوری ============================ */

/**
 *  دریافتِ حضوری با کد (DLV-15، S43): مشتری کد را نشان می‌دهد ⇐ نامش تطبیق
 *  داده می‌شود ⇐ «تحویل شد» با کد (و نقدِ گرفته‌شده در صندوق) ⇐ برنامه فروش
 *  را ثبت می‌کند (`HandoverSale`، تصمیمِ صاحب: سفارشِ آنلاین موجودیِ سرور را
 *  دست نمی‌زند؛ فروشِ برنامه می‌زند).
 */
@Composable
internal fun ColumnScope.PickupTab(
  d: DeskData,
  cfg: DeskConfig,
  writable: Boolean,
  repo: DeliveryRepository,
  ordersRepo: OrdersRepository,
  snackbar: SnackbarHostState,
  onChanged: () -> Unit,
) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  var query by remember { mutableStateOf("") }
  var handover by remember { mutableStateOf<Pair<OrderDto, Mission>?>(null) }
  var scanHit by remember { mutableStateOf("") }
  var shownCode by remember { mutableStateOf<Pair<Long, String>?>(null) }
  var newCodeFor by remember { mutableStateOf<Mission?>(null) }
  var busyId by remember { mutableStateOf<String?>(null) }
  val pickupKeys = remember { HashMap<String, DeliveryRepository.OnceKey>() }

  /**
   *  PCK-11، S43: QRِ مشتری در جست‌وجو اسکن شد ⇐ همان سفارش، و اگر آمادهٔ دریافت است و کدش باز،
   *  برگهٔ تحویل با کدِ همان اسکن باز می‌شود؛ وگرنه فقط فهرست به همان سفارش می‌رسد.
   */
  fun onSearch(raw: String) {
    val scan = if (cfg.picking) HandoverCode.parse(raw) else null
    val no = scan?.orderNo
    val code = scan?.code
    if (no == null || code == null) { query = raw; return }
    query = no.toString()
    val o = d.pickups.firstOrNull { it.orderNo == no } ?: return
    val m = d.missionOf(o.id)?.takeIf { it.mode == "pickup" } ?: return
    if (writable && DeskActions.Act.PICKUP_HANDOVER in DeskActions.of(m.copy(orderStatus = o.status), cfg.perms)) {
      scanHit = code
      handover = o to m
    }
  }

  val q = OrderDraft.latin(query).removePrefix("#").trim()
  val list = d.pickups.filter { o ->
    q.isEmpty() || o.orderNo.toString().contains(q) || o.customer.name.contains(query.trim(), ignoreCase = true)
  }

  SearchBox(query, { onSearch(it) }, if (cfg.picking) "شمارهٔ سفارش، نامِ مشتری یا اسکنِ QR" else "شمارهٔ سفارش یا نامِ مشتری", Modifier.fillMaxWidth())
  Spacer(Modifier.height(8.dp))
  if (list.isEmpty()) {
    TohidEmptyState(
      title = "سفارشِ دریافتِ حضوری نیست",
      description = if (query.isBlank()) "سفارش‌های «دریافت از دکان» که هنوز تحویل نشده‌اند این‌جا می‌آیند." else "با «$query» چیزی پیدا نشد.",
    )
    return
  }
  LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    items(list, key = { it.id }) { o ->
      val m = d.missionOf(o.id)?.takeIf { it.mode == "pickup" }
      TohidCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("#${o.orderNo}", fontWeight = FontWeight.Bold, color = c.text)
          Spacer(Modifier.width(8.dp))
          Text(o.customer.name, Modifier.weight(1f), color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
          OrderStatusPill(o.status)
        }
        Spacer(Modifier.height(4.dp))
        val expected = AutoAssign.expectedOf(o.paymentMethod, o.paymentStatus, o.totalMinor)
        Text(
          (if (expected > 0) "در صندوق گرفته شود: ${afn(expected)}" else "پرداخت‌شده") + " · ${o.itemsCount} قلم",
          style = MaterialTheme.typography.bodySmall, color = c.text,
        )
        val state = when {
          m == null -> "هنوز کدِ دریافت ساخته نشده"
          m.codeLocked -> "کد پس از ۵ تلاشِ نادرست قفل شد — «کدِ تازه» یا تأییدِ دستیِ مدیر"
          m.hasCode && !m.codeUsed -> "کد ساخته و به مشتری داده شده"
          else -> DeliveryText.status(m.status)
        }
        Text(state, style = MaterialTheme.typography.bodySmall, color = if (m?.codeLocked == true) c.warning else c.muted)
        if (o.status != "ready_for_pickup") {
          Text("تحویل وقتی ممکن است که سفارش «آمادهٔ دریافت» باشد (برگهٔ سفارش‌ها).", style = MaterialTheme.typography.labelSmall, color = c.muted)
        }
        if (writable) {
          Spacer(Modifier.height(8.dp))
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (m == null && cfg.perms.assign && cfg.pickup) {
              TohidButton("ساختنِ کدِ دریافت", busy = busyId == o.id, enabled = busyId == null, onClick = {
                busyId = o.id
                scope.launch {
                  val body = DeliveryWire.assignJson("pickup")
                  repo.assign(o.id, body, pickupKeys.getOrPut(o.id) { DeliveryRepository.OnceKey() }.of(body))
                    .onSuccess { a ->
                      onChanged()
                      if (a.code != null) shownCode = o.orderNo to a.code
                      //  کد پیش‌تر ساخته شده و پاسخش گم شده بود؛ سرور فقط HMACش را دارد
                      else snackbar.showSnackbar("کدِ دریافتِ سفارش #${o.orderNo} پیش‌تر ساخته شده بود؛ برای دیدنش «کدِ تازه» بزنید.")
                    }
                    .onFailure { snackbar.showSnackbar(it.userMessage) }
                  busyId = null
                }
              })
            }
            if (m != null) {
              DeskActions.of(m.copy(orderStatus = o.status), cfg.perms).forEach { a ->
                when (a) {
                  DeskActions.Act.PICKUP_HANDOVER -> TohidButton(DeskActions.label(a), onClick = { scanHit = ""; handover = o to m })
                  DeskActions.Act.NEW_CODE -> TohidSecondaryButton(DeskActions.label(a), onClick = { newCodeFor = m })
                  else -> {}
                }
              }
            }
          }
        }
      }
    }
  }

  handover?.let { (o, m) ->
    PickupHandoverDialog(
      order = o, m = m, cfg = cfg, repo = repo, ordersRepo = ordersRepo,
      onDismiss = { handover = null },
      onDone = { msg ->
        handover = null
        onChanged()
        scope.launch { snackbar.showSnackbar(msg) }
      },
      scanned = scanHit,
    )
  }
  newCodeFor?.let { m ->
    TohidConfirmDialog(
      title = "کدِ تازه برای سفارش #${m.orderNo}",
      message = "کدِ قبلی باطل می‌شود و قفلِ تلاش‌ها باز می‌شود. کدِ تازه را به مشتری بدهید.",
      confirmText = "بساز",
      onConfirm = {
        newCodeFor = null
        scope.launch {
          repo.newCode(m.id)
            .onSuccess { shownCode = m.orderNo to it; onChanged() }
            .onFailure { snackbar.showSnackbar(it.userMessage) }
        }
      },
      onDismiss = { newCodeFor = null },
    )
  }
  shownCode?.let { (no, code) -> CodeOnceDialog(no, code, qr = cfg.picking) { shownCode = null } }
}

/**
 *  پالایهٔ کادرِ کدِ تحویل. پرچمِ جمع‌آوری خاموش ⇒ همان رفتارِ پیشین (فقط رقم، تا شش‌تا).
 *  روشن (PCK-11) ⇒ QRِ اسکن‌شده و رقمِ فارسی هم؛ QRِ سفارشِ دیگر کادر را خالی می‌کند و خطا می‌گوید.
 */
internal fun scannedCode(raw: String, orderNo: Long, picking: Boolean, onError: (String) -> Unit): String {
  if (!picking) return raw.filter { ch -> ch.isDigit() }.take(6)
  val other = HandoverCode.otherOrder(raw, orderNo)
  if (other != null) {
    onError("این QR مالِ سفارش #$other است، نه #$orderNo")
    return ""
  }
  return HandoverCode.typed(raw)
}

@Composable
private fun PickupHandoverDialog(
  order: OrderDto,
  m: Mission,
  cfg: DeskConfig,
  repo: DeliveryRepository,
  ordersRepo: OrdersRepository,
  onDismiss: () -> Unit,
  onDone: (String) -> Unit,
  scanned: String = "",
) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  //  PCK-11: با اسکنِ QR در جست‌وجو، کد از همان اسکن می‌آید (کارمند فقط نام را تطبیق می‌دهد)
  var code by remember { mutableStateOf(scanned) }
  var manual by remember { mutableStateOf(false) }
  var note by remember { mutableStateOf("") }
  var cash by remember { mutableStateOf(if (m.expectedCollectMinor > 0) fieldOf(m.expectedCollectMinor) else "0") }
  var cashReason by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var err by remember { mutableStateOf<String?>(null) }
  //  یک کلیدِ یکتایی برای همین فرم — پاسخِ گم‌شده پس از ثبت، با دوباره زدن همان کار است
  val keys = remember { DeliveryRepository.OnceKey() }
  val cashMinor = minorOf(cash)
  val differs = cashMinor != null && cashMinor != m.expectedCollectMinor
  val valid = cashMinor != null && (!differs || cashReason.isNotEmpty()) &&
    (if (manual) note.isNotBlank() else code.length == 6) && (cashReason != "other" || note.isNotBlank())

  TohidBottomSheet(title = "تحویلِ حضوری — سفارش #${order.orderNo}", onDismiss = onDismiss) {
    SheetBody {
      Text("نامِ مشتری را تطبیق دهید:", style = MaterialTheme.typography.bodySmall, color = c.muted)
      Text(order.customer.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.text)
      if (!manual) {
        TohidTextField(
          code, { raw -> code = scannedCode(raw, order.orderNo, cfg.picking) { err = it } },
          label = if (cfg.picking) "کد یا QRی که مشتری نشان می‌دهد (۶ رقم)" else "کدی که مشتری نشان می‌دهد (۶ رقم)",
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
      }
      if (cfg.perms.assign) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = manual, onCheckedChange = { manual = it; err = null })
          Text("مشتری کد ندارد — تأییدِ دستیِ مدیر (با توضیح، در سابقه)", style = MaterialTheme.typography.bodySmall, color = c.text)
        }
      }
      TohidTextField(
        cash, { cash = it.take(14) }, label = "نقدِ گرفته‌شده در صندوق (افغانی)",
        supporting = if (m.expectedCollectMinor > 0) "مبلغِ سفارش: ${afn(m.expectedCollectMinor)}" else "پرداخت‌شده",
        error = if (cashMinor == null) "عدد درست نیست" else null,
        keyboardOptions = MONEY_KEYS,
      )
      if (differs) {
        Text("مبلغ با سفارش فرق دارد — دلیل:", style = MaterialTheme.typography.labelLarge, color = c.warning)
        ReasonChips(reasonsOf(cfg, "cash"), cashReason) { cashReason = it }
      }
      if (manual || cashReason == "other") {
        TohidTextField(note, { note = it.take(300) }, label = "توضیح (لازم)", singleLine = false)
      }
      ErrorLine(err)
      TohidButton(
        "تحویل شد",
        busy = busy,
        enabled = valid && !busy,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
          busy = true
          err = null
          scope.launch {
            val body = DeliveryWire.statusJson(
              to = "delivered", from = m.status, note = note, code = if (manual) "" else code,
              cashCollectedMinor = cashMinor, cashReason = if (differs) cashReason else "",
            )
            when (val r = repo.status(m.id, body, keys.of(body))) {
              is ApiResult.Failure -> {
                err = r.error.userMessage
                if (r.error.code == "bad_code") code = ""
              }
              is ApiResult.Success -> onDone(recordSale(order, m, cashMinor ?: 0L, ordersRepo))
            }
            busy = false
          }
        },
      )
    }
  }
}

/**
 *  پس از «تحویل شد»: فروشِ همین سفارش از راهِ قلابِ `HandoverSale` (#340،
 *  `OrdersLive.postById`: از سرور می‌پرسد و با شناسهٔ سفارش یکتا ثبت می‌کند). تحویل
 *  پیش‌تر در سرور ثبت شده؛ اگر ثبت همین حالا نشد، برنامه آن را از فهرستِ «تحویلِ
 *  بی‌فروش» دوباره ثبت می‌کند — دیگر لازم نیست کسی در صندوق دستی بزند (دو بار می‌شد).
 */
private suspend fun recordSale(order: OrderDto, m: Mission, collected: Long, ordersRepo: OrdersRepository): String {
  val hook = HandoverSale.hook
    ?: return "سفارش #${order.orderNo} تحویل شد؛ فروشش را برنامه از «سفارش‌ها ⇐ تحویلِ بی‌فروش» ثبت می‌کند."
  //  قلاب فقط شناسهٔ سفارش را لازم دارد؛ جزئیات را خودش از سرور می‌گیرد
  val full = ordersRepo.get(order.id).valueOrNull() ?: order
  val handed = HandedOverOrder(
    kind = HandoverKind.PICKUP_AT_STORE,
    orderId = full.id, orderNo = full.orderNo, assignmentId = m.id, customerName = full.customer.name,
    paymentMethod = full.paymentMethod, paymentStatus = full.paymentStatus,
    subtotalMinor = full.subtotalMinor, discountMinor = full.discountMinor, deliveryFeeMinor = full.deliveryFeeMinor,
    totalMinor = full.totalMinor, collectedMinor = collected,
    lines = full.lines.map { HandedOverLine(it.productId, it.name, it.qtyMilli, it.unitPriceMinor, it.lineTotalMinor) },
  )
  return when (val r = runCatching { hook.record(handed) }.getOrElse { HandoverSaleResult.Failed(it.message ?: "خطا") }) {
    is HandoverSaleResult.Recorded -> "سفارش #${order.orderNo} تحویل و فروشش ثبت شد."
    is HandoverSaleResult.AlreadyRecorded -> "سفارش #${order.orderNo} تحویل شد؛ فروشش پیش‌تر ثبت شده بود."
    is HandoverSaleResult.Failed -> "سفارش #${order.orderNo} تحویل شد؛ فروش همین حالا ثبت نشد (${r.message}) — برنامه دوباره می‌کوشد، در صندوق دستی نزنید."
    //  بررسیِ صاحب: نه وعدهٔ «دوباره می‌کوشد»، فقط دلیل
    is HandoverSaleResult.NeedsReview -> "سفارش #${order.orderNo} تحویل شد؛ فروشش خودکار ثبت نمی‌شود — ${r.message}"
  }
}
