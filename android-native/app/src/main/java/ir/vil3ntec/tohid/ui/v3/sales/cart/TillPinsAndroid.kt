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

  /** سنجاق یا برداشتن؛ @return حالا سنجاق‌شده است؟ */
  fun toggle(context: android.content.Context, id: String): Boolean {
    load(context)
    val next = TillPins.toggle(pins.value, id)
    pins.value = next
    runCatching { context.getSharedPreferences(PREFS, 0).edit().putString(KEY, TillPins.encode(next)).apply() }
    return id in next
  }
}

/** برگه‌ای که Context ندارد (`KeypadRoute`): سنجاق‌ها بار می‌شوند و این تابع سنجاق/برداشتن را می‌کند؛ @return حالا سنجاق است؟ */
@Composable
fun rememberTillPinToggle(): (String) -> Boolean {
  val context = LocalContext.current
  return remember(context) { TillPinsPrefs.load(context); { id: String -> TillPinsPrefs.toggle(context, id) } }
}
