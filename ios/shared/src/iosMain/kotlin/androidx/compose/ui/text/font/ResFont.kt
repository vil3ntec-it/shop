package androidx.compose.ui.text.font

import ir.vil3ntec.tohid.R
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

/*
 *  `Font(R.font.x, weight)` ِ اندروید برای iOS (همان `ResFont.kt` ِ کامپیوتر): قلمِ Vazirmatn از
 *  بستهٔ برنامهٔ آیفون. نبودنِ قلم ⇐ خطای روشن (Xcode باید `res/font` را در Resources بگذارد).
 */
fun Font(resId: Int, weight: FontWeight = FontWeight.Normal, style: FontStyle = FontStyle.Normal): Font {
  val (name, ext) = R.file(resId)
  val path = NSBundle.mainBundle.pathForResource(name, ofType = ext) ?: error("قلم در بستهٔ برنامه نیست: $name.$ext")
  val data = NSData.dataWithContentsOfFile(path) ?: error("قلم خوانده نشد: $path")
  val bytes = ByteArray(data.length.toInt())
  bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
  return androidx.compose.ui.text.platform.Font("$name.$ext", bytes, weight, style)
}
