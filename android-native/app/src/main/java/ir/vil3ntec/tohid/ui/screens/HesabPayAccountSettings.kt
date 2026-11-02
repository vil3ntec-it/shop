package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.gateway.AccountCall
import ir.vil3ntec.tohid.core.gateway.HesabAccountView
import ir.vil3ntec.tohid.core.gateway.HesabPayShop
import ir.vil3ntec.tohid.core.i18n.StrSettingsHelp
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/*
 *  «حساب‌پیِ دکان» — تنظیمِ صاحبِ دکان (درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۱؛ دستهٔ B ِ `SettingsTiers`، زیرِ «پیشرفته»).
 *  یک صفحه برای هر دو رابط: نسخهٔ ۳ (`HesabPayAccountScreen` در `ui/v3/settings/sections`) و رابطِ امروز
 *  (`SettingsLegacy`).
 *
 *  - رمزِ جدای حساب‌پی (نه رمزِ برنامه): بی آن نه دیدن، نه عوض کردن. سرور می‌سنجدش و پس از ۵ خطا قفل می‌کند؛
 *    هر تغییر در سرور سابقه دارد و به دستگاه‌های صاحب خبر می‌رود.
 *  - «ساده»: شمارهٔ حساب‌پیِ دکان یا QR ِ خودش (اسکن) ⇐ صندوق همان را با مبلغ و کدِ فاکتور نشان می‌دهد؛ تأیید
 *    فقط با «پول رسید» ِ صندوقدار. اعلانِ برنامهٔ حساب‌پی (اختیاری، اندروید) فقط پیشنهاد می‌دهد.
 *  - «خودکار»: کلید و رازِ پذیرندهٔ خودِ دکان — فقط نوشتنی؛ پس از ذخیره فقط چهار نویسهٔ آخر دیده می‌شود.
 *  منطق و درها: `core/gateway/HesabPayShop.kt`؛ سرور: `server/src/lib/hesabpay-account.js`.
 */

/** وضعیت در یک خط («وصل است / وصل نیست») — زیرنویسِ ردیف و سرِ صفحه */
fun hesabPayStatusLine(v: HesabAccountView?): String = when {
  v == null -> "…"
  !v.connected -> "وصل نیست"
  v.mode == HesabPayShop.SIMPLE -> "وصل است — ساده"
  else -> "وصل است — خودکار"
}

/** پیامِ ردِ کارگزار: پیامِ خودِ سرور، و برای سنجشِ پیش از فرستادن متنِ همین‌جا */
internal fun hesabPayRefusal(r: AccountCall.Refused): String = r.message.ifBlank {
  when (r.code) {
    "hesabpay_pin_format" -> "رمزِ حساب‌پی باید ۶ رقم باشد."
    "hesabpay_pin_weak" -> "این رمز ساده است (همه یکسان یا پشتِ سرِ هم)؛ رمزِ دیگری بگذارید."
    "owner_only" -> "حساب‌پیِ دکان را فقط صاحبِ دکان می‌بیند و تنظیم می‌کند."
    else -> "انجام نشد؛ دوباره بکوشید."
  }
}

private const val HP_OFFLINE = "سرور در دسترس نیست؛ اینترنت را ببینید و دوباره بکوشید."

/** رابطِ امروز (`SettingsLegacy`): همان صفحه در بخشِ تاشو؛ زیرنویس «وصل است / وصل نیست» */
@Composable
fun HesabPayLegacySection(signedIn: Boolean, toast: (String) -> Unit) {
  val deps = rememberHesabPayDeps()
  var hp by remember { mutableStateOf<HesabAccountView?>(null) }
  SettingsSectionLegacy(
    icon = Icons.Filled.AccountBalanceWallet,
    title = StrSettingsHelp.hesabShopTitle,
    subtitle = if (hp == null) StrSettingsHelp.hesabShopLine else hesabPayStatusLine(hp),
    stateKey = SettingsLayout.HESAB_SHOP,
  ) {
    HesabPayAccountSettings(
      ready = deps.ownerReady(signedIn),
      toast = toast,
      onView = { hp = it },
    )
  }
}

