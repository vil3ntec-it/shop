package ir.vil3ntec.tohid.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.screens.Screen
import ir.vil3ntec.tohid.ui.screens.screenSize
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  نمای همین صفحه (کارت/جدول) به‌همراهِ ذخیره — `ViewModePref`.
 *
 *  `mode` را بخوانید و با `set()` عوض کنید؛ ذخیره برای همین کاربر و همین
 *  صفحه روی همین دستگاه انجام می‌شود. فیلتر و انتخاب مالِ خودِ صفحه‌اند و
 *  با عوض‌شدنِ نما دست نمی‌خورند.
 */
@Stable
class ViewModeState internal constructor(
  initial: ViewMode,
  val toggleVisible: Boolean,
  private val persist: (ViewMode) -> Unit,
) {
  var mode by mutableStateOf(initial)
    private set

  fun set(next: ViewMode) {
    if (next == mode) return
    mode = next
    persist(next)
  }
}

/** شکلِ صفحه از نگاهِ `ViewModePref`: کامپیوتر و تبلت «بزرگ»، گوشی «گوشی» */
@Composable
fun currentFormFactor(): FormFactor =
  if (Platform.DESKTOP || screenSize() != Screen.PHONE) FormFactor.LARGE else FormFactor.PHONE

/**
 *  @param screen شناسهٔ پایدارِ صفحه، مثلِ "products" یا "sale"
 *  @param kind مدیریت (پیش‌فرض جدول) یا فروش (پیش‌فرض کارت)
 */
@Composable
fun rememberViewMode(screen: String, kind: ScreenKind): ViewModeState {
  val context = LocalContext.current
  val form = currentFormFactor()
  val user = remember(context) { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountId }.getOrDefault("") }
  val store = remember(context) {
    ViewModePref.Store.of(context.getSharedPreferences(ViewModePref.PREFS, android.content.Context.MODE_PRIVATE))
  }
  return remember(screen, kind, form, user) {
    ViewModeState(
      initial = ViewModePref.load(store, user, screen, kind, form),
      toggleVisible = ViewModePref.toggleVisible(form),
    ) { ViewModePref.save(store, user, screen, it, form) }
  }
}

/**
 *  کلیدِ دوتاییِ «کارت | جدول» — بخشی از `TableToolbar`، ولی جدا هم
 *  به کار می‌رود (صفحهٔ فروش). روی گوشی هیچ چیزی نمی‌کشد.
 */
@Composable
fun ViewModeToggle(state: ViewModeState, modifier: Modifier = Modifier) {
  if (!state.toggleVisible) return
  val colors = Shop.colors
  val cell = if (Platform.DESKTOP) 34.dp else 48.dp
  Row(
    modifier
      .clip(Shape.button)
      .border(1.dp, colors.border, Shape.button)
      .background(colors.surfaceSolid)
      .padding(2.dp)
      .selectableGroup(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    for ((m, label) in listOf(ViewMode.CARD to "نمای کارتی", ViewMode.TABLE to "نمای جدولی")) {
      val on = state.mode == m
      Box(
        Modifier
          .size(cell)
          .clip(Shape.button)
          .background(if (on) colors.primaryTint else colors.surfaceSolid)
          .selectable(selected = on, role = Role.Tab) { state.set(m) }
          .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          if (m == ViewMode.CARD) Icons.Filled.GridView else Icons.Filled.TableRows,
          contentDescription = null,
          tint = if (on) colors.primary else colors.muted,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}
