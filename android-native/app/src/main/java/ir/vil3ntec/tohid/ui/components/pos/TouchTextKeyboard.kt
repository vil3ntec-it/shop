package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.StrTouch
import ir.vil3ntec.tohid.core.pos.TouchKeyboard
import ir.vil3ntec.tohid.core.pos.TouchKeyboard.Key
import ir.vil3ntec.tohid.core.pos.TouchMode
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Shape as ShopShape

// i18n: سه‌زبانه

/*
 *  صفحه‌کلیدِ حروفِ روی‌صفحه برای کادرِ اسکنِ صندوقِ کامپیوترِ لمسی (POS-31، S18 گامِ ۳؛ §۵.۳) — جست‌وجوی کالا با نام بی
 *  کیبوردِ فیزیکی. فقط با «حالتِ لمسیِ صندوق» ِ روشن و کلیدِ نوارِ روشن (`PosTouchPrefs.extras()`) کلیدش دیده می‌شود و
 *  فقط با زدنِ همان کلید باز می‌شود ⇒ تنظیمِ خاموش یعنی همان صندوقِ امروز. قاعده‌ها در هسته (`TouchKeyboard`).
 *  کلیدها همیشه از چپ به راست، مثلِ کیبوردِ فیزیکی (ض و ۱ سمتِ چپ).
 */

/** باز/بسته و چیدمانِ همین صندوق — چیدمانِ نخست از زبانِ برنامه */
@Stable
class TouchKeyboardUi(initial: TouchKeyboard.Layout) {
  var open by mutableStateOf(false)
  var layout by mutableStateOf(initial)
}

@Composable
fun rememberTouchKeyboard(): TouchKeyboardUi = remember { TouchKeyboardUi(TouchKeyboard.initial(I18n.lang.code)) }

/** کلیدِ «حروف» کنارِ کلیدِ «حالتِ لمسی» — فقط کامپیوترِ لمسی ([show] = `PosTouchPrefs.extras()`) */
@Composable
fun TouchKeysToggle(kb: TouchKeyboardUi, show: Boolean, target: Dp, onDone: () -> Unit) {
  if (!show) return
  val c = Shop.colors
  val label = if (kb.open) StrTouch.lettersClose else StrTouch.lettersOpen
  Spacer(Modifier.width(10.dp))
  OutlinedButton(
    onClick = { kb.open = !kb.open; onDone() },
    shape = ShopShape.button,
    colors = if (kb.open) {
      ButtonDefaults.outlinedButtonColors(containerColor = c.surfaceSelected, contentColor = c.primaryDark)
    } else {
      ButtonDefaults.outlinedButtonColors(contentColor = c.muted)
    },
    contentPadding = PaddingValues(horizontal = 12.dp),
    modifier = Modifier.heightIn(min = target).semantics { contentDescription = label },
  ) {
    Icon(Icons.Filled.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(6.dp))
    Text(StrTouch.letters, style = MaterialTheme.typography.labelLarge, maxLines = 1)
  }
}

/**
 *  خودِ صفحه‌کلید زیرِ کادرِ اسکن — فقط وقتی [show] و باز است. [onKey] هر کلید را به کادر می‌دهد
 *  (`TouchKeyboard.apply`)؛ کلیدِ چیدمان این‌جا چیدمان را می‌چرخاند و به کادر کاری ندارد.
 */
@Composable
fun TouchKeysPanel(kb: TouchKeyboardUi, show: Boolean, target: Dp, onKey: (Key) -> Unit) {
  if (!show || !kb.open) return
  val keyHeight = maxOf(target, TouchMode.TOUCH_TARGET_DP.dp)
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      for (row in TouchKeyboard.rows(kb.layout)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TouchMode.DANGER_GAP_DP.dp / 2)) {
          for (key in row) {
            val weight = when (key) {
              Key.Space -> 4f
              is Key.Letter -> 1f
              else -> 1.6f
            }
            KeyCap(text(key, kb.layout), describe(key, kb.layout), keyHeight, Modifier.weight(weight)) {
              if (key == Key.NextLayout) kb.layout = TouchKeyboard.next(kb.layout)
              onKey(key)
            }
          }
        }
      }
    }
  }
}

/**
 *  POS-31 — صفحه‌کلیدِ حروف برای هر کادرِ متنیِ دیگرِ صندوقِ کامپیوترِ لمسی (§۵.۳ «با فوکوس هر فیلد متنی در حالت لمسی
 *  باز می‌شود»؛ نامِ قلمِ آزاد، یادداشتِ ردیف و فاکتور). [show] = `PosTouchPrefs.extras()`؛ خاموش و گوشی ⇒ هیچ.
 */
@Stable
class TouchTextKeys(val ui: TouchKeyboardUi, val show: Boolean)

@Composable
fun rememberTouchTextKeys(show: Boolean = PosTouchPrefs.extras()): TouchTextKeys {
  val ui = rememberTouchKeyboard()
  return remember(ui, show) { TouchTextKeys(ui, show) }
}

/** فوکوسِ کادر ⇒ صفحه‌کلید باز (فقط کامپیوترِ لمسی؛ بستن فقط با کلیدِ «حروف») */
fun Modifier.touchKeysOnFocus(keys: TouchTextKeys): Modifier =
  if (!keys.show) this else onFocusChanged { if (it.isFocused) keys.ui.open = true }

/** کلیدِ «حروف» و خودِ صفحه‌کلید زیرِ کادر؛ هر کلید با `TouchKeyboard.apply` (تا [max] نویسه) به [onValue] */
@Composable
fun TouchTextKeysPanel(keys: TouchTextKeys, value: String, onValue: (String) -> Unit, max: Int = TouchKeyboard.MAX) {
  if (!keys.show) return
  val target = TouchMode.TOUCH_TARGET_DP.dp
  Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) { TouchKeysToggle(keys.ui, true, target) {} }
  TouchKeysPanel(keys.ui, true, target) { onValue(TouchKeyboard.apply(value, it, max)) }
}

private fun text(key: Key, layout: TouchKeyboard.Layout): String = when (key) {
  is Key.Letter -> key.text
  Key.Space -> StrTouch.space
  Key.HalfSpace -> StrTouch.halfSpace
  Key.Backspace -> "⌫"
  Key.Clear -> StrTouch.clear
  Key.NextLayout -> layoutName(TouchKeyboard.next(layout))
}

private fun layoutName(l: TouchKeyboard.Layout): String = when (l) {
  TouchKeyboard.Layout.FA -> StrTouch.layoutFa
  TouchKeyboard.Layout.PS -> StrTouch.layoutPs
  TouchKeyboard.Layout.LATIN -> StrTouch.layoutLatin
}

private fun describe(key: Key, layout: TouchKeyboard.Layout): String = when (key) {
  Key.Backspace -> StrTouch.backLetter
  Key.Clear -> StrTouch.clearAll
  Key.NextLayout -> StrTouch.nextLayout(layoutName(TouchKeyboard.next(layout)))
  else -> text(key, layout)
}

@Composable
private fun KeyCap(text: String, description: String, height: Dp, modifier: Modifier, onClick: () -> Unit) {
  OutlinedButton(
    onClick = onClick,
    shape = ShopShape.button,
    colors = ButtonDefaults.outlinedButtonColors(contentColor = Shop.colors.text),
    contentPadding = PaddingValues(horizontal = 2.dp),
    modifier = modifier.heightIn(min = height).semantics { contentDescription = description },
  ) {
    Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Clip)
  }
}