/** `ready` = واردشده، سرورِ آماده و نقشِ همین دستگاه صاحبِ دکان */
@Composable
fun HesabPayAccountSettings(ready: Boolean, toast: (String) -> Unit, onView: (HesabAccountView?) -> Unit = {}) {
  val deps = rememberHesabPayDeps()
  val env = deps.env
  val scope = rememberCoroutineScope()
  val c = Shop.colors
  var view by remember { mutableStateOf<HesabAccountView?>(null) }
  var offline by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var pin by remember { mutableStateOf("") }
  var newPin by remember { mutableStateOf("") }
  var newPin2 by remember { mutableStateOf("") }
  var changingPin by remember { mutableStateOf(false) }
  var mode by remember { mutableStateOf(HesabPayShop.SIMPLE) }
  var account by remember { mutableStateOf("") }
  var qr by remember { mutableStateOf("") }
  var apiKey by remember { mutableStateOf("") }
  var secret by remember { mutableStateOf("") }
  var merchant by remember { mutableStateOf("") }
  var askReset by remember { mutableStateOf(false) }
  var askRemove by remember { mutableStateOf(false) }
  var scanning by remember { mutableStateOf(false) }
  var hintOn by remember { mutableStateOf(deps.hintOn()) }

  fun show(v: HesabAccountView?) {
    view = v
    onView(v)
    if (v != null && v.opened) {
      mode = if (v.mode == HesabPayShop.AUTO) HesabPayShop.AUTO else HesabPayShop.SIMPLE
      account = v.account
      qr = v.qrPayload
    }
  }

  fun act(block: suspend () -> AccountCall, then: (HesabAccountView) -> Unit = { show(it) }) {
    if (busy) return
    busy = true
    scope.launch {
      try {
        when (val r = block()) {
          is AccountCall.Ok -> then(r.view)
          is AccountCall.Refused -> {
            toast(hesabPayRefusal(r))
            //  قفل یا بازنشانی ⇐ حالِ تازه از سرور (رمزِ واردشده دیگر به کار نمی‌آید)
            if (r.code == "hesabpay_pin_locked") (env.account.status() as? AccountCall.Ok)?.let { show(it.view) }
          }
          is AccountCall.Offline -> toast(HP_OFFLINE)
        }
      } finally {
        busy = false
      }
    }
  }

  LaunchedEffect(ready) {
    if (!ready) return@LaunchedEffect
    when (val r = env.account.status()) {
      is AccountCall.Ok -> { show(r.view); offline = false }
      else -> offline = true
    }
  }

  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    val v = view
    when {
      !ready -> HpNote("فقط صاحبِ دکان، واردشده و با اینترنت، این بخش را می‌بیند.")
      offline -> HpNote(HP_OFFLINE)
      v == null -> HpNote("در حالِ خواندن…")
      else -> {
        Text(hesabPayStatusLine(v), fontWeight = FontWeight.SemiBold, color = if (v.connected) c.success else c.muted)
        HpNote("پولِ حساب‌پی مستقیم به حسابِ خودِ همین دکان می‌رود؛ هیچ حسابِ دیگری جایش نمی‌نشیند.")
        when {
          !v.pinSet -> {
            HpNote("برای این بخش یک رمزِ ۶ رقمیِ جدا بگذارید (غیر از رمزِ برنامه). بی این رمز هیچ‌کس حسابِ دکان را نمی‌بیند یا عوض نمی‌کند.")
            v.holdUntil?.let { HpNote("پس از بازنشانیِ رمز، تا ${hpMinutesLeft(it, env.now())} دقیقهٔ دیگر حسابِ تازه پذیرفته نمی‌شود.") }
            HpPinField(newPin, { newPin = it }, "رمزِ تازهٔ حساب‌پی (۶ رقم)")
            HpPinField(newPin2, { newPin2 = it }, "تکرارِ رمز")
            Button(
              onClick = {
                if (newPin != newPin2) toast("دو رمز یکی نیستند.")
                else act({ env.account.setPin(null, newPin) }) { show(it); newPin = ""; newPin2 = ""; toast("رمزِ حساب‌پی گذاشته شد.") }
              },
              enabled = !busy && newPin.isNotBlank(),
              modifier = Modifier.fillMaxWidth(),
            ) { Text("گذاشتنِ رمز") }
          }
          v.locked -> {
            HpNote("رمزِ حساب‌پی چند بار اشتباه زده شد؛ حدودِ ${hpMinutesLeft(v.lockedUntil ?: 0L, env.now())} دقیقهٔ دیگر دوباره بکوشید.")
            TextButton(onClick = { askReset = true }, enabled = !busy) { Text("رمز را فراموش کرده‌ام") }
          }
          !v.opened -> {
            HpPinField(pin, { pin = it }, "رمزِ حساب‌پی")
            Button(
              onClick = { act({ env.account.open(pin) }) { show(it) } },
              enabled = !busy && pin.isNotBlank(),
              modifier = Modifier.fillMaxWidth(),
            ) { Text("باز کردن") }
            TextButton(onClick = { askReset = true }, enabled = !busy) { Text("رمز را فراموش کرده‌ام") }
          }
          else -> HpOpened(
            v = v, busy = busy, mode = mode, onMode = { mode = it },
            account = account, onAccount = { account = it }, qr = qr, onQr = { qr = it },
            apiKey = apiKey, onApiKey = { apiKey = it }, secret = secret, onSecret = { secret = it },
            merchant = merchant, onMerchant = { merchant = it },
            onScan = { scanning = true },
            onSave = {
              if (mode == HesabPayShop.SIMPLE) {
                val num = account.trim()
                if (num.isNotEmpty() && HesabPayShop.normalizeAccount(num) == null) {
                  toast("شمارهٔ حساب‌پی درست نیست (مثلِ 0799123456).")
                } else if (num.isEmpty() && qr.isBlank()) {
                  toast("شمارهٔ حساب‌پیِ دکان یا QR ِ آن را بدهید.")
                } else {
                  act({ env.account.saveSimple(pin, num, qr) }) { show(it); env.forget(); toast("حساب‌پیِ دکان ذخیره شد.") }
                }
              } else {
                act({ env.account.saveAuto(pin, apiKey, secret, merchant) }) {
                  show(it); env.forget(); apiKey = ""; secret = ""; merchant = ""; toast("کلیدهای حساب‌پیِ دکان ذخیره شد.")
                }
              }
            },
            onRemove = { askRemove = true },
          )
        }
        if (v.opened) {
          if (changingPin) {
            HpPinField(newPin, { newPin = it }, "رمزِ تازه (۶ رقم)")
            HpPinField(newPin2, { newPin2 = it }, "تکرارِ رمزِ تازه")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(onClick = { changingPin = false; newPin = ""; newPin2 = "" }, modifier = Modifier.weight(1f)) { Text("بی‌خیال") }
              Button(
                onClick = {
                  if (newPin != newPin2) toast("دو رمز یکی نیستند.")
                  else act({ env.account.setPin(pin, newPin) }) {
                    show(v); pin = newPin; newPin = ""; newPin2 = ""; changingPin = false; toast("رمزِ حساب‌پی عوض شد.")
                  }
                },
                enabled = !busy && newPin.isNotBlank(),
                modifier = Modifier.weight(1f),
              ) { Text("عوض کردنِ رمز") }
            }
          } else {
            TextButton(onClick = { changingPin = true }, enabled = !busy) { Text("عوض کردنِ رمزِ حساب‌پی") }
          }
        }
        if (deps.noticeSupported && v.opened && v.mode == HesabPayShop.SIMPLE) {
          HpHintRow(hintOn, granted = deps.noticeGranted(), onToggle = {
            hintOn = it
            deps.setHintOn(it)
            if (it && !deps.noticeGranted()) deps.openNoticeAccess()
          })
        }
      }
    }
  }

  if (askReset) {
    AlertDialog(
      onDismissRequest = { askReset = false },
      title = { Text("بازنشانیِ رمزِ حساب‌پی") },
      text = { Text("رمز و حسابِ حساب‌پیِ دکان هر دو پاک می‌شوند و تا ۲۴ ساعت حسابِ تازه پذیرفته نمی‌شود. به دستگاه‌های صاحبِ دکان هم خبر می‌رود.") },
      confirmButton = {
        TextButton(onClick = {
          askReset = false
          act({ env.account.resetPin() }) { show(it); pin = ""; env.forget(); toast("رمز و حسابِ حساب‌پی پاک شد.") }
        }) { Text("پاک کن") }
      },
      dismissButton = { TextButton(onClick = { askReset = false }) { Text("بی‌خیال") } },
    )
  }
  if (askRemove) {
    AlertDialog(
      onDismissRequest = { askRemove = false },
      title = { Text("برداشتنِ حساب‌پیِ دکان") },
      text = { Text("دکمهٔ حساب‌پی از صندوق برداشته می‌شود. رمزِ حساب‌پی می‌ماند.") },
      confirmButton = {
        TextButton(onClick = {
          askRemove = false
          act({ env.account.remove(pin) }) { show(it); account = ""; qr = ""; env.forget(); toast("حساب‌پیِ دکان برداشته شد.") }
        }) { Text("بردار") }
      },
      dismissButton = { TextButton(onClick = { askRemove = false }) { Text("بی‌خیال") } },
    )
  }
  if (scanning) HpQrScanDialog(onDismiss = { scanning = false }) { text ->
    scanning = false
    val parsed = HesabPayShop.parseQr(text)
    if (parsed == null) toast("این QR خوانده نشد یا بلند است.")
    else {
      qr = parsed.payload
      if (account.isBlank() && parsed.account.isNotEmpty()) account = parsed.account
    }
  }
}

