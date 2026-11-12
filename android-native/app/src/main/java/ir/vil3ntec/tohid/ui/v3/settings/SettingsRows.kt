package ir.vil3ntec.tohid.ui.v3.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
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
import ir.vil3ntec.tohid.ui.screens.SettingsHelp
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
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.nav.SettingsEntry
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.v3.settings.sections.rememberLockOn
import ir.vil3ntec.tohid.ui.v3.settings.sections.rememberServerSections

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
    expiryApproval = remember { ir.vil3ntec.tohid.data.ExpiryPref.approval(context) },
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
    serverOn = rememberServerSections(),
  )
}

internal fun cameraGranted(context: android.content.Context): Boolean =
  androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
    android.content.pm.PackageManager.PERMISSION_GRANTED

/** یک بخشِ کوتاهِ صفحهٔ دسته: ردیف‌های دیدنیِ `SettingsLayout` و ردیف‌هایی که ماژول‌ها همین‌جا گذاشته‌اند */
internal class CategoryPart(val id: String, val rows: List<String>, val extra: List<SettingsEntry>)

/**
 *  بخش‌های دیدنیِ یک دسته (نسخهٔ دوم، ۱۴۰۵/۰۸/۱۱) — گوشی، تبلت و کامپیوتر همین را می‌کشند: ردیف فقط با
 *  `SettingsLayout.visible` (نه A، نه آزمایشی، نه قابلیتِ خاموش، نه پشتِ پرچمِ خاموشِ سرور، نه بخشِ سکوی دیگر)؛
 *  ردیفِ ماژول در بخشِ `part` ِ خودش یا آخرین بخشِ دسته. بخشِ بی ردیف کشیده نمی‌شود.
 */
@Composable
internal fun RouteScope.rememberCategoryParts(group: String, facts: SettingsFacts, featuresOn: Set<String>): List<CategoryPart> {
  val entries = remember(access) { V3Modules.settingsEntries(access).filter { it.group == group } }
  val shown = entries.filter { it.shown() }
  val all = SettingsLayout.GROUPS.firstOrNull { it.title == group }?.parts.orEmpty()
  val visible = SettingsLayout.visibleParts(group, Platform.DESKTOP, facts.serverOn, featuresOn).associateBy { it.id }
  return all.map { p ->
    CategoryPart(p.id, visible[p.id]?.rows.orEmpty(), shown.filter { (it.part ?: all.last().id) == p.id })
  }.filter { it.rows.isNotEmpty() || it.extra.isNotEmpty() }
}

/**
 *  کارتِ یک بخشِ کوتاه (r5): سرخط بیرونِ کارت، ردیف‌ها با آیکنِ خطی، یک خطِ ساده («این چه می‌کند»، `SettingsHelp`)
 *  و مقدارِ خاکستری / کلید / فلش. `onRow` صفحهٔ همان بخش (یا پیوندش) را باز می‌کند و `onExtra` مسیرِ ردیفِ ماژول را.
 */
@Composable
internal fun PartCard(
  part: CategoryPart,
  facts: SettingsFacts,
  onRow: (String) -> Unit,
  onExtra: (String) -> Unit,
  onLockToggle: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  footer: String? = null,
) {
  V3Section(SettingsLayout.partLabel(part.id), modifier, footer = footer) {
    part.rows.forEachIndexed { i, key ->
      if (i > 0) V3Divider(start = ROW_DIVIDER_START)
      SettingsRow(key, facts, onClick = { onRow(key) }, onLockToggle = onLockToggle)
    }
    part.extra.forEachIndexed { i, e ->
      if (part.rows.isNotEmpty() || i > 0) V3Divider(start = ROW_DIVIDER_START)
      V3ListRow(e.label(), subtitle = e.subtitle(), icon = e.icon, tile = false, onClick = { onExtra(e.route) })
    }
  }
}

/** نشانِ ردیفِ انتخاب‌شده: نوارِ برند در لبهٔ آغاز (رنگ تنها نیست: صفحهٔ کنار همان بخش است) */
@Composable
internal fun SelectedMark(modifier: Modifier) {
  Box(modifier.width(4.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(PhoneV3Roles.focus(PhoneV3.colors)))
}

/** یک ردیف: مقدارِ تازه با محوِ نرم جای قبلی می‌نشیند */
@Composable
internal fun SettingsRow(key: String, facts: SettingsFacts, onClick: () -> Unit, onLockToggle: (Boolean) -> Unit, selected: Boolean? = null) {
  val label = SettingsLayout.labelOf(key)
  val toggle = SettingsIndexModel.toggle(key, facts)
  val value = SettingsIndexModel.value(key, facts)
  V3ListRow(
    title = label,
    //  تنظیماتِ کم: هر ردیفِ دیده‌شده یک خطِ ساده دارد
    subtitle = SettingsHelp.line(key),
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
