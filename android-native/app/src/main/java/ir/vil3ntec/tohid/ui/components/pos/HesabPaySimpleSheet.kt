package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.vil3ntec.tohid.core.gateway.HesabPayHint
import ir.vil3ntec.tohid.core.gateway.HesabPayShop
import ir.vil3ntec.tohid.core.gateway.Payee
import ir.vil3ntec.tohid.core.gateway.SimpleAccount
import ir.vil3ntec.tohid.core.i18n.StrPay
import ir.vil3ntec.tohid.core.pay.Settlement
import ir.vil3ntec.tohid.core.qr.QrCode
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.pay.CustomerPay
import ir.vil3ntec.tohid.pay.HesabPayHints
import ir.vil3ntec.tohid.pay.HesabPaySettle
import ir.vil3ntec.tohid.pay.platform.PayDevice
import ir.vil3ntec.tohid.ui.ProvideLang
import ir.vil3ntec.tohid.ui.theme.LightColors
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import kotlinx.coroutines.delay

/*
 *  «حساب‌پیِ دکان» در صندوق — حالتِ ساده (درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۱).
 *
 *  - دکمه فقط وقتی سرور برای همین دکان حالتِ «ساده» با شماره/QR ِ خودِ دکان گفته ([SimpleAccount])؛ پول مستقیم
 *    به حسابِ همان دکان می‌رود و سرور هیچ پولی نمی‌بیند.
 *  - برگه: QR ِ خودِ دکان (یا شماره)، مبلغِ درشت و کدِ یکتای همین فاکتور ([HesabPayShop.invoiceRef]) برای یادداشتِ
 *    پرداخت‌کننده. TODO(hesabpay-sandbox): اگر QR ِ حساب‌پی مبلغ/یادداشت بپذیرد، `HesabPayShop.qrWithAmount`.
 *  - ⛔ تأیید فقط با «پول رسید» ِ صندوقدار ⇐ `HesabPaySettle.settleSimple` (`verification = cashier`). اعلانِ برنامهٔ
 *    حساب‌پی (اختیاری، اندروید) فقط **پیشنهاد** نشان می‌دهد ([HesabPayHint.suggest]) و هرگز خودش نمی‌بندد.
 *  - ⛔ QR فقط از حسابِ ذخیره‌شده در سرور (`/shop/payments/config`)، هرگز عکس یا متنِ واردشده در صندوق؛ کنارش
 *    «پول به حسابِ <نام> — <۴ رقم>» ([PayeeLine]) و اثرِ انگشتِ همان حساب روی ردیفِ پرداخت (`payee`).
 */

/** «پول به حسابِ <نام> — <۴ رقم>» کنارِ QR ِ صندوق (حسابِ فعالِ سرور) — صندوقدار و مشتری گیرنده را می‌بینند */
@Composable
internal fun PayeeLine(p: Payee, modifier: Modifier = Modifier) {
  Text(
    StrPay.payeeLine(p.name.ifBlank { "—" }, p.last4),
    modifier = modifier.padding(top = 8.dp), color = Shop.colors.text, fontWeight = FontWeight.SemiBold,
    textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium,
  )
}

