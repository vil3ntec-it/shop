package android.content

import platform.UIKit.UIPasteboard

/*
 *  shimِ iOS ِ تخته‌برشِ اندروید روی `UIPasteboard.generalPasteboard` — همان کارِ «رونوشت» ِ کدِ مشترک.
 *  `IosContext.getSystemService(CLIPBOARD_SERVICE)` همین را می‌دهد.
 */
class ClipData private constructor(val label: CharSequence?, internal val text: CharSequence?) {
  class Item(val text: CharSequence?) {
    fun coerceToText(context: Context): CharSequence = text ?: ""
  }

  val itemCount: Int get() = if (text == null) 0 else 1
  fun getItemAt(index: Int): Item = Item(text)

  companion object {
    fun newPlainText(label: CharSequence?, text: CharSequence?): ClipData = ClipData(label, text)
  }
}

class ClipboardManager internal constructor() {
  fun setPrimaryClip(clip: ClipData) { UIPasteboard.generalPasteboard.string = clip.text?.toString() }
  val primaryClip: ClipData? get() = UIPasteboard.generalPasteboard.string?.let { ClipData.newPlainText("", it) }
  fun hasPrimaryClip(): Boolean = UIPasteboard.generalPasteboard.hasStrings
  fun clearPrimaryClip() { UIPasteboard.generalPasteboard.string = "" }

  companion object {
    internal val ios = ClipboardManager()
  }
}
