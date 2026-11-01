package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.vil3ntec.tohid.core.gateway.GatewayCall
import ir.vil3ntec.tohid.core.gateway.HesabPayAttempts
import ir.vil3ntec.tohid.core.gateway.IntentPoller
import ir.vil3ntec.tohid.core.gateway.IntentStatus
import ir.vil3ntec.tohid.core.gateway.PaymentIntent
import ir.vil3ntec.tohid.core.gateway.PendingPayment
import ir.vil3ntec.tohid.core.i18n.StrPay
import ir.vil3ntec.tohid.core.pay.Settlement
import ir.vil3ntec.tohid.core.qr.QrCode
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.pay.HceSlot
import ir.vil3ntec.tohid.pay.HesabPaySettle
import ir.vil3ntec.tohid.pay.platform.HesabPayEnv
import ir.vil3ntec.tohid.pay.platform.PayDevice
import ir.vil3ntec.tohid.ui.theme.LightColors
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 *  حساب‌پی در صندوق (PAY-21، PAY-08) — دکمهٔ «پرداخت با حساب‌پی» و برگهٔ تمام‌صفحهٔ QR.
 *
 *  - دکمه فقط وقتی دیده می‌شود که سرور برای همین دکان روشنش کرده باشد (`pay.hesabpay`) و نت باشد؛
 *    وگرنه هیچ‌چیز رسم نمی‌شود و صندوق همان امروز است.
 *  - برگه: QR ِ مبلغِ دقیق (`qrPayload`)، مبلغِ درشت، شمارشِ معکوس، وضعیت، «لغو»؛ NFC (اندروید) تا برگه
 *    باز است همان نشانی را به گوشیِ مشتری می‌دهد.
 *  - «پرداخت شد» ⇐ `onPaid(تسویه)` — همان راهِ ثبتِ پرداخت‌های دیگر (`SalesEngine.record` با `settlement`)؛
 *    ناموفق/گذشته/لغو ⇐ برگشت به انتخابِ روش، فروش بسته نمی‌شود.
 *  - کلیدِ یکتایی و شناسهٔ قصد کنارِ کلیدِ فاکتور می‌مانند (`HesabPayAttempts`): بسته شدنِ برنامه وسطِ
 *    پرداخت ⇐ بازکردنِ دوبارهٔ همان فاکتور همان قصد را می‌پاید (برگه خودش باز می‌شود).
 *
 *  منطق در `core/gateway` و `pay/HesabPaySettle`؛ این‌جا فقط صفحه. `LocalContext` نمی‌گیرد: محیط را
 *  صفحهٔ صدازننده می‌دهد (`HesabPayEnv.of(context)`).
 */

/**
 *  آنچه صفحهٔ صندوق به درگاه‌ها می‌دهد: محیط، کلیدِ فاکتور (= شناسهٔ فروش) و سنجشِ «همین حالا ثبت‌شدنی است؟»
 *  (موجودی، تکرار) که **پیش از** گرفتنِ پول زده می‌شود — پیامِ خطا یا `null`.
 */
class GatewayHook(val env: HesabPayEnv, val saleKey: String?, val precheck: () -> String?)

/**
 *  روش‌های درگاهیِ تسویه — تنها قلابِ صفحه‌های صندوق (`CheckoutDialog`، `DeskSale`، `PayPanelDialog`).
 *  `panel` = پنلِ ترکیبیِ همین تسویه (با پرچمِ `payV2`)؛ `null` = صندوقِ امروز (حساب‌پی کلِ فاکتور).
 */
@Composable
fun CheckoutGateways(
  hook: GatewayHook,
  total: Double,
  panel: PayPanelState?,
  customerId: String?,
  enabled: Boolean,
  busy: Boolean,
  onPaid: (Settlement) -> Unit,
  modifier: Modifier = Modifier,
) {
  HesabPayEntry(hook, total, panel?.draft(total, customerId), enabled, busy, onPaid, modifier)
}

