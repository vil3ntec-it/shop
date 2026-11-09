package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.backup.RecoveryPhraseFlow
import ir.vil3ntec.tohid.core.i18n.StrBackupSafety
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  OPS-02 — برگهٔ «عبارتِ بازیابی»: چرا ⇐ عبارت (یک بار، درشت، چپ‌به‌راست) با «چاپ» ⇐ نوشتنِ دوباره ⇐ روشن. همهٔ
 *  منطق در `RecoveryPhraseFlow` (هسته، آزموده) و کارها در `BackupController`؛ این فقط کشیدن است. گوشیِ نسخهٔ ۳ و
 *  کامپیوتر همین یک کد. بستنِ برگه پیش از پایان ⇒ عبارت دور ریخته و هیچ چیزی روشن نمی‌شود.
 */
@Composable
internal fun RecoveryPhraseSheet(ctrl: BackupController) {
  val flow = ctrl.phrase ?: return
  val c = PhoneV3.colors
  var typed by remember(flow) { mutableStateOf("") }
  V3Sheet(onDismiss = { if (!ctrl.making) ctrl.cancelPhrase() }, title = StrBackupSafety.phraseTitle, dismissible = !ctrl.making) { closer ->
    //  عبارت روی صفحه (نشان دادن و نوشتنِ دوباره) ⇒ بی عکسِ صفحه و ضبط (اندروید `FLAG_SECURE`)؛ «چرا» و بستن ⇒ برداشته
    ir.vil3ntec.tohid.ui.Platform.SecureWindow(RecoveryPhraseFlow.Step.secret(ctrl.phraseStep))
    when (ctrl.phraseStep) {
      RecoveryPhraseFlow.Step.INTRO -> {
        Text(StrBackupSafety.introBody, style = V3Type.callout, color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          V3Button(StrKit.cancel, { closer.close() }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
          V3Button(StrBackupSafety.showIt, ctrl::showPhrase, Modifier.weight(1f))
        }
      }
      RecoveryPhraseFlow.Step.SHOW -> {
        Text(StrBackupSafety.writeItDown, style = V3Type.callout, color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        V3Card {
          //  عبارت چپ‌به‌راست، هر دسته جدا — در صفحهٔ راست‌به‌چپ ترتیبِ دسته‌ها برعکس دیده نشود
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
              flow.groups.forEach { g -> Text(g, style = V3Type.headline.copy(fontFamily = FontFamily.Monospace), color = c.ink) }
            }
          }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          V3Button(StrBackupSafety.printIt, ctrl::printPhrase, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL, icon = Icons.Outlined.Print)
          V3Button(StrBackupSafety.wroteIt, ctrl::phraseWritten, Modifier.weight(1f))
        }
      }
      //  DONE: همان گامِ تأیید تا ساختنِ کلید تمام شود (برگه پس از آن بسته می‌شود)
      RecoveryPhraseFlow.Step.CONFIRM, RecoveryPhraseFlow.Step.DONE -> {
        Text(StrBackupSafety.confirmBody, style = V3Type.callout, color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        V3TextField(typed, { typed = it; ctrl.clearPhraseError() }, StrBackupSafety.confirmLabel, Modifier.fillMaxWidth(), error = ctrl.phraseError, enabled = !ctrl.making)
        if (ctrl.making) {
          Spacer(Modifier.height(10.dp))
          V3Message(V3Tone.INFO, StrBackupSafety.making)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          V3Button(StrBackupSafety.showAgain, ctrl::phraseBack, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL, enabled = !ctrl.making)
          V3Button(StrBackupSafety.confirmDo, { ctrl.confirmPhrase(typed) }, Modifier.weight(1f), enabled = typed.isNotBlank() && !ctrl.making, loading = ctrl.making)
        }
      }
      RecoveryPhraseFlow.Step.CANCELLED -> Unit
    }
  }
}

/** نسخهٔ رمزدار با عبارت که کلیدش روی این دستگاه نیست — عبارت را بپرس */
@Composable
internal fun SnapshotUnlockSheet(ctrl: BackupController) {
  val snap = ctrl.unlock ?: return
  val c = PhoneV3.colors
  var typed by remember(snap) { mutableStateOf("") }
  V3Sheet(onDismiss = ctrl::cancelUnlock, title = StrBackupSafety.unlockTitle) { closer ->
    //  عبارتی که برای باز کردن نوشته می‌شود هم
    ir.vil3ntec.tohid.ui.Platform.SecureWindow(true)
    Text(StrBackupSafety.unlockBody, style = V3Type.callout, color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(14.dp))
    V3TextField(typed, { typed = it }, StrBackupSafety.confirmLabel, Modifier.fillMaxWidth(), error = ctrl.unlockError)
    Spacer(Modifier.height(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button(StrKit.cancel, { closer.close() }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
      V3Button(StrBackupSafety.unlockDo, { ctrl.readSnapshot(snap, typed) }, Modifier.weight(1f), enabled = typed.isNotBlank())
    }
  }
}
