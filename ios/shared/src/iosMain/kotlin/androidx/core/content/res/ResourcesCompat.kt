package androidx.core.content.res

import android.content.Context
import android.graphics.Typeface

/** shimِ iOS ِ `ResourcesCompat.getFont(context, R.font.x)` — قلم از بستهٔ برنامه (‎Typeface.kt‎) */
object ResourcesCompat {
  fun getFont(context: Context, id: Int): Typeface? = Typeface.fromRes(id, if (id == ir.vil3ntec.tohid.R.font.vazirmatn_bold) Typeface.BOLD else Typeface.NORMAL)
}
