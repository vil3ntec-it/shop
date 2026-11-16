package ir.vil3ntec.tohid.ui.v3.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import ir.vil3ntec.tohid.ui.screens.SettingsHelp
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3NavRow
import ir.vil3ntec.tohid.ui.v3.V3NavRowSpec
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.nav.SettingsEntry
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.v3.settings.sections.rememberLockOn
import ir.vil3ntec.tohid.ui.v3.settings.sections.rememberServerSections

/** خطِ مویی میانِ ردیف‌ها، از زیرِ متن — همان «بیشتر» (کاشیِ ۴۰ + فاصله‌ها، `V3NavRowSpec`) */
internal val ROW_DIVIDER_START = V3NavRowSpec.DIVIDER_START

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
    version = ir.vil3ntec.tohid.ui.previewVersionLabel(BuildConfig.VERSION_NAME),
    serverOn = rememberServerSections(),
    owner = remember { ir.vil3ntec.tohid.data.ShopRole.current(context) == ir.vil3ntec.tohid.data.ShopRole.OWNER },
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
    val rows = visible[p.id]?.rows.orEmpty().filter { facts.owner || it !in SettingsLayout.OWNER_ONLY }
    CategoryPart(p.id, rows, shown.filter { (it.part ?: all.last().id) == p.id })
  }.filter { it.rows.isNotEmpty() || it.extra.isNotEmpty() }
}

/**
 *  کارتِ یک بخشِ کوتاه: سرخط بیرونِ کارت و ردیف‌ها همان ردیفِ «بیشتر» (`V3NavRow`، ۶۵r) — کاشیِ رنگیِ بخش
 *  (`SettingsHues`)، عنوانِ یک‌خطی، یک خطِ ساده («این چه می‌کند»، `SettingsHelp`) و مقدارِ کم‌رنگ کنارِ پیکان یا کلید.
 *  `onRow` صفحهٔ همان بخش (یا پیوندش) را باز می‌کند و `onExtra` مسیرِ ردیفِ ماژول را.
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
      V3NavRow(e.label(), icon = e.icon, hue = SettingsHues.extra(e.group, e.part ?: part.id), help = e.subtitle(), onClick = { onExtra(e.route) })
    }
  }
}

/** نشانِ ردیفِ انتخاب‌شده: نوارِ برند در لبهٔ آغاز (رنگ تنها نیست: صفحهٔ کنار همان بخش است) */
@Composable
internal fun SelectedMark(modifier: Modifier) {
  Box(modifier.width(4.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(PhoneV3Roles.focus(PhoneV3.colors)))
}

/**
 *  یک ردیفِ تنظیمات — همان ردیفِ «بیشتر» (`V3NavRow`): کاشیِ رنگیِ بخش، عنوان، یک خطِ ساده (`SettingsHelp.line`)
 *  و در پایان مقدارِ کم‌رنگ پیش از پیکان (مقدارِ تازه با محوِ نرم جای قبلی می‌نشیند)، یا کلیدِ خودِ ردیف.
 */
@Composable
internal fun SettingsRow(key: String, facts: SettingsFacts, onClick: () -> Unit, onLockToggle: (Boolean) -> Unit, selected: Boolean? = null) {
  val label = SettingsLayout.labelOf(key)
  val toggle = SettingsIndexModel.toggle(key, facts)
  val value = SettingsIndexModel.value(key, facts)
  V3NavRow(
    title = label,
    icon = SettingsPages.icon(key),
    hue = SettingsHues.of(key),
    //  تنظیماتِ کم: هر ردیفِ دیده‌شده یک خطِ ساده دارد
    help = SettingsHelp.line(key),
    value = value.takeIf { toggle == null },
    valueDot = if (key == "همگام‌سازی" && value != null) syncDot(facts.sync) else null, // داده: کلیدِ بخشِ تنظیمات
    toggle = toggle,
    onToggle = onLockToggle,
    selected = selected,
    onClick = onClick,
  )
}

/** همگام‌سازی: نقطهٔ وضعیت (رنگ + نوشته، نه رنگِ تنها) پیش از «۲ دقیقه پیش» */
@Composable
private fun syncDot(state: SyncState): androidx.compose.ui.graphics.Color? {
  val tone = when (state) {
    SyncState.OK -> V3Tone.SUCCESS
    SyncState.WAITING -> V3Tone.WARNING
    SyncState.FAILED -> V3Tone.ERROR
    SyncState.OFF -> null
  } ?: return null
  return PhoneV3Roles.toneIcon(PhoneV3.colors, tone)
}
