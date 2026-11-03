package ir.vil3ntec.tohid.ui.components.keys

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ir.vil3ntec.tohid.core.i18n.StrTill
import ir.vil3ntec.tohid.core.keymap.KeyMap
import ir.vil3ntec.tohid.core.keymap.KeyScope
import ir.vil3ntec.tohid.core.keymap.KeyStatus
import ir.vil3ntec.tohid.core.keys.KeyHelp
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.delay

/**
 *  S50 (POS-41، بخشِ ۳.۱۶) — راهنمای کلیدِ هر صفحه از `keymap.json` و برچسب‌های «نگه‌داشتنِ Ctrl»، فقط کامپیوتر.
 *
 *  ⛔ پشتِ تنظیمِ همین دستگاه (`tohid-ui:key_help`)، پیش‌فرض **روشن** (تنظیماتِ کم، ۱۴۰۵/۰۸/۱۱)؛ **خاموش**: پنجرهٔ F1 همان فهرستِ دست‌نوشتهٔ
 *  پیشین (`ShortcutsHelp` ِ `desktop/Main.kt`) و هیچ برچسبی با Ctrl. منطق در `core/keys/KeyHelp`؛ این‌جا فقط
 *  مرزِ پلتفرم (`SharedPreferences`، ساعت، کلیدِ Compose) و نمایش.
 */
object KeyHelpPref {
  val on = mutableStateOf(false)
  private var loaded = false
  private const val KEY = "key_help"

  fun load(context: android.content.Context) {
    if (loaded) return
    loaded = true
    on.value = context.getSharedPreferences("tohid-ui", 0).getBoolean(KEY, true)
  }

  fun set(context: android.content.Context, value: Boolean) {
    on.value = value
    if (!value) AppNav.keyLabels.value = false
    context.getSharedPreferences("tohid-ui", 0).edit().putBoolean(KEY, value).apply()
  }
}

private val onMac: Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac")

/** همهٔ کارهای keymap برای `KeyHelp` — برچسبِ کلید با Ctrl یا Cmd ِ همین پلتفرم */
fun keyHelpItems(mac: Boolean = onMac): List<KeyHelp.Item> = KeyMap.ALL.map { a ->
  KeyHelp.Item(
    id = a.id, scope = a.scope, title = a.title, keys = a.keys.map { it.label(mac) },
    listed = a.status != KeyStatus.PLANNED, overrides = a.overrides, mod = a.keys.firstOrNull()?.mod == true,
  )
}

private val globalScopes: Set<String> = KeyMap.SCOPES.filter { it.kind == KeyScope.Kind.GLOBAL }.map { it.id }.toSet()

/** فهرستِ همان صفحه‌ای که باز است (`AppNav.route`) */
fun keyHelpSections(route: String?): List<KeyHelp.Section> =
  KeyHelp.forScreen(keyHelpItems(), globalScopes, KeyHelp.scopeOf(route))

private fun scopeTitle(id: String): String = KeyMap.SCOPES.firstOrNull { it.id == id }?.title ?: id

