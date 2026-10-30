package ir.vil3ntec.tohid.ui.v3.online.storefront

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.data.repo.StorefrontConfigDto
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3MenuItem
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3PageSwitch
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.online.CountTab
import ir.vil3ntec.tohid.ui.v3.online.DraftCountTabs

/**
 *  قابِ «ویترین» (`storefront?tab=`): دروازه‌ها به ترتیبِ online.md (روشن نیست ⇐ فقط صاحب/مدیر ⇐
 *  بی اشتراک = نوارِ هشدار ⇐ ساخته‌نشده = «رفتن به تنظیمات» در هر برگه‌ای که ویترین می‌خواهد)،
 *  تب‌های لغزان، و جابه‌جاییِ برگه‌ها با همان حرکتِ کیت. هر برگه محتوا و نوارِ پایینِ خودش را دارد.
 */
@Composable
fun StorefrontScreen(
  config: StorefrontConfigDto?,
  loading: Boolean,
  error: String?,
  tab: String,
  onTab: (String) -> Unit,
  onRetry: () -> Unit,
  onBack: (() -> Unit)?,
  bottomBar: (@Composable () -> Unit)? = null,
  pane: @Composable (tab: String, pad: PaddingValues) -> Unit,
) {
  val tabs = StorefrontModel.tabs(config)
  val ready = config != null && config.flag && config.permissions.manage
  V3Scaffold(
    title = StrOnline.sfTitle,
    subtitle = StrOnline.sfCaption,
    onBack = onBack,
    overflow = listOf(V3MenuItem(Icons.Outlined.Refresh, StrOnline.reload, onClick = onRetry)),
    chips = if (ready) ({ DraftCountTabs(tabs.map { CountTab(it, StorefrontModel.label(it)) }, tab, onTab) }) else null,
    bottomBar = if (ready && !(config?.settings == null && tab in StorefrontModel.NEEDS_SETTINGS)) bottomBar else null,
  ) { pad ->
    when {
      config == null && loading -> Column(Modifier.fillMaxSize().padding(16.dp)) { V3Skeleton(rows = 4) }
      config == null -> V3EmptyState(Icons.Outlined.CloudOff, StrOnline.sfLoadFailed, body = error ?: StrOnline.checkConnection, action = StrKit.retry, onAction = onRetry)
      !config.flag -> V3EmptyState(Icons.Outlined.Storefront, StrOnline.sfOffTitle, body = StrOnline.sfOffBody)
      !config.permissions.manage -> V3EmptyState(Icons.Outlined.Lock, StrOnline.sfOwnerTitle, body = StrOnline.sfOwnerBody)
      else -> Column(Modifier.fillMaxSize()) {
        if (!config.entitled) V3Message(V3Tone.WARNING, StrOnline.sfNotEntitled, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        V3PageSwitch(tab, depth = { tabs.indexOf(it) }, modifier = Modifier.weight(1f)) { t ->
          if (config.settings == null && t in StorefrontModel.NEEDS_SETTINGS) {
            Box(Modifier.fillMaxSize()) {
              V3EmptyState(
                Icons.Outlined.Storefront, StrOnline.sfCreateFirst, body = StrOnline.sfCreateBody,
                action = StrOnline.sfGoSettings, onAction = { onTab(StorefrontModel.SETTINGS) },
              )
            }
          } else pane(t, pad)
        }
      }
    }
  }
}
