package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrSyncPlain
import ir.vil3ntec.tohid.core.net.userText
import ir.vil3ntec.tohid.data.platform.SupportDiagAndroid
import ir.vil3ntec.tohid.displayMillis
import ir.vil3ntec.tohid.sync.AutoSync
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v1.SyncV1Worker
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.screens.ServerPulse
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsIndexModel
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame
import kotlinx.coroutines.launch

/**
 *  `settings/data/sync`: کارتِ وضعیت (نقطه + «همه‌چیز روی سرور نشسته» / «۳ مورد در
 *  انتظار» / «آخرین تلاش ناموفق بود»)، «همگام‌سازی حالا» (دورِ Sync v1 — ⛔ F0-02، نه
 *  `Syncer.run`)، کارتِ «سرور» (`ServerPulse`) و «دفترِ تغییرات» — همان برگهٔ نقطهٔ
 *  سربرگِ قدیم، حالا صفحهٔ خودش. هشدارهای همگام‌سازیِ خانه همین‌جا را باز می‌کنند و
 *  گره را به زبانِ آدم می‌گوید (`SyncModel.diagnose`: چه شده، چه نگه داشته شده، چه
 *  کنید). صفحه فقط‌خواندنی است و کارمند هم بازش می‌کند؛ کارِ پاک‌کننده‌ای در آن نیست.
 *
 *  ⛔ هیچ جزئیاتِ فنی/زیرساخت (`docs/RULES-fa.md`): نه نشانیِ سرور، نه نسخهٔ سرور/Schema، نه راهِ API، نه متنِ خامِ
 *  خطا — برای هیچ نقشی. همان جزئیات برای پشتیبانی **فرستاده** می‌شود (`SupportDiagAndroid`) و صفحه فقط می‌گوید
 *  «پشتیبانی خبردار شد». نگهبان: `InfraLeakGuardTest`.
 */