@Composable
private fun HpOpened(
  v: HesabAccountView,
  busy: Boolean,
  mode: String,
  onMode: (String) -> Unit,
  account: String,
  onAccount: (String) -> Unit,
  qr: String,
  onQr: (String) -> Unit,
  apiKey: String,
  onApiKey: (String) -> Unit,
  secret: String,
  onSecret: (String) -> Unit,
  merchant: String,
  onMerchant: (String) -> Unit,
  onScan: () -> Unit,
  onSave: () -> Unit,
  onRemove: () -> Unit,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    for ((m, label) in listOf(HesabPayShop.SIMPLE to "ساده", HesabPayShop.AUTO to "خودکار")) {
      if (mode == m) Button(onClick = { onMode(m) }, modifier = Modifier.weight(1f)) { Text(label) }
      else OutlinedButton(onClick = { onMode(m) }, modifier = Modifier.weight(1f)) { Text(label) }
    }
  }
  if (mode == HesabPayShop.SIMPLE) {
    HpNote("شماره یا QR ِ حساب‌پیِ خودِ دکان. در صندوق همین QR با مبلغ و کدِ فاکتور نشان داده می‌شود و پول مستقیم به همین حساب می‌رود. صندوقدار فقط وقتی پول را در حساب دید «پول رسید» را می‌زند.")
    OutlinedTextField(
      value = account, onValueChange = onAccount, label = { Text("شمارهٔ حساب‌پیِ دکان (مثلِ 0799123456)") },
      singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
      value = qr, onValueChange = onQr, label = { Text("QR ِ حساب‌پیِ دکان (اختیاری)") },
      singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    if (!Platform.DESKTOP) TextButton(onClick = onScan, enabled = !busy) { Text("اسکنِ QR ِ حساب‌پیِ دکان") }
  } else {
    if (!v.autoAvailable) {
      HpNote(
        if (v.autoReason == "server_not_ready") "سرور هنوز برای حالتِ خودکار آماده نیست؛ از حالتِ ساده استفاده کنید."
        else "حالتِ خودکار برای این دکان هنوز روشن نشده؛ کلیدها ذخیره می‌شوند و با روشن شدن به کار می‌روند.",
      )
    }
    HpNote("کلید و رازِ پذیرندهٔ حساب‌پیِ خودِ دکان. پرداخت فقط وقتی خودکار تأیید می‌شود که حساب‌پی با امضای همین دکان کد و مبلغِ همان فاکتور را بفرستد. کلیدها پس از ذخیره دیگر نشان داده نمی‌شوند.")
    if (v.mode == HesabPayShop.AUTO) {
      HpNote("کلیدِ فعلی: …${v.keyLast4}${if (v.merchantSet) " · شناسهٔ پذیرنده دارد" else ""}")
      if (v.webhookUrl.isNotBlank()) HpNote("این نشانی را در داشبوردِ حساب‌پیِ دکان به‌عنوانِ وب‌هوک ثبت کنید: ${v.webhookUrl}")
    }
    HpSecretField(apiKey, onApiKey, "کلیدِ API")
    HpSecretField(secret, onSecret, "رازِ وب‌هوک")
    OutlinedTextField(
      value = merchant, onValueChange = onMerchant, label = { Text("شناسهٔ پذیرنده (اگر حساب‌پی داده)") },
      singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
  }
  Button(onClick = onSave, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("ذخیره") }
  if (v.connected) OutlinedButton(onClick = onRemove, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("برداشتنِ حساب‌پیِ دکان") }
}

@Composable
private fun HpHintRow(on: Boolean, granted: Boolean, onToggle: (Boolean) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.weight(1f)) {
      Text("پیشنهاد از اعلانِ حساب‌پی (فقط همین دستگاه)")
      HpNote(
        "وقتی اعلانِ «پول رسید» ِ برنامهٔ حساب‌پی آمد، صندوق فقط پیشنهاد می‌دهد؛ تأیید باز هم با «پول رسید» ِ صندوقدار است. پیامک خوانده نمی‌شود." +
          if (on && !granted) " اجازهٔ «دسترسی به اعلان» هنوز داده نشده." else "",
      )
    }
    Switch(checked = on, onCheckedChange = onToggle)
  }
}

@Composable
private fun HpPinField(value: String, onChange: (String) -> Unit, label: String) {
  OutlinedTextField(
    value = value,
    onValueChange = { s -> onChange(HesabPayShop.latin(s).filter { it.isDigit() }.take(6)) },
    label = { Text(label) },
    singleLine = true,
    visualTransformation = PasswordVisualTransformation(),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun HpSecretField(value: String, onChange: (String) -> Unit, label: String) {
  OutlinedTextField(
    value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
    visualTransformation = PasswordVisualTransformation(),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun HpNote(text: String) {
  Text(text, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
}

private fun hpMinutesLeft(until: Long, now: Long): Long = maxOf(1L, (until - now + 59_999L) / 60_000L)
