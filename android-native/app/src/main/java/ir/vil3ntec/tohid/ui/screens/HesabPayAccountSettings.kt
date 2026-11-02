package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import ir.vil3ntec.tohid.core.gateway.PendingPayee
import ir.vil3ntec.tohid.core.i18n.StrPay
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/*
 *  «حساب‌پیِ دکان» — تنظیمِ صاحبِ دکان (درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۱؛ دستهٔ B ِ `SettingsTiers`، زیرِ «پیشرفته»).
 *  بدنهٔ صفحهٔ نسخهٔ ۳ (`HesabPayAccountScreen` در `ui/v3/settings/sections`).
 *
 *  - رمزِ جدای حساب‌پی (نه رمزِ برنامه): بی آن نه دیدن، نه عوض کردن. سرور می‌سنجدش و پس از ۵ خطا قفل می‌کند؛
 *    هر تغییر در سرور سابقه دارد و به دستگاه‌های صاحب خبر می‌رود.
 *  - «ساده»: شمارهٔ حساب‌پیِ دکان یا QR ِ خودش (اسکن) ⇐ صندوق همان را با مبلغ و کدِ فاکتور نشان می‌دهد؛ تأیید
 *    فقط با «پول رسید» ِ صندوقدار. اعلانِ برنامهٔ حساب‌پی (اختیاری، اندروید) فقط پیشنهاد می‌دهد.
 *  - «خودکار»: کلید و رازِ پذیرندهٔ خودِ دکان — فقط نوشتنی؛ پس از ذخیره فقط چهار نویسهٔ آخر دیده می‌شود.
 *  - نگهبانِ حسابِ گیرنده (سرور: `lib/hesabpay-guard.js`): نامِ صاحبِ حساب اجباری؛ نخستین حساب فوری، هر حسابِ بعدی
 *    ۲۴ ساعت «در انتظار» با کارتِ «این من نبودم — لغو کن» ([HpPendingCard]، همان نوارِ پوسته `HesabPayChangeNotice`)؛
 *    تغییر فقط از دستگاهِ مطمئن + رمزِ حساب‌پی + تأییدِ دوبارهٔ ورود (پنجرهٔ `ReauthHost` خودش باز می‌شود).
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
  var holder by remember { mutableStateOf("") }
  var askReset by remember { mutableStateOf(false) }
  var askRemove by remember { mutableStateOf(false) }
  var scanning by remember { mutableStateOf(false) }
  var hintOn by remember { mutableStateOf(deps.hintOn()) }

  fun show(v: HesabAccountView?) {
    view = v
    onView(v)
    //  نوارِ پوسته («این من نبودم») همان حال را بی پرسشِ دوباره ببیند
    if (v != null) ir.vil3ntec.tohid.ui.v3.shell.HesabPayChangeWatch.seen(v.pending)
    if (v != null && v.opened) {
      mode = if (v.mode == HesabPayShop.AUTO) HesabPayShop.AUTO else HesabPayShop.SIMPLE
      account = v.account
      qr = v.qrPayload
      if (holder.isBlank()) holder = v.payee?.name.orEmpty()
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
        v.payee?.let { Text(StrPay.payeeLine(it.name.ifBlank { "—" }, it.last4), color = c.text) }
        HpNote("پولِ حساب‌پی مستقیم به حسابِ خودِ همین دکان می‌رود؛ هیچ حسابِ دیگری جایش نمی‌نشیند.")
        v.pending?.let { p ->
          HpPendingCard(p, env.now(), busy) {
            act({ env.account.cancelPending() }) { show(it); env.forget(); toast(StrPay.changeCancelled) }
          }
        }
        v.sectionLockedUntil?.let {
          HpNote("پس از «این من نبودم»، بخشِ حساب‌پی تا ${hpMinutesLeft(it, env.now())} دقیقهٔ دیگر بسته است؛ پول به همان حسابِ قبلیِ دکان می‌رود. پس از آن رمزِ حساب‌پی و رمزِ برنامه را عوض کنید.")
        }
        if (v.deviceTrusted == false) {
          HpNote("این ورود هنوز برای عوض کردنِ حساب‌پی مطمئن نیست (دست‌کم ${v.trustDays} روز پس از ورود روی همین دستگاه، بی بیرون رفتن). دیدن شدنی است؛ برای تغییر از گوشی یا کمپیوترِ همیشگیِ خودتان بکوشید.")
        }
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
            holder = holder, onHolder = { holder = it },
            onScan = { scanning = true },
            onSave = {
              val saved: (HesabAccountView) -> Unit = {
                show(it); env.forget(); apiKey = ""; secret = ""
                toast(if (it.pending != null) "حسابِ تازه ۲۴ ساعتِ دیگر فعال می‌شود؛ تا آن دم پول به همان حسابِ قبلی می‌رود." else "حساب‌پیِ دکان ذخیره شد.")
              }
              if (holder.isBlank()) {
                toast("نامِ صاحبِ حساب را بنویسید (همان که در صندوق کنارِ QR دیده می‌شود).")
              } else if (mode == HesabPayShop.SIMPLE) {
                val num = account.trim()
                if (num.isNotEmpty() && HesabPayShop.normalizeAccount(num) == null) {
                  toast("شمارهٔ حساب‌پی درست نیست (مثلِ 0799123456).")
                } else if (num.isEmpty() && qr.isBlank()) {
                  toast("شمارهٔ حساب‌پیِ دکان یا QR ِ آن را بدهید.")
                } else {
                  act({ env.account.saveSimple(pin, holder, num, qr) }, saved)
                }
              } else if (merchant.isBlank()) {
                toast("شناسهٔ پذیرندهٔ حساب‌پیِ دکان را بنویسید.")
              } else {
                act({ env.account.saveAuto(pin, holder, apiKey, secret, merchant) }) { saved(it); merchant = "" }
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
    else if (parsed.account.isEmpty()) toast("در این QR شمارهٔ حساب‌پی نیست؛ شمارهٔ حساب را بنویسید.")
    else if (account.isNotBlank() && HesabPayShop.normalizeAccount(account) != parsed.account) toast("شمارهٔ درونِ این QR با شمارهٔ حساب یکی نیست.")
    else {
      qr = parsed.payload
      account = parsed.account
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
  holder: String,
  onHolder: (String) -> Unit,
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
  OutlinedTextField(
    value = holder, onValueChange = { onHolder(it.take(60)) }, label = { Text("نامِ صاحبِ حساب (در صندوق کنارِ QR دیده می‌شود)") },
    singleLine = true, modifier = Modifier.fillMaxWidth(),
  )
  if (v.connected) HpNote("حسابِ تازه ۲۴ ساعت پس از ذخیره فعال می‌شود و تا آن دم پول به همین حسابِ امروز می‌رود؛ به همهٔ دستگاه‌های صاحب خبر می‌رود.")
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
      value = merchant, onValueChange = onMerchant, label = { Text("شناسهٔ پذیرنده (لازم؛ از داشبوردِ حساب‌پی)") },
      singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
  }
  Button(onClick = onSave, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("ذخیره") }
  if (v.connected) OutlinedButton(onClick = onRemove, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("برداشتنِ حساب‌پیِ دکان") }
}

/** «حسابِ گیرنده در حالِ عوض شدن است» + «این من نبودم — لغو کن» (یک لمس؛ سرور تأییدِ دوبارهٔ ورود می‌خواهد) */
@Composable
internal fun HpPendingCard(p: PendingPayee, now: Long, busy: Boolean, onCancel: () -> Unit) {
  val c = Shop.colors
  Surface(color = c.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(StrPay.changeNotice(p.name.ifBlank { "—" }, p.last4), color = c.warning, fontWeight = FontWeight.Bold)
      HpNote("حدودِ ${maxOf(1L, hpMinutesLeft(p.until, now) / 60)} ساعتِ دیگر فعال می‌شود. ${StrPay.changeNoticeDetail}")
      Button(onClick = onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(StrPay.notMe) }
    }
  }
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
