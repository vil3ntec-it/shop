package ir.vil3ntec.tohid.ui.v3.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.Watchman
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.print.PrinterRegistry
import ir.vil3ntec.tohid.sync.AutoSync
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.SyncDot
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Chevron
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Toggle
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.SettingsEntry
import ir.vil3ntec.tohid.ui.v3.settings.sections.rememberLockOn

/** خطِ مویی میانِ ردیف‌ها، از زیرِ متن (آیکنِ ۲۴ + فاصله‌ها) */
internal val ROW_DIVIDER_START = 60.dp

/** هر آنچه ردیف‌ها نشان می‌دهند — از موتورهای امروز، بی منطقِ تازه */
@Composable
internal fun rememberSettingsFacts(theme: ThemeChoice): SettingsFacts {
  val context = LocalContext.current
  val signedIn = remember { SyncStore(context).accessToken != null }
  val lastSync = remember { SyncStore(context).lastSyncAt }
  val v1 by SyncV1Engine.of(context).status.collectAsState()
  var sessions by remember { mutableStateOf<Int?>(null) }
  LaunchedEffect(signedIn) {
    if (signedIn && Backend.isReady(context)) Backend.account(context).devices().onSuccess { sessions = it.size }
  }
  val printer = remember {
    val def = PrinterRegistry.defaultLink(context)
    PrinterRegistry.list(context).firstOrNull { it.link == def }?.let { p -> p.name.ifBlank { PrinterRegistry.label(p.link) } }
  }
  val health = AutoSync.health
  return SettingsFacts(
    debtLimit = remember { Watchman.debtLimit(context) },
    theme = theme,
    lang = LocaleChoice.lang,
    digits = LocaleChoice.digits,
    symbol = LocaleChoice.symbol,
    lockOn = rememberLockOn(),
    signedIn = signedIn,
    sessions = sessions,
    blockExpired = remember { ShopPolicy.blockExpired(context) },
    printer = printer,
    cameraGranted = remember { cameraGranted(context) },
    sync = SettingsIndexModel.syncState(
      signedIn,
      failed = v1.dot == SyncDot.RED || health == AutoSync.Health.FAILED,
      waiting = v1.dot == SyncDot.YELLOW || health == AutoSync.Health.WAITING,
    ),
    lastSyncOk = maxOf(lastSync, v1.lastOkAt),
    now = System.currentTimeMillis(),
    version = BuildConfig.VERSION_NAME,
  )
}

internal fun cameraGranted(context: android.content.Context): Boolean =
  androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
    android.content.pm.PackageManager.PERMISSION_GRANTED

/**
 *  کارتِ یک گروه (r5): سرخط بیرونِ کارت، ردیف‌ها با آیکنِ خطی و مقدارِ خاکستری /
 *  کلید / فلش. زدنِ هر ردیف صفحهٔ خودش را باز می‌کند (`open(path)`). صفحه‌هایی که
 *  ماژول‌های دیگر در همین گروه گذاشته‌اند (`extra`) ته کارت می‌آیند.
 */
@Composable
internal fun SettingsGroupCard(
  group: String,
  facts: SettingsFacts,
  extra: List<SettingsEntry>,
  open: (String) -> Unit,
  onLockToggle: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  showTitle: Boolean = true,
  footer: String? = null,
) {
  val sections = SettingsLayout.GROUPS.firstOrNull { it.title == group }?.sections.orEmpty()
  V3Section(if (showTitle) SettingsLayout.groupLabel(group) else null, modifier, footer = footer) {
    sections.forEachIndexed { i, key ->
      if (i > 0) V3Divider(start = ROW_DIVIDER_START)
      SettingsRow(key, facts, onClick = { open(SettingsPaths.section(key)) }, onLockToggle = onLockToggle)
    }
    extra.forEach { e ->
      V3Divider(start = ROW_DIVIDER_START)
      V3ListRow(e.label(), icon = e.icon, tile = false, onClick = { open(e.route) })
    }
  }
}

/** یک ردیف: مقدارِ تازه با محوِ نرم جای قبلی می‌نشیند */
@Composable
internal fun SettingsRow(key: String, facts: SettingsFacts, onClick: () -> Unit, onLockToggle: (Boolean) -> Unit, selected: Boolean? = null) {
  val label = SettingsLayout.labelOf(key)
  val toggle = SettingsIndexModel.toggle(key, facts)
  val value = SettingsIndexModel.value(key, facts)
  V3ListRow(
    title = label,
    icon = SettingsPages.icon(key),
    tile = false,
    selected = selected,
    trailing = when {
      toggle != null -> ({ V3Toggle(toggle, label, onLockToggle) })
      key == "همگام‌سازی" && value != null -> ({ SyncValue(facts.sync, value) })
      value != null -> ({ RowValue(value) })
      else -> null
    },
    onClick = onClick,
  )
}

@Composable
private fun RowValue(value: String) {
  val c = PhoneV3.colors
  Row(verticalAlignment = Alignment.CenterVertically) {
    AnimatedContent(value, transitionSpec = { fadeIn(MotionSpec.tweenOf(MotionTokens.MEDIUM)) togetherWith fadeOut(MotionSpec.tweenOf(MotionTokens.SHORT)) }, label = "rowValue") {
      Text(it, style = V3Type.subhead, color = c.inkMuted, maxLines = 1)
    }
    Spacer(Modifier.width(4.dp))
    V3Chevron()
  }
}

/** همگام‌سازی: نقطهٔ وضعیت (رنگ + نوشته، نه رنگِ تنها) و «۲ دقیقه پیش» */
@Composable
private fun SyncValue(state: SyncState, value: String) {
  val c = PhoneV3.colors
  val tone = when (state) {
    SyncState.OK -> V3Tone.SUCCESS
    SyncState.WAITING -> V3Tone.WARNING
    SyncState.FAILED -> V3Tone.ERROR
    SyncState.OFF -> null
  }
  Row(verticalAlignment = Alignment.CenterVertically) {
    if (tone != null) {
      Box(Modifier.size(8.dp).clip(CircleShape).background(PhoneV3Roles.toneIcon(c, tone)))
      Spacer(Modifier.width(6.dp))
    }
    RowValue(value)
  }
}