@Composable
internal fun RouteScope.SyncScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val signedIn = SettingsShotState.signedIn ?: remember { SyncStore(context).accessToken != null }
  val lastSync = remember { SyncStore(context).lastSyncAt }
  val v1 by SyncV1Engine.of(context).status.collectAsState()
  LaunchedEffect(Unit) { ServerPulse.probe(context) }
  val now = System.currentTimeMillis()
  val lastOk = maxOf(lastSync, AutoSync.lastOk, v1.lastOkAt)
  //  جزئیاتِ فنیِ گره ⇒ فقط پشتیبانی؛ صفحه «خبردار شد» را پس از رسیدنِ گزارش به سرور می‌گوید (عکسِ صفحه چیزی نمی‌فرستد)
  var notified by remember { mutableStateOf(false) }
  val shot = SettingsShotState.signedIn != null || SettingsShotState.syncError != null

  SettingsPageFrame(title = SettingsLayout.labelOf("همگام‌سازی"), onBack = onBack) {
    item(key = "hero") {
      SettingsBlock {
        if (signedIn) {
          val head = SyncModel.headline(AutoSync.health, AutoSync.running, AutoSync.pendingCount + v1.queued)
          StatusLine(head)
          Spacer(Modifier.height(4.dp))
          Text(StrNav.lastSync(SettingsIndexModel.since(lastOk, now)), style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
          if (v1.queued > 0) Text(StrSettings.queueSize(v1.queued), style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
          AutoSync.rejectionNote?.let { ErrorText(it, V3Tone.WARNING) }
        } else {
          StatusLine(SyncModel.Line(StrSettings.notSignedIn, null))
          Spacer(Modifier.height(4.dp))
          Text(StrNav.syncAfterSignIn, style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
        }
        Spacer(Modifier.height(14.dp))
        V3Button(
          StrSettings.syncNow,
          {
            runCatching { SyncV1Worker.reconnect.userAsked(); SyncV1Worker.now(context) }   // SYN-12: دورِ کاربر هرگز درنگِ پس از وصل ندارد
              .onSuccess { Haptics.confirm(context); toasts.show(StrSettings.syncStarted) }
              .onFailure { toasts.show(it.userText(StrSettings.syncFailed)) }
          },
          Modifier.fillMaxWidth(),
          icon = Icons.Outlined.Sync,
          enabled = signedIn,
        )
      }
      SettingsNote(StrNav.changesTravel)
    }
    if (signedIn) {
      item(key = "diagnosis") {
        //  SYN-12: حکمِ موتور فقط برای خطای خودِ موتور (`v1.lastError`)؛ خطای عکسِ صفحه یا `AutoSync` ⇒ همان نشانه‌ها
        val engineError = SettingsShotState.syncError == null && v1.lastError.isNotBlank()
        val d = SyncModel.diagnose(SettingsShotState.syncError ?: v1.lastError.ifBlank { AutoSync.lastError.orEmpty() }, v1.holding, v1.dropped, AutoSync.needsShop, ServerPulse.state, if (engineError) v1.outage else null)
        val support = d.detail ?: if (v1.dropped > 0) "dropped=${v1.dropped}" else null
        LaunchedEffect(d.problem, support) {
          notified = false
          if (support != null && !shot) {
            notified = SupportDiagAndroid.report(context, d.problem.name, support, SyncModel.facts(v1, ServerPulse.version, ServerPulse.prefix))
          }
        }
        if (d.problem != SyncModel.Problem.NONE) DiagnosisCard(d, if (support == null) null else notified)
      }
    }
    item(key = "server") {
      val pulse = SyncModel.server(ServerPulse.state)
      V3Section(StrNav.server) {
        V3ListRow(StrSettings.serverStatus, chevron = false, trailing = { StatusLine(pulse, small = true) })
        if (ServerPulse.at > 0) { V3Divider(start = 22.dp); V3ListRow(StrNav.lastCheck(SettingsIndexModel.since(ServerPulse.at, now)), chevron = false) }
      }
      SyncModel.pulseNote(ServerPulse.state, ServerPulse.note)?.let { ErrorText(it) }
      V3TextButton(
        if (ServerPulse.state == ServerPulse.State.CHECKING) StrNav.checking else StrNav.checkAgain,
        { if (ServerPulse.state != ServerPulse.State.CHECKING) scope.launch { ServerPulse.probe(context, force = true) } },
        Modifier.padding(top = 6.dp),
      )
    }
    if (signedIn) {
      item(key = "log") {
        val q = SyncModel.queue(v1.queued)
        V3Section(StrNav.changeLog) {
          V3ListRow(q.text, chevron = false, trailing = { Dot(q.tone) })
          V3Divider(start = 22.dp)
          V3ListRow(if (v1.lastOkAt > 0) StrNav.lastSyncOk(displayMillis(v1.lastOkAt)) else StrNav.noSyncYet, chevron = false)
          if (v1.dropped > 0) {
            V3Divider(start = 22.dp)
            val told = if (notified) StrSyncPlain.supportNotified else StrSyncPlain.supportPending
            V3ListRow(StrSyncPlain.rejectedTitle(v1.dropped) + " — " + told, chevron = false, trailing = { Dot(V3Tone.WARNING) })
          }
        }
      }
    }
  }
}

/**
 *  گره به زبانِ آدم: چه شده، چه نگه داشته شده، و «چه کنید». ⛔ متنِ خام (`d.detail`) این‌جا نیست — فقط برای پشتیبانی
 *  فرستاده می‌شود؛ [notified]: `null` ⇒ چیزی برای پشتیبانی نیست، `true` ⇒ «پشتیبانی خبردار شد»، `false` ⇒ «فرستاده می‌شود».
 */
@Composable
private fun DiagnosisCard(d: SyncModel.Diagnosis, notified: Boolean?) {
  Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
    V3Message(d.tone, d.title, body = d.body + "\n\n" + StrSettings.whatToDo + ": " + d.action)
    notified?.let { Text(if (it) StrSyncPlain.supportNotified else StrSyncPlain.supportPending, style = V3Type.caption, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp)) }
  }
}

/** نقطهٔ وضعیت + نوشته (رنگ هرگز تنها نیست) */
@Composable
private fun StatusLine(line: SyncModel.Line, small: Boolean = false) {
  val c = PhoneV3.colors
  Row(verticalAlignment = Alignment.CenterVertically) {
    Dot(line.tone, if (small) 8 else 12)
    Spacer(Modifier.width(if (small) 6.dp else 10.dp))
    Text(line.text, style = if (small) V3Type.subhead else V3Type.headline, color = if (line.tone == null) c.inkMuted else c.ink)
  }
}

@Composable
private fun Dot(tone: V3Tone?, size: Int = 8) {
  val c = PhoneV3.colors
  Box(Modifier.size(size.dp).clip(CircleShape).background(if (tone == null) c.chevron else PhoneV3Roles.toneIcon(c, tone)))
}

@Composable
private fun ErrorText(text: String, tone: V3Tone = V3Tone.ERROR) {
  Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
    Text(text, style = V3Type.footnote, color = if (tone == V3Tone.ERROR) PhoneV3Roles.errorText(PhoneV3.colors) else PhoneV3Roles.toneIcon(PhoneV3.colors, tone))
  }
}