/** دکمهٔ «پرداخت با حساب‌پی» و برگه‌اش. `draft` = پنلِ ترکیبی (حساب‌پی باقی را می‌پردازد)، `null` = کلِ فاکتور */
@Composable
fun HesabPayEntry(
  hook: GatewayHook,
  total: Double,
  draft: PayFlow.Draft?,
  enabled: Boolean,
  busy: Boolean,
  onPaid: (Settlement) -> Unit,
  modifier: Modifier = Modifier,
) {
  val env = hook.env
  val saleKey = hook.saleKey
  var available by remember { mutableStateOf(false) }
  //  هر فاکتورِ تازه دوباره می‌پرسد (یک دقیقه در حافظه): پرچم یا نت که عوض شد، صفحهٔ ماندگارِ کامپیوتر هم می‌فهمد
  LaunchedEffect(env, saleKey) { available = env.available() }
  if (!available || saleKey == null) return
  val due = HesabPaySettle.due(total, draft)
  if (due !is HesabPaySettle.Due.Amount) return
  var open by remember(saleKey) { mutableStateOf(false) }
  val stored = remember(saleKey, open) { env.pending.of(saleKey) }
  val resumable = stored != null && stored.amountMinor == due.minor && stored.status in RESUMABLE
  //  برنامه وسطِ پرداخت بسته شده بود ⇐ همان پرداخت خودش دوباره باز و پاییده می‌شود
  LaunchedEffect(saleKey, resumable) { if (resumable && enabled) open = true }

  Column(modifier) {
    OutlinedButton(
      onClick = { open = true },
      enabled = enabled && !busy,
      modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
    ) { Text(if (resumable) StrPay.resumeHesab else StrPay.payWithHesab) }
    if (draft != null) {
      Text(
        StrPay.coversRest(amountText(due.minor)),
        style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
      )
    }
  }
  if (open) {
    HesabPaySheet(
      env = env, saleKey = saleKey, amountMinor = due.minor, total = total, draft = draft, busy = busy,
      precheck = hook.precheck, onPaid = onPaid, onClose = { open = false },
    )
  }
}

private val RESUMABLE = setOf(IntentStatus.CREATED.wire, IntentStatus.PENDING.wire, IntentStatus.PAID.wire, IntentStatus.UNKNOWN.wire)

/** مبلغِ دقیق به افغانی، با اعشارِ پول اگر هست (رقم را قلم فارسی می‌کند) */
internal fun amountText(minor: Long): String {
  val whole = ir.vil3ntec.tohid.money((minor / 100).toDouble())
  val cents = minor % 100
  return if (cents == 0L) whole else whole + "." + cents.toString().padStart(2, '0')
}

private sealed interface Phase {
  data object Starting : Phase
  data class Live(val intent: PaymentIntent, val trouble: Boolean = false) : Phase
  data class Paid(val intent: PaymentIntent, val settlement: Settlement) : Phase
  data class Ended(val message: String, val retry: Boolean) : Phase
}

