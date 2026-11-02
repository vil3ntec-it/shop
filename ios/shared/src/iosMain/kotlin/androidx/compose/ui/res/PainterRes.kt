package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import ir.vil3ntec.tohid.R
import java.net.toByteArray
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile

/** `painterResource(R.drawable.x)` ِ اندروید برای iOS (همان `PainterRes.kt` ِ کامپیوتر): PNG از بستهٔ برنامه */
@Composable
fun painterResource(id: Int): Painter = remember(id) {
  val (name, ext) = R.file(id)
  val path = NSBundle.mainBundle.pathForResource(name, ofType = ext) ?: error("تصویر در بستهٔ برنامه نیست: $name.$ext")
  val bytes = NSData.dataWithContentsOfFile(path)?.toByteArray() ?: error("تصویر خوانده نشد: $path")
  BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap())
}
