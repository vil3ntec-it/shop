package ir.vil3ntec.tohid.ui.v3.sales.checkout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.core.gateway.HesabTill
import ir.vil3ntec.tohid.core.gateway.IntentPoller
import ir.vil3ntec.tohid.core.i18n.StrPay
import ir.vil3ntec.tohid.core.i18n.StrTillPay
import ir.vil3ntec.tohid.core.qr.QrCode
import ir.vil3ntec.tohid.pay.CustomerPay
import ir.vil3ntec.tohid.pay.HceSlot
import ir.vil3ntec.tohid.pay.HesabHold
import ir.vil3ntec.tohid.pay.HesabPayIntentFlow
import ir.vil3ntec.tohid.pay.platform.HesabPayEnv
import ir.vil3ntec.tohid.pay.platform.PayDevice
import ir.vil3ntec.tohid.ui.ProvideLang
import ir.vil3ntec.tohid.ui.components.pos.CustomerPayScreen
import ir.vil3ntec.tohid.ui.components.pos.QrImage
import ir.vil3ntec.tohid.ui.components.pos.amountText
import ir.vil3ntec.tohid.ui.theme.LightColors
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 *  `sale/checkout/hesabpay` — «پرداخت با حساب‌پی (NFC/QR)» در صندوقِ گوشیِ نسخهٔ ۳ (PAY-21، پشتِ `pay.hesabpay`).
 *
 *  فروشنده در برگهٔ تسویه این را می‌زند ⇒ این برگه: مبلغِ درشت، QR (جایگزینِ NFC و تنها راه در آیفون/کامپیوتر)، «پول به
 *  حسابِ <نام> — <۴ رقم>» و کدِ یکتای فاکتور؛ گوشیِ فروشنده تا «در انتظار» است برچسبِ NFC ِ نوعِ ۴ را با HCE نشان می‌دهد
 *  (`HceSlot.ndef` ⇐ `HesabTillModel.tag`، فقط اندروید با NFC). گوشیِ مشتری (با NFC و برنامهٔ حساب‌پی یا مرورگر) نشانی
 *  را باز می‌کند و می‌پردازد. تأیید فقط با `HesabTill`: «خودکار» = قصدِ سرور با مرجع، مبلغ و گیرندهٔ همه‌خوان؛ «ساده» =
 *  «پول رسید» ِ فروشنده. تأییدشده ⇒ تسویه در `CheckoutSession.hesabPaid` و برگشت؛ تسویه همان دم با همان راهِ ثبت.
 *  نمایشگرِ رو به مشتری (HW-10) هم همان مبلغ و QR. ⛔ دستگاه‌های واقعی آزموده نشده‌اند (NOT-TESTED).
 */
@Composable
fun RouteScope.HesabTillRoute() {
  val ask = CheckoutSession.hesab
  //  محیطِ حساب‌پی را برگهٔ تسویه می‌دهد (همان نمونهٔ `HesabPayEnv.of`)؛ پیوندِ مستقیم بی تسویه ⇒ برگشت
  val env = CheckoutSession.hesabEnv
  if (ask == null || env == null) { LaunchedEffect(Unit) { back() }; return }
  HesabTillSheet(ask, env, onPaid = { st -> CheckoutSession.hesabPaid = st; CheckoutSession.hesab = null; back() }, onClose = { CheckoutSession.hesab = null; back() })
}

