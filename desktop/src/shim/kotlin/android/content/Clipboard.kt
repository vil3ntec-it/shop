package android.content

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class ClipData private constructor(val label: CharSequence?, val text: CharSequence) {
  companion object {
    fun newPlainText(label: CharSequence?, text: CharSequence): ClipData = ClipData(label, text)
  }
}

/** کلیپ‌بوردِ سیستم */
class ClipboardManager {
  fun setPrimaryClip(clip: ClipData) {
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(clip.text.toString()), null) }
  }
}
