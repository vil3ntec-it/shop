package androidx.core.content.res

import android.content.Context
import android.graphics.Typeface
import ir.vil3ntec.tohid.R

object ResourcesCompat {
  /** قلمِ `res/font` برای کشیدنِ فاکتور با Java2D */
  fun getFont(context: Context, id: Int): Typeface? = runCatching {
    Thread.currentThread().contextClassLoader.getResourceAsStream(R.path(id))!!.use {
      Typeface.of(java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, it))
    }
  }.getOrNull()
}
