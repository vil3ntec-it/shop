package ir.vil3ntec.tohid.pay.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import ir.vil3ntec.tohid.core.i18n.StrTillPay
import ir.vil3ntec.tohid.scan.WebcamDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.FileDialog
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 *  کامپیوتر: همان API ِ ‎ShopQrPicture.kt‎ ِ گوشی (فهرستِ `replaced` ِ ‎desktop/build.gradle.kts‎) — عکسِ QR ِ دکان با ImageIO
 *  (چرخشِ EXIF، شفافی روی سفید، ضلع ≤ ۱۰۲۴، JPEG ِ بی فراداده)، خواندنِ QR با همان ZXing ِ وب‌کم (`WebcamDecoder`) و پنجرهٔ
 *  فایلِ سیستم برای «انتخابِ عکسِ QR».
 */
object ShopQrPicture {
  const val MAX_INPUT_BYTES = 25 * 1024 * 1024
  const val MAX_SIDE = 1024
  const val CAN_READ = true
  private const val QUALITY = 0.92f
  private val QR = listOf(BarcodeFormat.QR_CODE)

  sealed interface Prep {
    class Ok(val jpeg: ByteArray, val payload: String) : Prep
    data object NotImage : Prep
    data object TooBig : Prep
    data object NoQr : Prep
  }

  suspend fun prepare(bytes: ByteArray): Prep = withContext(Dispatchers.Default) {
    if (bytes.size > MAX_INPUT_BYTES) return@withContext Prep.TooBig
    val src = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return@withContext Prep.NotImage
    if (src.width <= 0 || src.height <= 0) return@withContext Prep.NotImage
    if (src.width.toLong() * src.height > 40_000_000L) return@withContext Prep.TooBig
    val o = if (ir.vil3ntec.tohid.core.image.JpegMeta.isJpeg(bytes)) ir.vil3ntec.tohid.core.image.JpegMeta.orientation(bytes) else 1
    val swap = o in 5..8
    val (uw, uh) = if (swap) src.height to src.width else src.width to src.height
    val k = minOf(1.0, MAX_SIDE.toDouble() / maxOf(uw, uh))
    val w = maxOf(1, (uw * k).toInt())
    val h = maxOf(1, (uh * k).toInt())
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val g = out.createGraphics()
    try {
      g.color = Color.WHITE
      g.fillRect(0, 0, w, h)
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
      g.drawImage(src, exif(o, src.width, src.height, w.toDouble() / uw, h.toDouble() / uh), null)
    } finally {
      g.dispose()
    }
    val jpeg = encode(out)
    val payload = read(jpeg) ?: return@withContext Prep.NoQr
    Prep.Ok(jpeg, payload)
  }

  suspend fun read(bytes: ByteArray): String? = withContext(Dispatchers.Default) {
    val img = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return@withContext null
    WebcamDecoder.read(img, QR)?.text
  }

  fun bitmap(bytes: ByteArray): ImageBitmap? = runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

  /** «انتخابِ عکسِ QR»: پنجرهٔ فایلِ سیستم (فقط JPG/PNG) ⇒ بایت‌ها؛ لغو ⇒ هیچ */
  @Composable
  fun picker(onPicked: (ByteArray?) -> Unit): () -> Unit {
    val done = rememberUpdatedState(onPicked)
    return {
      val d = FileDialog(null as Frame?, StrTillPay.pickQrImage, FileDialog.LOAD).apply {
        setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg") } }
        //  ویندوز پالایهٔ نام را نمی‌پذیرد، الگوی «نامِ فایل» را چرا
        if (System.getProperty("os.name").orEmpty().lowercase().contains("win")) file = "*.png;*.jpg;*.jpeg"
        isVisible = true
      }
      val f = d.file?.let { File(d.directory.orEmpty(), it) }
      if (f != null) done.value(runCatching { f.takeIf { it.length() <= MAX_INPUT_BYTES }?.readBytes() }.getOrNull())
    }
  }

  /** چرخشِ EXIF (۱ تا ۸) و کوچک‌سازی در یک تبدیل */
  private fun exif(o: Int, w: Int, h: Int, sx: Double, sy: Double): AffineTransform = AffineTransform().apply {
    scale(sx, sy)
    when (o) {
      2 -> { translate(w.toDouble(), 0.0); scale(-1.0, 1.0) }
      3 -> { translate(w.toDouble(), h.toDouble()); rotate(Math.PI) }
      4 -> { translate(0.0, h.toDouble()); scale(1.0, -1.0) }
      5 -> { rotate(Math.PI / 2); scale(1.0, -1.0) }
      6 -> { translate(h.toDouble(), 0.0); rotate(Math.PI / 2) }
      7 -> { translate(h.toDouble(), w.toDouble()); rotate(-Math.PI / 2); scale(1.0, -1.0) }
      8 -> { translate(0.0, w.toDouble()); rotate(-Math.PI / 2) }
    }
  }

  private fun encode(img: BufferedImage): ByteArray {
    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    return ByteArrayOutputStream().use { bos ->
      ImageIO.createImageOutputStream(bos).use { ios ->
        writer.output = ios
        val p = writer.defaultWriteParam.apply { compressionMode = ImageWriteParam.MODE_EXPLICIT; compressionQuality = QUALITY }
        writer.write(null, IIOImage(img, null, null), p)
        writer.dispose()
      }
      bos.toByteArray()
    }
  }
}