/** دکمهٔ «حساب‌پی (حسابِ دکان)» و برگه‌اش. `draft` = پنلِ ترکیبی (باقی را می‌پردازد)، `null` = کلِ فاکتور */
@Composable
internal fun HesabPaySimpleEntry(
  hook: GatewayHook,
  simple: SimpleAccount,
  payee: Payee?,
  total: Double,
  draft: PayFlow.Draft?,
  enabled: Boolean,
  busy: Boolean,
  onPaid: (Settlement) -> Unit,
  modifier: Modifier = Modifier,
) {
  val saleKey = hook.saleKey ?: return
  val due = HesabPaySettle.due(total, draft)
  if (due !is HesabPaySettle.Due.Amount) return
  var open by remember(saleKey) { mutableStateOf(false) }
  Column(modifier) {
    OutlinedButton(
      onClick = { if (hook.ready()) open = true },
      enabled = enabled && !busy,
      modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
    ) { Text(StrPay.payToShop) }
    if (draft != null) {
      Text(StrPay.coversRest(amountText(due.minor)), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
  }
  if (open) {
    HesabPaySimpleSheet(
      saleKey = saleKey, simple = simple, payee = payee, amountMinor = due.minor, total = total, draft = draft, busy = busy,
      precheck = hook.precheck, now = hook.env::now, storeName = hook.env::storeName, onPaid = onPaid, onClose = { open = false },
    )
  }
}

@Composable
private fun HesabPaySimpleSheet(
  saleKey: String,
  simple: SimpleAccount,
  payee: Payee?,
  amountMinor: Long,
  total: Double,
  draft: PayFlow.Draft?,
  busy: Boolean,
  precheck: () -> String?,
  now: () -> Long,
  storeName: () -> String,
  onPaid: (Settlement) -> Unit,
  onClose: () -> Unit,
) {
  val c = Shop.colors
  val ref = remember(saleKey) { HesabPayShop.invoiceRef(saleKey) }
  val openedAt = remember { now() }
  var clock by remember { mutableLongStateOf(openedAt) }
  LaunchedEffect(Unit) { while (true) { delay(5_000); clock = now() } }
  val hint by HesabPayHints.hint.collectAsState()
  val suggestion = HesabPayHint.suggest(hint, amountMinor, ref, openedAt, maxOf(clock, hint?.at ?: 0L))
  var error by remember { mutableStateOf<String?>(null) }
  //  سنجشِ تازهٔ صندوق (موجودی، تکرار) همان لحظهٔ «پول رسید»، نه هنگامِ باز شدن
  val check by rememberUpdatedState(precheck)
  val qrText = remember(simple, amountMinor, ref) { HesabPayShop.qrWithAmount(simple, amountMinor, ref) }
  val rows = remember(qrText) {
    if (qrText.isBlank()) null else runCatching { QrCode.encode(qrText, QrCode.LARGE_MAX_VERSION) }.getOrNull()
  }

  fun confirm() {
    check()?.let { error = it; return }
    when (val s = HesabPaySettle.settleSimple(total, draft, ref, payee?.fp)) {
      is PayFlow.Outcome.Ok -> { HesabPayHints.consume(); onPaid(s.value) }
      is PayFlow.Outcome.Bad -> error = StrPay.amountMismatch
    }
  }

  //  نمایشگرِ رو به مشتری (HW-10): همان QR و مبلغ
  LaunchedEffect(qrText, amountMinor) { CustomerPay.view.value = CustomerPay.View(amountMinor, qrText, CustomerPay.Stage.WAITING) }
  DisposableEffect(Unit) { onDispose { CustomerPay.view.value = null } }
  val shop = remember { storeName() }
  PayDevice.CustomerScreen(active = true) {
    TohidTheme { ProvideLang { CustomerPay.view.value?.let { CustomerPayScreen(shop, it) } } }
  }

  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
    Surface(color = c.bg, modifier = Modifier.fillMaxSize()) {
      Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
          Text(StrPay.shopHesabTitle, style = MaterialTheme.typography.titleLarge, color = c.text, fontWeight = FontWeight.Bold)
          Spacer(Modifier.height(4.dp))
          Text("${amountText(amountMinor)} ؋", fontSize = 44.sp, color = c.primary, fontWeight = FontWeight.Bold)
          Spacer(Modifier.height(12.dp))
          //  کادرِ QR همیشه روشن (QR ِ وارونه را بیشترِ دوربین‌ها نمی‌خوانند) — همان پالتِ روزِ `HesabPaySheet`
          Box(
            Modifier.size(260.dp).clip(RoundedCornerShape(16.dp)).background(LightColors.surface),
            contentAlignment = Alignment.Center,
          ) {
            if (rows != null) QrImage(rows, Modifier.fillMaxSize().padding(8.dp))
            else Text(simple.account, fontSize = 28.sp, color = LightColors.textPrimary)
          }
          payee?.let { PayeeLine(it) }
          Spacer(Modifier.height(12.dp))
          Text("${StrPay.invoiceCode}: $ref", style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
          Spacer(Modifier.height(8.dp))
          Text(
            StrPay.simpleSteps(simple.account.ifBlank { "—" }, ref),
            color = c.text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge,
          )
          suggestion?.let { sg ->
            Spacer(Modifier.height(12.dp))
            Surface(color = c.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
              Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(StrPay.hintSuggest(amountText(sg.amountMinor), ref), color = c.success, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                if (!sg.refSeen) Text(StrPay.hintNoRef, color = c.warning, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
              }
            }
          }
          Spacer(Modifier.height(12.dp))
          Text(StrPay.confirmOnlyWhenSeen, color = c.muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
          error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = c.danger, textAlign = TextAlign.Center)
          }
          Spacer(Modifier.height(14.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f).height(52.dp)) { Text(StrPay.cancel) }
            Button(onClick = ::confirm, enabled = !busy, modifier = Modifier.weight(1f).height(52.dp)) { Text(StrPay.moneyArrived) }
          }
        }
      }
    }
  }
}