@Composable
private fun HesabPaySheet(
  env: HesabPayEnv,
  saleKey: String,
  amountMinor: Long,
  total: Double,
  draft: PayFlow.Draft?,
  busy: Boolean,
  precheck: () -> String?,
  onPaid: (Settlement) -> Unit,
  onClose: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  var phase by remember { mutableStateOf<Phase>(Phase.Starting) }
  //  هر «دوباره» یک دورِ تازه؛ «لغو» دورِ جاری را می‌بندد (کلیدِ اثر عوض می‌شود)
  var round by remember { mutableIntStateOf(0) }
  var running by remember { mutableStateOf(true) }
  //  سنجشِ تازهٔ صندوق (دفترِ همین لحظه)، نه آنچه هنگامِ باز شدن بود
  val check by rememberUpdatedState(precheck)
  val flow = remember { SheetFlow(env, saleKey, amountMinor, total, draft) { check() } }

  fun paid(i: PaymentIntent) {
    when (val s = flow.settle(i)) {
      is PayFlow.Outcome.Ok -> { phase = Phase.Paid(i, s.value); onPaid(s.value) }
      is PayFlow.Outcome.Bad -> phase = Phase.Ended(StrPay.amountMismatch, retry = false)
    }
  }

  LaunchedEffect(round, running) {
    if (!running) return@LaunchedEffect
    phase = Phase.Starting
    val start = flow.start()
    if (start !is SheetFlow.Step.Show) { phase = (start as SheetFlow.Step.Stop).phase; return@LaunchedEffect }
    var cur = start.intent
    phase = Phase.Live(cur)
    val end = IntentPoller(env.client, now = env::now, sleep = { delay(it) }).run(cur) { ev ->
      when (ev) {
        is IntentPoller.Event.Update -> { cur = ev.intent; flow.seen(cur); phase = Phase.Live(cur) }
        is IntentPoller.Event.Trouble -> phase = Phase.Live(cur, trouble = true)
      }
    }
    when (end) {
      is IntentPoller.End.Final -> {
        flow.seen(end.intent)
        if (end.intent.status == IntentStatus.PAID) paid(end.intent) else phase = Phase.Ended(endText(end.intent.status), retry = end.intent.status != IntentStatus.REFUNDED)
      }
      is IntentPoller.End.TimedOut -> phase = Phase.Ended(StrPay.unknown, retry = true)
      is IntentPoller.End.Lost -> { flow.forget(); phase = Phase.Ended(StrPay.lost, retry = true) }
    }
  }

  /** «لغو»: حلقه می‌ایستد، لغو به سرور می‌رود؛ اگر همان دم پرداخت شده بود، فروش بسته می‌شود */
  fun cancel() {
    val live = (phase as? Phase.Live)?.intent
    running = false
    if (live == null) { onClose(); return }
    phase = Phase.Starting
    scope.launch {
      when (val r = flow.cancel(live)) {
        is SheetFlow.Step.Show -> if (r.intent.status == IntentStatus.PAID) paid(r.intent) else onClose()
        is SheetFlow.Step.Stop -> phase = r.phase
      }
    }
  }

  //  NFC: تا QR زنده است همان نشانی؛ با بسته شدنِ برگه پاک
  val live = (phase as? Phase.Live)?.intent
  DisposableEffect(live?.qrPayload) {
    HceSlot.url = live?.qrPayload?.takeIf { it.isNotBlank() }
    onDispose { HceSlot.url = null }
  }
  PayDevice.HceForeground(active = live != null)

  Dialog(
    onDismissRequest = { if (phase is Phase.Live || phase is Phase.Starting) cancel() else onClose() },
    properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
  ) {
    Surface(color = Shop.colors.bg, modifier = Modifier.fillMaxSize()) {
      BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val wide = maxWidth > maxHeight && maxWidth > 560.dp
        val qrSide = (if (wide) minOf(maxHeight - 32.dp, maxWidth / 2) else minOf(maxWidth, maxHeight * 0.5f)).coerceAtMost(420.dp)
        val qr: @Composable () -> Unit = { QrPanel(phase, qrSide) }
        val info: @Composable () -> Unit = {
          InfoPanel(
            env = env, phase = phase, amountMinor = amountMinor, skewMs = flow.skewMs, busy = busy,
            onCancel = ::cancel,
            onRetry = { running = true; round++ },
            onClose = onClose,
            onRecord = { (phase as? Phase.Paid)?.let { onPaid(it.settlement) } },
          )
        }
        if (wide) {
          Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { qr() }
            Box(Modifier.weight(1f)) { info() }
          }
        } else {
          Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            qr()
            Spacer(Modifier.height(16.dp))
            info()
          }
        }
      }
    }
  }
}

private fun endText(s: IntentStatus): String = when (s) {
  IntentStatus.FAILED -> StrPay.failed
  IntentStatus.EXPIRED -> StrPay.expired
  IntentStatus.CANCELLED -> StrPay.cancelled
  IntentStatus.REFUNDED -> StrPay.refunded
  else -> StrPay.unknown
}

