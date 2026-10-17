package ir.vil3ntec.tohid.scan

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.utf16CodePoint

/*
 *  F0-06 — کلیدِ فیزیکیِ یک رویداد برای `ScanKeys`. `Key` در اندروید کدِ
 *  کلیدِ سخت‌افزار است و در کامپیوتر کدِ مجازیِ AWT؛ هیچ‌کدام به چیدمانِ
 *  فارسی/پشتو بستگی ندارد.
 */
private val DIGITS = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
private val NUMPAD = listOf(
  Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4,
  Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9,
)
private val LETTERS = listOf(
  Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
  Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z,
)

/** نویسهٔ بارکد از این کلید (`ScanKeys.charOf`)، یا `null` */
fun KeyEvent.scanChar(allowSpace: Boolean = false): Char? {
  val digit = DIGITS.indexOf(key).let { if (it >= 0) it else NUMPAD.indexOf(key) }
  return ScanKeys.charOf(
    digit = digit,
    letter = LETTERS.indexOf(key),
    shift = isShiftPressed,
    codePoint = utf16CodePoint,
    allowSpace = allowSpace,
  )
}