@Composable
private fun HesabTillSheet(ask: HesabTillModel.Ask, env: HesabPayEnv, onPaid: (ir.vil3ntec.tohid.core.pay.Settlement) -> Unit, onClose: () -> Unit) {
  val c = PhoneV3.colors
  val scope = rememberCoroutineScope()
  var state by remember { mutableStateOf(HesabTillModel.start(ask, env.now())) }
  /** شکستِ ساختن/پاییدنِ قصد («دوباره» شدنی یا نه) — جدا از حالتِ تأیید */
  var stop by remember { mutableStateOf<HesabPayIntentFlow.Step.Stop?>(null) }
  var round by remember { mutableIntStateOf(0) }
  var error by remember { mutableStateOf<String?>(null) }
  val check by rememberUpdatedState(ask.precheck)
  val amount = state?.expect?.amountMinor
  if (state == null || amount == null) { LaunchedEffect(Unit) { onClose() }; return }
  fun on(e: HesabTill.Event) { state = state?.let { HesabTill.step(it, e) } }
  //  یک دور = یک قصد (کلیدِ یکتایی در دفترِ تلاش‌های همین دستگاه، `HesabPayAttempts`)؛ «دوباره» دورِ تازه
  val flow = remember(round) { HesabPayIntentFlow(env, ask.saleKey, amount, ask.total, ask.draft) { check() } }

  //  «خودکار»: قصدِ سرور (ساختن یا ازسرگیری با همان کلید) و پاییدنِ وضعیت؛ «ساده» شبکه ندارد. پایانی ⇒ پاییدن می‌ایستد
  val polling = state is HesabTill.State.Pending || state is HesabTill.State.Cancelling
  LaunchedEffect(round, polling) {
    if (ask.mode != HesabTill.Mode.AUTO || state !is HesabTill.State.Pending) return@LaunchedEffect
    stop = null
    when (val first = flow.start()) {
      is HesabPayIntentFlow.Step.Stop -> stop = first
      is HesabPayIntentFlow.Step.Show -> {
        on(HesabTill.Event.Gateway(first.intent))
        if (state is HesabTill.State.Pending) {
          when (val end = IntentPoller(env.client, now = env::now, sleep = { delay(it) }).run(first.intent) { ev ->
            if (ev is IntentPoller.Event.Update) { flow.seen(ev.intent); on(HesabTill.Event.Gateway(ev.intent)) }
          }) {
            is IntentPoller.End.Final -> { flow.seen(end.intent); on(HesabTill.Event.Gateway(end.intent)) }
            is IntentPoller.End.TimedOut -> on(HesabTill.Event.Tick(Long.MAX_VALUE))
            is IntentPoller.End.Lost -> { flow.forget(); stop = HesabPayIntentFlow.Step.Stop(StrPay.lost, true) }
          }
        }
      }
    }
  }
  //  ساعت: پایانِ «در انتظار»
  LaunchedEffect(Unit) { while (true) { delay(1_000); on(HesabTill.Event.Tick(env.now())) } }

  fun cancel() {
    val live = (state as? HesabTill.State.Pending)?.intent
    on(HesabTill.Event.Cancel)
    if (ask.mode == HesabTill.Mode.AUTO && live != null) scope.launch {
      when (val r = flow.cancel(live)) {
        is HesabPayIntentFlow.Step.Show -> on(HesabTill.Event.Gateway(r.intent))
        is HesabPayIntentFlow.Step.Stop -> stop = r
      }
    }
  }

  fun moneyArrived() {
    check()?.let { error = it; return }
    on(HesabTill.Event.SellerConfirm)
  }

  //  تأیید ⇒ تسویه و برگشت به تسویهٔ فاکتور (ثبت همان‌جا، با همان راه)
  val s = state
  LaunchedEffect(s) {
    if (s is HesabTill.State.Confirmed) HesabTillModel.settle(ask, s)?.let(onPaid) ?: run { error = StrPay.amountMismatch }
  }

  //  NFC (HCE) و نمایشگرِ رو به مشتری فقط تا «در انتظار»
  val qr = HesabTillModel.link(ask, s)
  val tag = remember(s) { HesabTillModel.tag(ask, s) }
  DisposableEffect(tag) {
    HceSlot.ndef = tag
    onDispose { HceSlot.ndef = null }
  }
  PayDevice.HceForeground(active = tag != null)
  val shown = when (s) {
    is HesabTill.State.Pending -> qr?.let { CustomerPay.View(amount, it, CustomerPay.Stage.WAITING) }
    is HesabTill.State.Confirmed -> CustomerPay.View(amount, "", CustomerPay.Stage.PAID)
    else -> null
  }
  LaunchedEffect(shown) { CustomerPay.view.value = shown }
  DisposableEffect(Unit) { onDispose { CustomerPay.view.value = null } }
  val storeName = remember { env.storeName() }
  PayDevice.CustomerScreen(active = shown != null) {
    TohidTheme { ProvideLang { CustomerPay.view.value?.let { CustomerPayScreen(storeName, it) } } }
  }

  val live = s is HesabTill.State.Pending || s is HesabTill.State.Cancelling
  V3Sheet(onDismiss = { if (live) cancel() else onClose() }, title = StrTillPay.title, dismissible = !live, detents = false) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
      Text("${amountText(amount)} ؋", style = V3Type.figure.copy(fontSize = 34.sp, lineHeight = 42.sp), color = c.ink)
      Spacer(Modifier.height(10.dp))
      QrBox(qr.takeIf { s is HesabTill.State.Pending })
      ask.payee?.let { p ->
        Text(StrPay.payeeLine(p.name.ifBlank { "—" }, p.last4), style = V3Type.headline, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
      }
      Text("${StrPay.invoiceCode}: ${ask.ref}", style = V3Type.callout, color = c.ink, modifier = Modifier.padding(top = 4.dp))
      Spacer(Modifier.height(8.dp))
      if (s is HesabTill.State.Pending) NfcLine(env)
      Text(StrTillPay.customerNeeds, style = V3Type.footnote, color = c.inkMuted, textAlign = TextAlign.Center)
      Spacer(Modifier.height(10.dp))
      Status(ask, s, stop, error)
      Spacer(Modifier.height(12.dp))
      Text(if (ask.mode == HesabTill.Mode.AUTO) StrTillPay.autoHonest else StrTillPay.simpleHonest, style = V3Type.footnote, color = c.inkMuted, textAlign = TextAlign.Center)
      Spacer(Modifier.height(14.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (live) {
          V3Button(StrPay.cancel, ::cancel, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY, enabled = s is HesabTill.State.Pending)
          //  ⛔ «پول رسید» فقط در «ساده»؛ در «خودکار» فقط تأییدِ همه‌خوانِ حساب‌پی فاکتور را می‌بندد
          if (ask.mode == HesabTill.Mode.SIMPLE) V3Button(StrPay.moneyArrived, ::moneyArrived, Modifier.weight(1f), haptic = true)
        } else {
          V3Button(StrPay.close, onClose, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY, enabled = s !is HesabTill.State.Confirmed)
          if (stop?.retry == true || (s as? HesabTill.State.Ended)?.why == HesabTill.End.TIMED_OUT) {
            V3Button(StrPay.retry, { state = HesabTillModel.start(ask, env.now()); stop = null; error = null; round++ }, Modifier.weight(1f))
          }
        }
      }
    }
  }
}