@Composable
private fun QrPanel(phase: Phase, side: androidx.compose.ui.unit.Dp) {
  val payload = when (phase) {
    is Phase.Live -> phase.intent.qrPayload
    else -> ""
  }
  val rows = remember(payload) {
    if (payload.isBlank()) null else runCatching { QrCode.encode(payload, QrCode.LARGE_MAX_VERSION) }.getOrNull()
  }
  //  کادرِ QR در هر دو تم روشن است (QR ِ وارونه را بیشترِ دوربین‌ها نمی‌خوانند)؛
  //  پس رنگ‌ها از پالتِ روزِ Theme.kt می‌آیند، نه از تمِ جاری و نه عددِ ثابت (F0-18)
  Box(
    Modifier.size(side).clip(RoundedCornerShape(16.dp)).background(LightColors.surface),
    contentAlignment = Alignment.Center,
  ) {
    if (rows != null) QrImage(rows, Modifier.fillMaxSize().padding(8.dp))
    else Text(
      when (phase) {
        is Phase.Paid -> "✓"
        else -> "…"
      },
      fontSize = 64.sp, color = LightColors.textPrimary,
    )
  }
}

/** ماتریسِ QR با حاشیهٔ خالیِ استاندارد؛ همیشه سیاه روی سفید (تمِ تیره هم) تا هر اسکنری بخواند */
@Composable
fun QrImage(rows: Array<BooleanArray>, modifier: Modifier = Modifier) {
  Canvas(modifier) {
    val n = rows.size + 2 * QrCode.QUIET_ZONE
    val cell = size.minDimension / n
    val ox = (size.width - cell * n) / 2 + cell * QrCode.QUIET_ZONE
    val oy = (size.height - cell * n) / 2 + cell * QrCode.QUIET_ZONE
    drawRect(Color.White)
    for (y in rows.indices) for (x in rows[y].indices) {
      if (rows[y][x]) drawRect(Color.Black, topLeft = Offset(ox + x * cell, oy + y * cell), size = Size(cell + 0.6f, cell + 0.6f))
    }
  }
}

@Composable
private fun InfoPanel(
  env: HesabPayEnv,
  phase: Phase,
  amountMinor: Long,
  skewMs: Long,
  busy: Boolean,
  onCancel: () -> Unit,
  onRetry: () -> Unit,
  onClose: () -> Unit,
  onRecord: () -> Unit,
) {
  val c = Shop.colors
  Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(StrPay.hesabTitle, style = MaterialTheme.typography.titleLarge, color = c.text, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text("${amountText(amountMinor)} ؋", fontSize = 44.sp, color = c.primary, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))

    when (phase) {
      Phase.Starting -> Text(StrPay.creating, color = c.muted, textAlign = TextAlign.Center)
      is Phase.Live -> {
        Text(StrPay.scanHint, color = c.text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        NfcHint(env)
        Spacer(Modifier.height(10.dp))
        Text(
          if (phase.trouble) StrPay.reconnecting else StrPay.waiting,
          color = if (phase.trouble) c.warning else c.muted, textAlign = TextAlign.Center,
        )
        if (phase.intent.needsReview) Text(StrPay.needsReview, color = c.warning, textAlign = TextAlign.Center)
        Countdown(env, phase.intent.expiresAt, skewMs)
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(StrPay.cancel) }
      }
      is Phase.Paid -> {
        Text(StrPay.paidRecording, color = c.success, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        //  ثبت نشد (پیامِ صندوق آمده) و برگه هنوز باز است ⇐ همان تسویه دوباره؛ فروشِ دوم ساخته نمی‌شود
        var ready by remember { mutableStateOf(false) }
        LaunchedEffect(busy) { ready = false; if (!busy) { delay(1_500); ready = true } }
        Button(onClick = onRecord, enabled = ready && !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(StrPay.recordSale) }
      }
      is Phase.Ended -> {
        Text(phase.message, color = c.danger, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f).height(52.dp)) { Text(StrPay.close) }
          if (phase.retry) Button(onClick = onRetry, modifier = Modifier.weight(1f).height(52.dp)) { Text(StrPay.retry) }
        }
      }
    }
  }
}