/** پنجرهٔ F1 با تنظیمِ روشن: کلیدهای همین صفحه، بعد سراسری — همه از keymap.json */
@Composable
fun KeyMapHelp() {
  val route by AppNav.route
  val sections = remember(route) { keyHelpSections(route) }
  ir.vil3ntec.tohid.ui.ProvideLang {
    Column(Modifier.fillMaxSize().background(Shop.colors.surfaceSolid).verticalScroll(rememberScrollState()).padding(16.dp)) {
      //  OPS-06: راهنمای همین صفحه، مثلِ فهرستِ پیشین
      val guide = ir.vil3ntec.tohid.core.guide.ScreenGuides.forScreen(AppNav.screen.value)
      if (guide != null) {
        val lang = ir.vil3ntec.tohid.LocaleChoice.lang.code
        Text(guide.title.of(lang), fontWeight = FontWeight.Bold, color = Shop.colors.primary, modifier = Modifier.padding(bottom = 4.dp))
        guide.steps.forEachIndexed { i, step -> Text("${i + 1}. ${step.of(lang)}", color = Shop.colors.text, modifier = Modifier.padding(vertical = 2.dp)) }
      }
      Text(StrTill.keyHelpScreen, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
      sections.forEach { s ->
        Text(scopeTitle(s.scope), fontWeight = FontWeight.Bold, color = Shop.colors.primary, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
        s.items.forEach { a ->
          Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(a.title, color = Shop.colors.text, modifier = Modifier.weight(1f))
            KeyCap(a.keys.joinToString(" / "))
          }
        }
      }
    }
  }
}

@Composable
private fun KeyCap(text: String) {
  Text(text, color = Shop.colors.text, modifier = Modifier.background(Shop.colors.primaryTint).padding(horizontal = 6.dp, vertical = 1.dp))
}

/**
 *  «نگه‌داشتنِ Ctrl نیم ثانیه ⇒ برچسب‌ها» — پنجرهٔ کامپیوتر هر کلید را پیش از همه به [onKey] می‌دهد
 *  (هیچ کلیدی را نمی‌گیرد). تنظیمِ خاموش ⇒ هیچ.
 */
object KeyLabels {
  private val hold = KeyHelp.Hold()

  /** زمانِ فشردنِ Ctrl ِ تنها، برای `LaunchedEffect` ِ [KeyLabelsOverlay] */
  private val pressed = mutableStateOf<Long?>(null)

  fun onKey(e: KeyEvent) {
    if (!KeyHelpPref.on.value) return
    val ctrl = e.key == Key.CtrlLeft || e.key == Key.CtrlRight || e.key == Key.MetaLeft || e.key == Key.MetaRight
    val now = System.currentTimeMillis()
    when (e.type) {
      KeyEventType.KeyDown -> {
        //  Alt هرگز (۵.۱): Ctrl+Alt یا Ctrl+Shift برچسب نمی‌آورد
        hold.down(ctrl && !e.isAltPressed && !e.isShiftPressed, now)
        if (!ctrl) AppNav.keyLabels.value = false
        //  تکرارِ خودکارِ Ctrl زمان را از نو نمی‌گیرد
        if (hold.waitMs(now) == null) pressed.value = null else if (pressed.value == null) pressed.value = now
      }
      KeyEventType.KeyUp -> if (ctrl) {
        hold.up()
        pressed.value = null
        AppNav.keyLabels.value = false
      }
      else -> Unit
    }
  }

  internal suspend fun await() {
    val wait = hold.waitMs(System.currentTimeMillis()) ?: return
    delay(wait)
    AppNav.keyLabels.value = hold.shown(System.currentTimeMillis())
  }

  internal val pressedAt get() = pressed
}

/** برچسب‌های Ctrl ِ همین صفحه، پایینِ پنجره — روی همه‌چیز و بی گرفتنِ فوکوس */
@Composable
fun KeyLabelsOverlay() {
  if (!KeyHelpPref.on.value) return
  val at by KeyLabels.pressedAt
  LaunchedEffect(at) { if (at != null) KeyLabels.await() }
  if (!AppNav.keyLabels.value) return
  val route by AppNav.route
  val labels = remember(route) { KeyHelp.ctrlLabels(keyHelpSections(route)) }
  if (labels.isEmpty()) return
  Popup(alignment = Alignment.BottomCenter, properties = PopupProperties(focusable = false)) {
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(10.dp), shadowElevation = 6.dp, modifier = Modifier.padding(12.dp)) {
      ir.vil3ntec.tohid.ui.ProvideLang {
        Row(
          Modifier.horizontalScroll(rememberScrollState()).padding(8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          labels.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              KeyCap(a.keys.first())
              Text(a.title, style = MaterialTheme.typography.labelMedium, color = Shop.colors.text)
            }
          }
        }
      }
    }
  }
}
