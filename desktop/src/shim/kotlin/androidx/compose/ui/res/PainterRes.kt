package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import ir.vil3ntec.tohid.R

/** `painterResource(R.drawable.x)` — از منابعِ همان پوشهٔ `res`ِ اندروید. */
@Composable
fun painterResource(id: Int): Painter = remember(id) {
  val path = R.path(id)
  val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
    ?: error("منبع پیدا نشد: $path")
  BitmapPainter(stream.use { org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap() })
}
