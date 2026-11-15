package ir.vil3ntec.tohid.ui.v3.account.team

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrBackupSafety
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrLostDevice
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.data.BackupSeal
import ir.vil3ntec.tohid.data.LostDevicePending
import ir.vil3ntec.tohid.data.LostDevicePendingAndroid
import ir.vil3ntec.tohid.data.LostDeviceRestore
import ir.vil3ntec.tohid.data.LostDeviceRestore.Step
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  S44 — برگهٔ «بازیابی از پشتیبان» ِ دستگاهِ غیرفعال، همان برای صفحهٔ عضو (`team/devices/{id}/replay`) و بخشِ «دستگاه‌ها» ِ
 *  کارمندان؛ گوشیِ نسخهٔ ۳ و کامپیوتر یک کد (انتخابِ فایل با `GetContent`، روی کامپیوتر همان پنجرهٔ فایل). گام‌ها و پیام‌ها:
 *  `LostDeviceRestore`. پس از «بازیابی تمام شد» [onDone] (فهرستِ دستگاه‌ها از نو).
 */
@Composable
internal fun LostDeviceReplaySheet(deviceRowId: String, onDismiss: () -> Unit, onDone: () -> Unit = {}) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val c = PhoneV3.colors
  var step by remember { mutableStateOf<Step>(Step.Idle) }
  var file by remember { mutableStateOf<Uri?>(null) }
  var phrase by remember { mutableStateOf("") }

  fun read(uri: Uri, typed: String?) {
    step = Step.Reading
    scope.launch {
      val r = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openInputStream(uri)?.use { LostDevicePendingAndroid.read(context, it, typed) } ?: error(StrLostDevice.errRead) }
      }
      step = r.fold(
        { LostDeviceRestore.afterRead(it) },
        { e ->
          when {
            typed == null && BackupSeal.needsPhrase(e) -> Step.Phrase(wrong = false)
            typed != null && e.message == StrBackupSafety.wrongPhraseFile -> Step.Phrase(wrong = true)
            else -> Step.Failed(StrLostDevice.errRead)
          }
        },
      )
    }
  }

  fun send(p: LostDevicePending.Pending) {
    step = Step.Sending(p)
    scope.launch {
      val r = LostDevicePendingAndroid.replay(context, deviceRowId, p)
      step = r.fold({ Step.Done(it) }, { e -> Step.Failed(LostDeviceRestore.failText((e as? ApiFailure)?.code, e.message)) })
      if (r.isSuccess) onDone()
    }
  }

  val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    uri?.let { file = it; phrase = ""; read(it, null) }
  }
  val choose = { pick.launch("*/*") }

  V3Sheet(onDismiss = onDismiss, title = StrLostDevice.title, dismissible = step !is Step.Sending && step !is Step.Reading) { closer ->
    Text(StrLostDevice.body, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(bottom = 12.dp))
    when (val s = step) {
      Step.Idle -> V3Button(StrLostDevice.pick, choose, Modifier.fillMaxWidth())
      Step.Reading -> V3Button(StrLostDevice.reading, {}, Modifier.fillMaxWidth(), loading = true, enabled = false)
      is Step.Phrase -> {
        V3TextField(
          phrase, { phrase = it }, StrLostDevice.phraseLabel,
          helper = StrLostDevice.phrase, error = if (s.wrong) StrBackupSafety.wrongPhraseFile else null,
        )
        Spacer(Modifier.height(12.dp))
        V3Button(StrLostDevice.open, { file?.let { read(it, phrase) } }, Modifier.fillMaxWidth(), enabled = phrase.isNotBlank())
      }
      is Step.Ready -> {
        V3Message(V3Tone.INFO, StrLostDevice.ready(s.pending.ops.size))
        Spacer(Modifier.height(12.dp))
        V3Button(StrLostDevice.send, { send(s.pending) }, Modifier.fillMaxWidth())
      }
      is Step.Sending -> V3Button(StrLostDevice.sending, {}, Modifier.fillMaxWidth(), loading = true, enabled = false)
      is Step.Done -> {
        V3Message(V3Tone.SUCCESS, StrLostDevice.doneTitle, body = LostDeviceRestore.doneText(s.counts))
        Spacer(Modifier.height(12.dp))
        V3Button(StrKit.close, { closer.close() }, Modifier.fillMaxWidth(), kind = V3ButtonKind.NEUTRAL)
      }
      is Step.Failed -> {
        V3Message(V3Tone.ERROR, s.message)
        Spacer(Modifier.height(12.dp))
        V3Button(StrLostDevice.pick, choose, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY)
      }
    }
  }
}
