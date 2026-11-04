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
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.i18n.StrCourierApp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrPay
import ir.vil3ntec.tohid.core.i18n.StrPayPages
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.core.i18n.StrSettings
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
  !v.connected -> StrCourierApp.notConnected
  v.mode == HesabPayShop.SIMPLE -> StrPayPages.connectedSimple
  else -> StrPayPages.connectedAuto
}

/** پیامِ ردِ کارگزار: پیامِ خودِ سرور، و برای سنجشِ پیش از فرستادن متنِ همین‌جا */
internal fun hesabPayRefusal(r: AccountCall.Refused): String = r.message.ifBlank {
  when (r.code) {
    "hesabpay_pin_format" -> StrPayPages.pinFormat
    "hesabpay_pin_weak" -> StrPayPages.pinWeak
    "owner_only" -> StrPayPages.ownerOnly
    else -> StrPayPages.notDoneRetry
  }
}

private val HP_OFFLINE: String get() = StrPayPages.offline

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
      !ready -> HpNote(StrPayPages.ownerOnline)
      offline -> HpNote(HP_OFFLINE)
      v == null -> HpNote(StrReports.loading)
      else -> {
        Text(hesabPayStatusLine(v), fontWeight = FontWeight.SemiBold, color = if (v.connected) c.success else c.muted)
        v.payee?.let { Text(StrPay.payeeLine(it.name.ifBlank { "—" }, it.last4), color = c.text) }
        HpNote(StrPayPages.moneyDirect)
        v.pending?.let { p ->
          HpPendingCard(p, env.now(), busy) {
            act({ env.account.cancelPending() }) { show(it); env.forget(); toast(StrPay.changeCancelled) }
          }
        }
        v.sectionLockedUntil?.let {
          HpNote(StrPayPages.notMeLock(hpMinutesLeft(it, env.now())))
        }
        if (v.deviceTrusted == false) {
          HpNote(StrPayPages.notTrusted(v.trustDays))
        }
        when {
          !v.pinSet -> {
            HpNote(StrPayPages.setPinNote)
            v.holdUntil?.let { HpNote(StrPayPages.resetHold(hpMinutesLeft(it, env.now()))) }
            HpPinField(newPin, { newPin = it }, StrPayPages.newPinField)
            HpPinField(newPin2, { newPin2 = it }, StrPayPages.repeatPin)
            Button(
              onClick = {
                if (newPin != newPin2) toast(StrPayPages.pinsDiffer)
                else act({ env.account.setPin(null, newPin) }) { show(it); newPin = ""; newPin2 = ""; toast(StrPayPages.pinSet) }
              },
              enabled = !busy && newPin.isNotBlank(),
              modifier = Modifier.fillMaxWidth(),
            ) { Text(StrPayPages.setPin) }
          }
          v.locked -> {
            HpNote(StrPayPages.pinLocked(hpMinutesLeft(v.lockedUntil ?: 0L, env.now())))
            TextButton(onClick = { askReset = true }, enabled = !busy) { Text(StrPayPages.forgotPin) }
          }
          !v.opened -> {
            HpPinField(pin, { pin = it }, StrPayPages.pinField)
            Button(
              onClick = { act({ env.account.open(pin) }) { show(it) } },
              enabled = !busy && pin.isNotBlank(),
              modifier = Modifier.fillMaxWidth(),
            ) { Text(StrSettings.open) }
            TextButton(onClick = { askReset = true }, enabled = !busy) { Text(StrPayPages.forgotPin) }
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
                toast(if (it.pending != null) StrPayPages.newAccountIn24h else StrPayPages.accountSaved)
              }
              if (holder.isBlank()) {
                toast(StrPayPages.holderNeeded)
              } else if (mode == HesabPayShop.SIMPLE) {
                val num = account.trim()
                if (num.isNotEmpty() && HesabPayShop.normalizeAccount(num) == null) {
                  toast(StrPayPages.accountBad)
                } else if (num.isEmpty() && qr.isBlank()) {
                  toast(StrPayPages.accountOrQr)
                } else {
                  act({ env.account.saveSimple(pin, holder, num, qr) }, saved)
                }
              } else if (merchant.isBlank()) {
                toast(StrPayPages.merchantNeeded)
              } else {
                act({ env.account.saveAuto(pin, holder, apiKey, secret, merchant) }) { saved(it); merchant = "" }
              }
            },
            onRemove = { askRemove = true },
          )
        }
        if (v.opened) {
          if (changingPin) {
            HpPinField(newPin, { newPin = it }, StrPayPages.newPinShort)
            HpPinField(newPin2, { newPin2 = it }, StrSettings.repeatPassword)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(onClick = { changingPin = false; newPin = ""; newPin2 = "" }, modifier = Modifier.weight(1f)) { Text(StrPos.nevermind) }
              Button(
                onClick = {
                  if (newPin != newPin2) toast(StrPayPages.pinsDiffer)
                  else act({ env.account.setPin(pin, newPin) }) {
                    show(v); pin = newPin; newPin = ""; newPin2 = ""; changingPin = false; toast(StrPayPages.pinChanged)
                  }
                },
                enabled = !busy && newPin.isNotBlank(),
                modifier = Modifier.weight(1f),
              ) { Text(StrPayPages.changePin) }
            }
          } else {
            TextButton(onClick = { changingPin = true }, enabled = !busy) { Text(StrPayPages.changeHpPin) }
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
      title = { Text(StrPayPages.resetPinTitle) },
      text = { Text(StrPayPages.resetPinBody) },
      confirmButton = {
        TextButton(onClick = {
          askReset = false
          act({ env.account.resetPin() }) { show(it); pin = ""; env.forget(); toast(StrPayPages.pinReset) }
        }) { Text(StrPos.clearIt) }
      },
      dismissButton = { TextButton(onClick = { askReset = false }) { Text(StrPos.nevermind) } },
    )
  }
  if (askRemove) {
    AlertDialog(
      onDismissRequest = { askRemove = false },
      title = { Text(StrPayPages.removeAccount) },
      text = { Text(StrPayPages.removeAccountBody) },
      confirmButton = {
        TextButton(onClick = {
          askRemove = false
          act({ env.account.remove(pin) }) { show(it); account = ""; qr = ""; env.forget(); toast(StrPayPages.accountRemoved) }
        }) { Text(StrAccount.removeConfirm) }
      },
      dismissButton = { TextButton(onClick = { askRemove = false }) { Text(StrPos.nevermind) } },
    )
  }
  if (scanning) HpQrScanDialog(onDismiss = { scanning = false }) { text ->
    scanning = false
    val parsed = HesabPayShop.parseQr(text)
    if (parsed == null) toast(StrPayPages.qrUnreadable)
    else if (parsed.account.isEmpty()) toast(StrPayPages.qrNoAccount)
    else if (account.isNotBlank() && HesabPayShop.normalizeAccount(account) != parsed.account) toast(StrPayPages.qrMismatch)
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
    for ((m, label) in listOf(HesabPayShop.SIMPLE to StrPayPages.simple, HesabPayShop.AUTO to StrSettings.auto)) {
      if (mode == m) Button(onClick = { onMode(m) }, modifier = Modifier.weight(1f)) { Text(label) }
      else OutlinedButton(onClick = { onMode(m) }, modifier = Modifier.weight(1f)) { Text(label) }
    }
  }
  OutlinedTextField(
    value = holder, onValueChange = { onHolder(it.take(60)) }, label = { Text(StrPayPages.holderField) },
    singleLine = true, modifier = Modifier.fillMaxWidth(),
  )
  if (v.connected) HpNote(StrPayPages.newAccountNote)
  if (mode == HesabPayShop.SIMPLE) {
    HpNote(StrPayPages.simpleNote)
    OutlinedTextField(
      value = account, onValueChange = onAccount, label = { Text(StrPayPages.accountField) },
      singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
      value = qr, onValueChange = onQr, label = { Text(StrPayPages.qrField) },
      singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    if (!Platform.DESKTOP) TextButton(onClick = onScan, enabled = !busy) { Text(StrPayPages.scanQr) }
  } else {
    if (!v.autoAvailable) {
      HpNote(
        if (v.autoReason == "server_not_ready") StrPayPages.serverNotReady
        else StrPayPages.autoNotOn,
      )
    }
    HpNote(StrPayPages.autoNote)
    if (v.mode == HesabPayShop.AUTO) {
      HpNote(StrPayPages.currentKey(v.keyLast4, if (v.merchantSet) StrPayPages.merchantSet else ""))
      if (v.webhookUrl.isNotBlank()) HpNote(StrPayPages.webhookNote(v.webhookUrl))
    }
    HpSecretField(apiKey, onApiKey, StrPayPages.apiKey)
    HpSecretField(secret, onSecret, StrPayPages.webhookSecret)
    OutlinedTextField(
      value = merchant, onValueChange = onMerchant, label = { Text(StrPayPages.merchantField) },
      singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
  }
  Button(onClick = onSave, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(StrKit.save) }
  if (v.connected) OutlinedButton(onClick = onRemove, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(StrPayPages.removeAccount) }
}

/** «حسابِ گیرنده در حالِ عوض شدن است» + «این من نبودم — لغو کن» (یک لمس؛ سرور تأییدِ دوبارهٔ ورود می‌خواهد) */
@Composable
internal fun HpPendingCard(p: PendingPayee, now: Long, busy: Boolean, onCancel: () -> Unit) {
  val c = Shop.colors
  Surface(color = c.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(StrPay.changeNotice(p.name.ifBlank { "—" }, p.last4), color = c.warning, fontWeight = FontWeight.Bold)
      HpNote(StrPayPages.activeInHours(maxOf(1L, hpMinutesLeft(p.until, now) / 60), StrPay.changeNoticeDetail))
      Button(onClick = onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(StrPay.notMe) }
    }
  }
}

@Composable
private fun HpHintRow(on: Boolean, granted: Boolean, onToggle: (Boolean) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.weight(1f)) {
      Text(StrPayPages.notifSuggest)
      HpNote(
        StrPayPages.notifNote +
          if (on && !granted) StrPayPages.notifPermission else "",
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
