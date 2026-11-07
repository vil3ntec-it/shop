package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.pos.TillPins

/**
 *  POS-06 — کالاهای سنجاق‌شدهٔ صندوقِ **همین دستگاه** (`tohid-ui:till_pins`، هر شناسه یک خط)؛ فایلِ مرزیِ پلتفرم
 *  (`SharedPreferences`). قاعده در هسته (`core/…/pos/TillPins.kt`). نبود ⇒ بی سنجاق ⇒ شبکهٔ کالا همان امروز.
 *  همگام نمی‌شود: هر صندوق چیدمانِ خودش را دارد.
 */
object TillPinsPrefs {
  val pins = mutableStateOf(emptyList<String>())
  private var loaded = false
  private const val PREFS = "tohid-ui"
  private const val KEY = "till_pins"

  fun load(context: android.content.Context) {
    if (loaded) return
    loaded = true
    runCatching { pins.value = TillPins.decode(context.getSharedPreferences(PREFS, 0).getString(KEY, null)) }
  }

  /** سنجاق یا برداشتن؛ @return چه شد (`TillPins.change`: پُر ⇒ بی تغییر، برای پیامِ درست) */
  fun toggle(context: android.content.Context, id: String): TillPins.Change {
    load(context)
    val change = TillPins.change(pins.value, id)
    val next = TillPins.toggle(pins.value, id)
    if (next == pins.value) return change
    pins.value = next
    runCatching { context.getSharedPreferences(PREFS, 0).edit().putString(KEY, TillPins.encode(next)).apply() }
    return change
  }

  /** سنجاق یا برداشتن و پیامِ همان کار (صندوقِ کامپیوتر)؛ null ⇒ هیچ */
  fun toggleSay(context: android.content.Context, id: String, name: String): String? = message(toggle(context, id), name)

  /** پیامِ همان کار (گوشی و کامپیوتر یکی)؛ null ⇒ هیچ */
  fun message(change: TillPins.Change, name: String): String? = when (change) {
    TillPins.Change.PINNED -> ir.vil3ntec.tohid.core.i18n.StrSale.pinnedFirst(name)
    TillPins.Change.UNPINNED -> ir.vil3ntec.tohid.core.i18n.StrSale.unpinnedFirst(name)
    TillPins.Change.FULL -> ir.vil3ntec.tohid.core.i18n.StrSale.pinsFull
    TillPins.Change.NONE -> null
  }
}

/** برگه‌ای که Context ندارد (`KeypadRoute`): سنجاق‌ها بار می‌شوند و این تابع سنجاق/برداشتن را می‌کند؛ @return چه شد */
@Composable
fun rememberTillPinToggle(): (String) -> TillPins.Change {
  val context = LocalContext.current
  return remember(context) { TillPinsPrefs.load(context); { id: String -> TillPinsPrefs.toggle(context, id) } }
}