/** «گوشی را نزدیک کنید» فقط با NFC ِ روشن؛ خاموش ⇐ میان‌برِ تنظیمات؛ بی NFC (و کامپیوتر) ⇐ هیچ */
@Composable
private fun NfcHint(env: HesabPayEnv) {
  var nfc by remember { mutableStateOf(env.nfc()) }
  LaunchedEffect(Unit) { while (true) { delay(2_000); nfc = env.nfc() } }
  when (nfc) {
    PayDevice.Nfc.READY -> {
      Spacer(Modifier.height(6.dp))
      Text(StrPay.tapHint, color = Shop.colors.primary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
    PayDevice.Nfc.OFF -> Row(verticalAlignment = Alignment.CenterVertically) {
      Text(StrPay.nfcOff, color = Shop.colors.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f, fill = false))
      TextButton(onClick = { env.openNfcSettings() }) { Text(StrPay.nfcSettings) }
    }
    PayDevice.Nfc.NONE -> Unit
  }
}

@Composable
private fun Countdown(env: HesabPayEnv, expiresAt: Long?, skewMs: Long) {
  if (expiresAt == null) return
  var now by remember { mutableLongStateOf(env.now()) }
  LaunchedEffect(expiresAt) { while (true) { now = env.now(); delay(1_000) } }
  val left = ((expiresAt - (now - skewMs)) / 1000).coerceAtLeast(0)
  val text = "${left / 60}:${(left % 60).toString().padStart(2, '0')}"
  Text(StrPay.expiresIn(text), color = if (left < 60) Shop.colors.warning else Shop.colors.muted, style = MaterialTheme.typography.labelLarge)
}

/**
 *  گام‌های برگه، بی Compose: آغاز (ازسرگیری، جایگزینی یا ساختن)، دیدنِ هر وضعیت، لغو و تسویه.
 *  هر رکورد پیش از درخواست ذخیره می‌شود؛ کلیدِ یکتایی تا تلاشِ تازه همان می‌ماند.
 */
private class SheetFlow(
  private val env: HesabPayEnv,
  private val saleKey: String,
  private val amountMinor: Long,
  private val total: Double,
  private val draft: PayFlow.Draft?,
  private val precheck: () -> String?,
) {
  sealed interface Step {
    data class Show(val intent: PaymentIntent) : Step
    data class Stop(val phase: Phase) : Step
  }

  private var rec: PendingPayment? = null
  val skewMs: Long get() = rec?.skewMs ?: 0

  suspend fun start(): Step = when (val plan = HesabPayAttempts.plan(env.pending.of(saleKey), saleKey, amountMinor, env.now())) {
    is HesabPayAttempts.Plan.Resume -> resume(plan.pending)
    is HesabPayAttempts.Plan.Replace -> replace(plan.old, plan.fresh)
    is HesabPayAttempts.Plan.Fresh -> create(plan.pending)
  }

  /** همان قصد: بی شناسه (پاسخِ ساختن گم شد) ⇐ همان کلید دوباره؛ با شناسه ⇐ خواندن */
  private suspend fun resume(p: PendingPayment): Step {
    rec = p
    val id = p.intentId ?: return create(p)
    return when (val r = env.client.fetch(id, 0)) {
      is GatewayCall.Ok -> show(r.value)
      is GatewayCall.Refused -> if (r.code == "payment_not_found") { forget(); start() } else stop(r.message.ifEmpty { StrPay.unreachable }, true)
      is GatewayCall.Offline -> stop(StrPay.unreachable, true)
      GatewayCall.Disabled -> stop(StrPay.unreachable, true)
    }
  }

  /** سبد پس از QR عوض شد ⇐ اول لغوِ قصدِ قبلی؛ اگر همان دم پرداخت شده بود، فروش بسته نمی‌شود */
  private suspend fun replace(old: PendingPayment, fresh: PendingPayment): Step {
    val oldId = old.intentId ?: return create(fresh)
    val now: PaymentIntent? = when (val gone = env.client.cancel(oldId)) {
      is GatewayCall.Ok -> gone.value
      is GatewayCall.Refused ->
        if (gone.code == "payment_not_found") null
        else (env.client.fetch(oldId, 0) as? GatewayCall.Ok<PaymentIntent>)?.value ?: return stop(StrPay.unreachable, true)
      is GatewayCall.Offline -> return stop(StrPay.unreachable, true)
      GatewayCall.Disabled -> return stop(StrPay.unreachable, true)
    }
    if (now != null && now.status == IntentStatus.PAID) {
      env.pending.save(HesabPayAttempts.withIntent(old, now))
      return stop(StrPay.amountMismatch, false)
    }
    return create(fresh)
  }

  private suspend fun create(p: PendingPayment): Step {
    //  پیش از گرفتنِ پول: فاکتوری که ثبت‌شدنی نیست (موجودی، تکرار) قصد نمی‌گیرد
    precheck()?.let { return stop(it, true) }
    rec = p
    env.pending.save(p)
    return when (val r = env.client.create(saleKey, amountMinor, p.idempotencyKey)) {
      is GatewayCall.Ok -> show(r.value, fresh = true)
      is GatewayCall.Refused -> when (r.code) {
        "provider_unavailable" -> { env.pending.save(p.copy(status = IntentStatus.FAILED.wire)); stop(StrPay.providerDown, true) }
        "already_paid" -> stop(StrPay.alreadyPaid, false)
        "intent_active" -> stop(StrPay.otherOpen, true)
        "hesabpay_disabled" -> stop(StrPay.disabled, false)
        "idempotency_key_reused" -> { forget(); stop(StrPay.unreachable, true) }
        else -> stop(r.message.ifEmpty { StrPay.unreachable }, true)
      }
      is GatewayCall.Offline -> stop(StrPay.unreachable, true)
      GatewayCall.Disabled -> stop(StrPay.disabled, false)
    }
  }

  private fun show(i: PaymentIntent, fresh: Boolean = false): Step {
    //  هرگز قصدِ فاکتورِ دیگر یا مبلغِ دیگر روی این فاکتور
    if ((i.refId.isNotEmpty() && i.refId != saleKey) || i.amountMinor != amountMinor) return stop(StrPay.amountMismatch, false)
    seen(i, if (fresh) env.now() else null)
    return Step.Show(i)
  }

  fun seen(i: PaymentIntent, localNow: Long? = null) {
    val r = rec ?: return
    val next = HesabPayAttempts.withIntent(r, i, localNow)
    rec = next
    env.pending.save(next)
  }

  fun forget() {
    rec = null
    env.pending.forget(saleKey)
  }

  suspend fun cancel(i: PaymentIntent): Step = when (val r = env.client.cancel(i.id)) {
    is GatewayCall.Ok -> { seen(r.value); Step.Show(r.value) }
    is GatewayCall.Refused -> when (r.code) {
      //  دیگر لغوشدنی نیست ⇐ شاید همین الان پرداخت شد
      "not_cancellable" -> when (val f = env.client.fetch(i.id, 0)) {
        is GatewayCall.Ok -> { seen(f.value); Step.Show(f.value) }
        else -> stop(StrPay.unknown, true)
      }
      "payment_not_found" -> { forget(); stop(StrPay.cancelled, true) }
      else -> stop(r.message.ifEmpty { StrPay.unknown }, true)
    }
    is GatewayCall.Offline, GatewayCall.Disabled -> stop(StrPay.unknown, true)
  }

  fun settle(i: PaymentIntent): PayFlow.Outcome<Settlement> = HesabPaySettle.settle(total, draft, saleKey, i)

  private fun stop(message: String, retry: Boolean) = Step.Stop(Phase.Ended(message, retry))
}
