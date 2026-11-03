package ir.vil3ntec.tohid.scan

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import ir.vil3ntec.tohid.core.hw.WebcamScan
import org.openpnp.capture.CaptureDevice
import org.openpnp.capture.OpenPnpCapture
import java.awt.image.BufferedImage

/**
 *  HW-08 (§۶.۱۱) — وب‌کمِ کامپیوتر به‌عنوانِ بارکدخوان؛ جایگزینِ کامپیوتریِ `scan/WebcamPlatform.kt` (همان API).
 *
 *  ── چرا این دو کتابخانه ──────────────────────────────────────────────
 *  جاوا دوربین نمی‌شناسد. openpnp-capture (MIT) کتابخانهٔ بومیِ کوچکِ ویندوز (Media Foundation)، مک (AVFoundation) و
 *  لینوکس (V4L2) را در همان jar دارد و با JNA صدا می‌شود؛ رمزگشایی با ZXing (Apache-2.0، جاوای خالص) — هر دو بی
 *  اینترنت و بی سرویسِ بیرونی. نبودِ دوربین یا بالا نیامدنِ کتابخانهٔ بومی هرگز برنامه را نمی‌اندازد: فهرستِ تهی و
 *  دلیل، و گیرندهٔ بارکدخوانِ USB/بلوتوث سرِ جایش.
 *  ──────────────────────────────────────────────────────────────────
 */
object WebcamPlatform {
  val supported: Boolean get() = true

  fun cameras(): List<WebcamScan.Camera> = Webcams.devices().map(Webcams::cameraOf)
}

/** دسترسی به دوربین‌ها — یک زمینهٔ openpnp برای کلِ برنامه */
internal object Webcams {
  private val lock = Any()
  @Volatile private var context: OpenPnpCapture? = null

  /** چرا کتابخانهٔ بومی بالا نیامد (برای پیامِ کادرِ اسکن) */
  @Volatile var loadError: String? = null
    private set

  fun context(): OpenPnpCapture? = synchronized(lock) {
    context ?: runCatching { OpenPnpCapture() }
      .onFailure { loadError = it.message ?: it.javaClass.simpleName }
      .getOrNull()
      ?.also { context = it; loadError = null }
  }

  fun devices(): List<CaptureDevice> = runCatching { context()?.devices.orEmpty() }.getOrDefault(emptyList())

  fun cameraOf(d: CaptureDevice): WebcamScan.Camera =
    WebcamScan.Camera(id = d.uniqueId?.takeIf { it.isNotBlank() } ?: d.name.orEmpty(), name = d.name.orEmpty())
}

/**
 *  خواندنِ بارکد از یک فریم — همان قالب‌های دوربینِ گوشی (`FORMATS` ِ `scan/BarcodeScanner.kt`)، و QR فقط وقتی
 *  صفحه خواسته. نتیجه هنوز «پذیرفته» نیست: از `BarcodeGuard` رد می‌شود ([WebcamLoop]).
 */
internal object WebcamDecoder {
  data class Read(val text: String, val kind: CodeKind)

  private val SALE = listOf(
    BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
    BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.ITF,
  )

  fun formats(qr: Boolean, qrOnly: Boolean): List<BarcodeFormat> = when {
    qrOnly -> listOf(BarcodeFormat.QR_CODE)
    qr -> SALE + BarcodeFormat.QR_CODE
    else -> SALE
  }

  /** همان نگاشتِ `kindOf` ِ دوربینِ گوشی */
  fun kindOf(format: BarcodeFormat): CodeKind = when (format) {
    BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A -> CodeKind.CHECKED
    BarcodeFormat.ITF -> CodeKind.ITF
    else -> CodeKind.OTHER
  }

  fun read(image: BufferedImage, formats: List<BarcodeFormat>): Read? {
    val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to formats, DecodeHintType.TRY_HARDER to true)
    val w = image.width
    val h = image.height
    if (w <= 0 || h <= 0) return null
    //  RGBLuminanceSource ِ خودِ ZXing (بی بستهٔ javase): پیکسل‌ها یک‌جا، بی کپیِ دوباره
    val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, h, image.getRGB(0, 0, w, h, null, 0, w))))
    val result = try {
      MultiFormatReader().decode(bitmap, hints)
    } catch (_: ReaderException) {
      return null
    }
    val text = result.text?.trim().orEmpty()
    if (text.isEmpty() || text.any { it.isISOControl() }) return null
    return Read(text, kindOf(result.barcodeFormat))
  }
}

/**
 *  یک گام برای هر فریم: آهنگِ رمزگشایی (`WebcamScan.due`)، خواندن، و سدِ خوانشِ نصفه و تکرار (`BarcodeGuard` ِ گوشی)؛
 *  QR ِ تنها (`scan?kind=qr`) مثلِ گوشی بی سدِ بارکد. خالص از ساعت و دوربین تا آزمون بی سخت‌افزار بسنجدش.
 */
internal class WebcamLoop(
  private val qrOnly: Boolean,
  private val guard: BarcodeGuard,
  private val decode: (BufferedImage) -> WebcamDecoder.Read?,
) {
  private var lastAt = 0L

  /** کدِ پذیرفته از همین فریم، یا null */
  fun offer(frame: BufferedImage, now: Long): String? {
    if (!WebcamScan.due(now, lastAt)) return null
    lastAt = now
    val read = decode(frame) ?: return null
    return if (qrOnly || guard.trust(read.text, read.kind, now)) read.text else null
  }
}