@Composable
private fun QrBox(payload: String?) {
  val rows = remember(payload) { payload?.takeIf { it.isNotBlank() }?.let { runCatching { QrCode.encode(it, QrCode.LARGE_MAX_VERSION) }.getOrNull() } }
  //  کادرِ QR در هر دو تم روشن (QR ِ وارونه را بیشترِ دوربین‌ها نمی‌خوانند) — پالتِ روزِ Theme.kt، همان `HesabPaySheet`
  Box(Modifier.size(240.dp).clip(RoundedCornerShape(16.dp)).background(LightColors.surface), contentAlignment = Alignment.Center) {
    if (rows != null) QrImage(rows, Modifier.fillMaxSize().padding(8.dp)) else Text("…", fontSize = 48.sp, color = LightColors.textPrimary)
  }
}

/** NFC ِ همین دستگاه: آماده ⇒ «نزدیک کنید»؛ خاموش ⇒ میان‌برِ تنظیمات؛ ندارد (آیفون، کامپیوتر، گوشیِ بی NFC) ⇒ فقط QR */
@Composable
private fun NfcLine(env: HesabPayEnv) {
  var nfc by remember { mutableStateOf(env.nfc()) }
  LaunchedEffect(Unit) { while (true) { delay(2_000); nfc = env.nfc() } }
  when (nfc) {
    PayDevice.Nfc.READY -> V3Message(V3Tone.INFO, StrTillPay.tapNow)
    PayDevice.Nfc.OFF -> V3Message(V3Tone.WARNING, StrPay.nfcOff, action = StrPay.nfcSettings, onAction = { env.openNfcSettings() })
    PayDevice.Nfc.NONE -> V3Message(V3Tone.INFO, StrTillPay.nfcNone)
  }
  Spacer(Modifier.height(6.dp))
}

@Composable
private fun Status(ask: HesabTillModel.Ask, s: HesabTill.State?, stop: HesabPayIntentFlow.Step.Stop?, error: String?) {
  val m = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
  when {
    error != null -> V3Message(V3Tone.ERROR, error, m)
    stop != null -> V3Message(if (stop.retry) V3Tone.WARNING else V3Tone.ERROR, stop.message, m)
    s is HesabTill.State.Confirmed -> V3Message(V3Tone.SUCCESS, StrTillPay.confirmed, m)
    s is HesabTill.State.Review -> V3Message(V3Tone.WARNING, HesabHold.text(s.hold), m)
    s is HesabTill.State.Ended -> V3Message(
      V3Tone.WARNING,
      when (s.why) {
        HesabTill.End.CANCELLED -> StrPay.cancelled
        HesabTill.End.TIMED_OUT -> StrTillPay.timedOut
        HesabTill.End.FAILED -> StrPay.failed
        HesabTill.End.EXPIRED -> StrPay.expired
      },
      m,
    )
    s is HesabTill.State.Cancelling -> V3Message(V3Tone.INFO, StrPay.cancelled, m)
    ask.mode == HesabTill.Mode.AUTO && (s as? HesabTill.State.Pending)?.intent == null -> V3Message(V3Tone.INFO, StrPay.creating, m)
    else -> V3Message(V3Tone.INFO, StrPay.waiting, m)
  }
}
