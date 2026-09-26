package androidx.compose.ui.text.font

import ir.vil3ntec.tohid.R

/** `Font(R.font.x, weight)`ِ اندروید — همان قلم‌های وزیرمتن از `res/font`. */
fun Font(resId: Int, weight: FontWeight = FontWeight.Normal, style: FontStyle = FontStyle.Normal): Font {
  val path = R.path(resId)
  val bytes = Thread.currentThread().contextClassLoader.getResourceAsStream(path)?.use { it.readBytes() }
    ?: error("قلم پیدا نشد: $path")
  return androidx.compose.ui.text.platform.Font(path, bytes, weight, style)
}
