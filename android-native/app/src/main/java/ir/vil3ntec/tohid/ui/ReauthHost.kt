package ir.vil3ntec.tohid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  میزبانِ «تأییدِ دوبارهٔ هویت» — یک بار در `AppRoot`، پس اندروید و کامپیوتر هر دو.
 *
 *  سرور کارِ حساس (گذاشتنِ رمز برای حسابِ بی‌رمز، افزودنِ شماره، مجوز و نقشِ
 *  کارمندان…) را از نشستِ کهنه با `reauth_required` رد می‌کند. `ApiClient`
 *  آن را به `ReauthGate` می‌دهد، این‌جا پنجره باز می‌شود و پس از تأییدِ سرور
 *  همان درخواست یک بار دوباره می‌رود — صفحه‌ها هیچ کارِ جدایی ندارند.
 *
 *  راهِ اثبات همانی است که سرور می‌پذیرد (`POST /auth/reauth`): رمزِ حساب،
 *  یا برای حسابِ بی‌رمز کدی که به ایمیل (وگرنه شمارهٔ) خودِ حساب می‌رود.
 *  «انصراف» یعنی «انجام نده» و صفحه همان پیامِ فارسیِ سرور را نشان می‌دهد.
 */
@Composable
fun ReauthHost() {
  val context = LocalContext.current
  val gate = remember(context) { Backend.api(context).reauth }
  DisposableEffect(gate) {
    val detach = gate.attach()
    onDispose { detach() }
  }
  val ask by gate.asking.collectAsState()
  ask?.let { open -> key(open) { ReauthDialog(onDone = { ok -> open.done(ok) }) } }
}

private enum class Proof { PASSWORD, CODE }

@Composable
private fun ReauthDialog(onDone: (Boolean) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val auth = remember(context) { Backend.auth(context) }
  val colors = Shop.colors
  //  `null` تا وقتی معلوم نیست حساب رمز دارد یا نه
  var proof by remember { mutableStateOf<Proof?>(null) }
  var destination by remember { mutableStateOf("") }
  var secret by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var note by remember { mutableStateOf<String?>(null) }
  var error by remember { mutableStateOf<String?>(null) }

  //  رمز دارد یا نه — از خودِ سرور (`hasPassword`). نشد ⇒ رمز، با راهِ کد کنارش.
  LaunchedEffect(Unit) {
    val me = Backend.account(context).me().valueOrNull()?.user
    //  همان ترتیبِ سرور: ایمیل، وگرنه شماره
    destination = me?.email?.takeIf { it.isNotBlank() } ?: me?.phone.orEmpty()
    proof = if (me == null || me.hasPassword) Proof.PASSWORD else Proof.CODE
  }

  fun sendCode() {
    busy = true
    error = null
    scope.launch {
      auth.requestReauthCode()
        .onSuccess { note = if (destination.isBlank()) "کد فرستاده شد." else "کد به $destination فرستاده شد." }
        .onFailure { error = it.userMessage }
      busy = false
    }
  }

  //  حسابِ بی‌رمز (یا «کد بفرست»): کد همان لحظه می‌رود — کاربر خودش کارِ حساس را زده
  LaunchedEffect(proof) { if (proof == Proof.CODE) sendCode() }

  fun submit() {
    val mode = proof ?: return
    busy = true
    error = null
    scope.launch {
      val done: ApiResult<Unit> =
        if (mode == Proof.PASSWORD) auth.reauthWithPassword(secret) else auth.reauthWithCode(secret)
      done
        .onSuccess { onDone(true) }
        .onFailure { error = it.userMessage }
      busy = false
    }
  }

  val ready = !busy && proof != null && secret.isNotBlank()

  AlertDialog(
    onDismissRequest = { if (!busy) onDone(false) },
    title = { Text("تأییدِ دوبارهٔ هویت") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "برای امنیتِ حساب، این کار فقط چند دقیقه پس از ورود ممکن است. " +
            if (proof == Proof.CODE) "کدی را که برایتان فرستاده شد بزنید." else "رمزِ حساب را دوباره بزنید.",
          style = MaterialTheme.typography.bodyMedium,
        )
        when (proof) {
          null -> Text("…", color = colors.muted)
          Proof.PASSWORD -> {
            OutlinedTextField(
              value = secret,
              onValueChange = { secret = it },
              singleLine = true,
              enabled = !busy,
              label = { Text("رمزِ حساب") },
              visualTransformation = PasswordVisualTransformation(),
              modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { secret = ""; proof = Proof.CODE }, enabled = !busy) {
              Text("رمز ندارم — کد بفرست")
            }
          }
          Proof.CODE -> {
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.muted) }
            OutlinedTextField(
              value = secret,
              //  رقمِ فارسی ⇒ لاتین: سرور (`otp.verify`) فقط ۰-۹ِ لاتین را نگه می‌دارد
              onValueChange = { secret = ir.vil3ntec.tohid.latinDigits(it).filter { c -> c in '0'..'9' }.take(8) },
              singleLine = true,
              enabled = !busy,
              label = { Text("کدِ تأیید") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
              modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { sendCode() }, enabled = !busy) { Text("فرستادنِ دوبارهٔ کد") }
          }
        }
        error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.danger) }
      }
    },
    confirmButton = {
      TextButton(enabled = ready, onClick = { submit() }) {
        Text("تأیید", color = if (ready) colors.primary else colors.muted2)
      }
    },
    dismissButton = {
      TextButton(onClick = { onDone(false) }, enabled = !busy) { Text("انصراف", color = colors.muted) }
    },
  )
}
