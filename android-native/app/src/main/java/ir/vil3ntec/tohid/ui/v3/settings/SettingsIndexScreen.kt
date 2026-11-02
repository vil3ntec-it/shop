package ir.vil3ntec.tohid.ui.v3.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrSettingsHelp
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.screens.SettingsTiers
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.settings.sections.RemovePinSheet
import ir.vil3ntec.tohid.ui.v3.settings.sections.SettingsShotState
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/** «حساب و نام دکان» جای خودش را دارد */
private const val PROFILE_EDIT = "profile/edit"

/**
 *  ریشهٔ تنظیمات (`settings`، r5): برگشتِ گرد و عنوانِ وسط، کارتِ گروه‌ها با ردیف‌هایی که هر کدام صفحهٔ
 *  خودشان را باز می‌کنند، و پانویسِ «حساب من».
 *
 *  تنظیماتِ کم (تصمیمِ صاحب‌کار، ۱۴۰۵/۰۸/۱۱، `SettingsTiers`): بالا فقط بخش‌های اصلی؛ تصمیم‌های پولی و گزینه‌های
 *  کم‌کاربرد زیرِ ردیفِ بستهٔ «پیشرفته» در ته؛ گزینه‌های بی‌خطرِ روشن (A) هیچ ردیفی ندارند.
 */
@Composable
internal fun RouteScope.SettingsIndexScreen(onBack: (() -> Unit)?) {
  val facts = rememberSettingsFacts(theme)
  val extra = remember(access) { V3Modules.settingsEntries(access) }
  val stagger = rememberV3Stagger()
  val lockToggle = rememberLockToggle()
  var advanced by rememberSaveable { mutableStateOf(SettingsShotState.advancedOpen) }
  SettingsPageFrame(title = Str.settings, onBack = onBack, largeTitle = false) {
    itemsIndexed(SettingsLayout.GROUPS, key = { _, g -> g.title }) { i, g ->
      SettingsGroupCard(
        group = g.title,
        facts = facts,
        extra = emptyList(),
        open = { nav.open(it) },
        onLockToggle = lockToggle,
        modifier = Modifier.v3Stagger(stagger, g.title, i),
      )
    }
    item(key = "advanced") { AdvancedRow(advanced) { advanced = !advanced } }
    if (advanced) {
      itemsIndexed(SettingsLayout.GROUPS, key = { _, g -> "advanced/" + g.title }) { _, g ->
        SettingsGroupCard(
          group = g.title,
          facts = facts,
          extra = extra.filter { it.group == g.title }.filter { it.shown() },
          open = { nav.open(it) },
          onLockToggle = lockToggle,
          tier = SettingsTiers.Tier.ADVANCED,
        )
      }
    }
    item(key = "footer") { AccountFooter { nav.open(PROFILE_EDIT) } }
  }
}

/** ردیفِ «پیشرفته»: بسته ⇒ فقط همین یک ردیف؛ زدن کارت‌های گزینه‌های پیشرفته را زیرش باز/بسته می‌کند */
@Composable
internal fun AdvancedRow(open: Boolean, onToggle: () -> Unit) {
  V3Section(null) {
    V3ListRow(
      StrSettingsHelp.advanced,
      subtitle = StrSettingsHelp.advancedLine,
      icon = Icons.Outlined.Tune,
      tile = false,
      value = if (open) Str.close else StrSettings.open,
      chevron = !open,
      onClick = onToggle,
    )
  }
}

/**
 *  صفحهٔ یک گروه (`settings/security`…) — همان ردیفی که «بیشتر ⇐ تنظیمات» می‌زند
 *  یک‌راست این‌جا می‌آید: عنوانِ درشت = نامِ گروه، کارتِ بخش‌های اصلیِ همین گروه، و «پیشرفته» ِ همین گروه (بسته).
 */
@Composable
internal fun RouteScope.SettingsGroupScreen(group: String, onBack: (() -> Unit)?) {
  val facts = rememberSettingsFacts(theme)
  val extra = remember(access) { V3Modules.settingsEntries(access).filter { it.group == group } }
  val lockToggle = rememberLockToggle()
  var advanced by rememberSaveable(group) { mutableStateOf(SettingsShotState.advancedOpen) }
  SettingsPageFrame(title = SettingsLayout.groupLabel(group), onBack = onBack) {
    item(key = "card") {
      SettingsGroupCard(
        group = group,
        facts = facts,
        extra = emptyList(),
        open = { nav.open(it) },
        onLockToggle = lockToggle,
        showTitle = false,
        footer = if (group == SettingsLayout.SECURITY) StrSettings.securityHelp else null,
      )
    }
    item(key = "advanced") { AdvancedRow(advanced) { advanced = !advanced } }
    if (advanced) {
      item(key = "advanced-card") {
        SettingsGroupCard(
          group = group,
          facts = facts,
          extra = extra.filter { it.shown() },
          open = { nav.open(it) },
          onLockToggle = lockToggle,
          showTitle = false,
          tier = SettingsTiers.Tier.ADVANCED,
        )
      }
    }
  }
}

/**
 *  کلیدِ «قفلِ برنامه» در ردیف: روشن ⇐ برگهٔ رمز (`settings/security/app-lock/pin`)؛
 *  خاموش ⇐ برگهٔ تأییدِ «برداشتن رمز».
 */
@Composable
internal fun RouteScope.rememberLockToggle(): (Boolean) -> Unit {
  var askRemove by remember { mutableStateOf(false) }
  if (askRemove) RemovePinSheet(onDismiss = { askRemove = false })
  return { on -> if (on) nav.open(SettingsPaths.PIN) else askRemove = true }
}

@Composable
private fun AccountFooter(onOpen: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().padding(top = 18.dp, start = 6.dp, end = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(StrSettings.accountElsewhere, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.weight(1f))
    V3TextButton(Str.myAccount, onOpen)
  }
}
