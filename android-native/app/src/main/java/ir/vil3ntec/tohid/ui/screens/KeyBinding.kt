package ir.vil3ntec.tohid.ui.screens

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import ir.vil3ntec.tohid.core.keymap.Chord
import ir.vil3ntec.tohid.core.keymap.KeyAction
import ir.vil3ntec.tohid.core.keymap.KeyName

/**
 *  ثبتِ مرکزیِ کلیدها در Compose — MASTER-SPEC ۳.۱۶.
 *
 *  صفحه کلید را از `KeyMap` می‌گیرد: `if (e.matches(KeyMap.GLOBAL_PRINT)) print()`
 *  و برچسبِ دکمه را هم: `KeyMap.GLOBAL_PRINT.hint` (روی مک «Cmd+P»).
 *  کلید با کدِ فیزیکی (`Key.*`) خوانده می‌شود، پس چیدمانِ فارسی و پشتو فرقی
 *  نمی‌کند (۵.۱ بند ۳). پیرایه‌ها دقیق‌اند: Ctrl+Shift+P همان Ctrl+P نیست.
 */

/** Mac؟ — فقط برای برچسب؛ خودِ کلید هم با Ctrl و هم با Cmd کار می‌کند (`isPrimaryModifier`) */
private val onMac: Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac")

fun KeyEvent.matches(c: Chord): Boolean =
  isPrimaryModifier == c.mod && isShiftPressed == c.shift && isAltPressed == c.alt && c.key.matches(key)

/** فقط KeyDown — همان قاعدهٔ همهٔ `onPreviewKeyEvent`های صفحه‌ها */
fun KeyEvent.matches(a: KeyAction): Boolean = type == KeyEventType.KeyDown && a.keys.any { matches(it) }

/** برچسبِ کوتاهِ نخستین کلید: «Ctrl+P»، روی مک «Cmd+P» */
val KeyAction.hint: String get() = hint(onMac)

/** نامِ کلید ⇒ `Key`ِ Compose. `when`ِ کامل است: نامِ تازه در keymap بی نگاشت کامپایل نمی‌شود. */
internal fun KeyName.matches(k: Key): Boolean = when (this) {
  KeyName.A -> k == Key.A
  KeyName.B -> k == Key.B
  KeyName.C -> k == Key.C
  KeyName.D -> k == Key.D
  KeyName.E -> k == Key.E
  KeyName.F -> k == Key.F
  KeyName.G -> k == Key.G
  KeyName.H -> k == Key.H
  KeyName.I -> k == Key.I
  KeyName.J -> k == Key.J
  KeyName.K -> k == Key.K
  KeyName.L -> k == Key.L
  KeyName.M -> k == Key.M
  KeyName.N -> k == Key.N
  KeyName.O -> k == Key.O
  KeyName.P -> k == Key.P
  KeyName.Q -> k == Key.Q
  KeyName.R -> k == Key.R
  KeyName.S -> k == Key.S
  KeyName.T -> k == Key.T
  KeyName.U -> k == Key.U
  KeyName.V -> k == Key.V
  KeyName.W -> k == Key.W
  KeyName.X -> k == Key.X
  KeyName.Y -> k == Key.Y
  KeyName.Z -> k == Key.Z
  KeyName.D0 -> k == Key.Zero || k == Key.NumPad0
  KeyName.D1 -> k == Key.One
  KeyName.D2 -> k == Key.Two
  KeyName.D3 -> k == Key.Three
  KeyName.D4 -> k == Key.Four
  KeyName.D5 -> k == Key.Five
  KeyName.D6 -> k == Key.Six
  KeyName.D7 -> k == Key.Seven
  KeyName.D8 -> k == Key.Eight
  KeyName.D9 -> k == Key.Nine
  KeyName.F1 -> k == Key.F1
  KeyName.F2 -> k == Key.F2
  KeyName.F3 -> k == Key.F3
  KeyName.F4 -> k == Key.F4
  KeyName.F5 -> k == Key.F5
  KeyName.F6 -> k == Key.F6
  KeyName.F7 -> k == Key.F7
  KeyName.F8 -> k == Key.F8
  KeyName.F9 -> k == Key.F9
  KeyName.F10 -> k == Key.F10
  KeyName.F11 -> k == Key.F11
  KeyName.F12 -> k == Key.F12
  KeyName.Enter -> k == Key.Enter || k == Key.NumPadEnter
  KeyName.Escape -> k == Key.Escape
  KeyName.Delete -> k == Key.Delete
  KeyName.Tab -> k == Key.Tab
  KeyName.Space -> k == Key.Spacebar
  KeyName.Home -> k == Key.MoveHome
  KeyName.End -> k == Key.MoveEnd
  KeyName.PageUp -> k == Key.PageUp
  KeyName.PageDown -> k == Key.PageDown
  KeyName.Up -> k == Key.DirectionUp
  KeyName.Down -> k == Key.DirectionDown
  KeyName.Left -> k == Key.DirectionLeft
  KeyName.Right -> k == Key.DirectionRight
  KeyName.Plus -> k == Key.Plus || k == Key.NumPadAdd
  KeyName.Minus -> k == Key.Minus || k == Key.NumPadSubtract
  KeyName.Equals -> k == Key.Equals
  KeyName.Slash -> k == Key.Slash
}
