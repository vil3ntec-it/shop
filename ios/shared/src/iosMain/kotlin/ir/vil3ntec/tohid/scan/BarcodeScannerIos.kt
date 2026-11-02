package ir.vil3ntec.tohid.scan

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeCode128Code
import platform.AVFoundation.AVMetadataObjectTypeCode39Code
import platform.AVFoundation.AVMetadataObjectTypeEAN13Code
import platform.AVFoundation.AVMetadataObjectTypeEAN8Code
import platform.AVFoundation.AVMetadataObjectTypeITF14Code
import platform.AVFoundation.AVMetadataObjectTypeInterleaved2of5Code
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.AVMetadataObjectTypeUPCECode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UIView
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

/*
 *  جایگزینِ iOS ِ `scan/BarcodeScanner.kt` (همان API؛ کامپیوتر: ‎BarcodeScannerDesk‎) — اسکنِ دوربینِ آیفون با
 *  AVFoundation (`AVCaptureMetadataOutput`، خواننده‌ی بارکدِ خودِ iOS؛ بی کتابخانهٔ بیرونی).
 *
 *  همان قاعده‌های اندروید:
 *   - قالب‌ها همان `FORMATS` ِ ML Kit: EAN-13/8، UPC-A/E، Code 128/39، ITF؛ با `qr` QR هم، با `qrOnly` فقط QR.
 *   - UPC-A را iOS «EAN-13 با صفرِ آغازین» می‌دهد ⇐ همان ۱۲ رقمِ ML Kit (کالای ثبت‌شده با همان کد پیدا شود).
 *   - هر خوانش از همان سدِ مشترکِ `BarcodeGuard` می‌گذرد (رقمِ کنترلی، تکرارِ لازم، `isKnown`)؛ `qrOnly` بی سد.
 *   - `onStatus`: «آماده»، «اجازهٔ دوربین نیست» و «دوربین باز نشد» (خطا)؛ بارکدخوانِ بلوتوثی (HID) هم کنارش کار می‌کند.
 *  فوکوس/بزرگ‌نمایی/چراغِ خودکارِ اندروید (`FocusPilot`، `LightPilot`) این‌جا نیست؛ فوکوسِ پیوستهٔ خودِ iOS کافی است.
 *  کلیدِ `NSCameraUsageDescription` در ‎ios/iosApp/iosApp/Info.plist‎.
 */
@Composable
fun CameraScanner(
  onCode: (String) -> Unit,
  onStatus: (String, Boolean) -> Unit,
  modifier: Modifier = Modifier,
  isKnown: (String) -> Boolean = { false },
  qr: Boolean = false,
  qrOnly: Boolean = false,
) {
  val latestCode by rememberUpdatedState(onCode)
  val latestStatus by rememberUpdatedState(onStatus)
  val latestKnown by rememberUpdatedState(isKnown)
  val camera = remember(qr, qrOnly) { IosCamera(qr, qrOnly) }

  DisposableEffect(camera) {
    //  سدِ خوانشِ دروغین — یکی برای هر بار باز شدنِ دوربین (همان اندروید)
    val guard = BarcodeGuard(known = { code -> latestKnown(code) })
    camera.start(
      onRead = { value, kind -> if (qrOnly || guard.trust(value, kind)) latestCode(value) },
      onStatus = { text, error -> latestStatus(text, error) },
    )
    onDispose { camera.stop() }
  }

  UIKitView(factory = { camera.view }, modifier = modifier)
}

private const val READY_TEXT = "آماده اسکن — بارکد را جلوی دوربین بگیرید"
private const val NO_PERMISSION = "اجازهٔ دوربین داده نشده — از «تنظیمات» ِ آیفون، بخشِ همین برنامه، روشنش کنید"
private const val NO_CAMERA = "دوربین باز نشد: این دستگاه دوربین ندارد"

/** نشستِ دوربین + لایهٔ پیش‌نمایش؛ همه‌ی کارهای UIKit روی رشتهٔ اصلی، startRunning بیرون از آن (قاعدهٔ اپل) */
@OptIn(ExperimentalForeignApi::class)
private class IosCamera(private val qr: Boolean, private val qrOnly: Boolean) {
  private val session = AVCaptureSession()
  private val preview = AVCaptureVideoPreviewLayer(session = session).apply { videoGravity = AVLayerVideoGravityResizeAspectFill }
  val view: UIView = PreviewView(preview)
  private var delegate: Reader? = null
  private var configured = false
  private var stopped = false

  fun start(onRead: (String, CodeKind) -> Unit, onStatus: (String, Boolean) -> Unit) {
    stopped = false
    when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
      AVAuthorizationStatusAuthorized -> open(onRead, onStatus)
      AVAuthorizationStatusNotDetermined -> AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { ok ->
        dispatch_async(dispatch_get_main_queue()) {
          if (stopped) return@dispatch_async
          if (ok) open(onRead, onStatus) else onStatus(NO_PERMISSION, true)
        }
      }
      else -> onStatus(NO_PERMISSION, true)
    }
  }

  private fun open(onRead: (String, CodeKind) -> Unit, onStatus: (String, Boolean) -> Unit) {
    if (!configured) {
      val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
      val input = device?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, error = null) }
      if (input == null || !session.canAddInput(input)) {
        onStatus(NO_CAMERA, true)
        return
      }
      session.addInput(input)
      val output = AVCaptureMetadataOutput()
      if (!session.canAddOutput(output)) {
        onStatus(NO_CAMERA, true)
        return
      }
      session.addOutput(output)
      val reader = Reader(onRead)
      delegate = reader
      output.setMetadataObjectsDelegate(reader, queue = dispatch_get_main_queue())
      //  نوع‌ها پس از `addOutput` معلوم می‌شوند؛ فقط آن‌ها که این دستگاه دارد
      val wanted = when {
        qrOnly -> listOf(AVMetadataObjectTypeQRCode)
        qr -> FORMATS + AVMetadataObjectTypeQRCode
        else -> FORMATS
      }
      output.metadataObjectTypes = wanted.filter { it != null && it in output.availableMetadataObjectTypes }
      configured = true
    }
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
      if (!stopped && !session.running) session.startRunning()
    }
    onStatus(READY_TEXT, false)
  }

  fun stop() {
    stopped = true
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
      if (session.running) session.stopRunning()
    }
  }

  private class Reader(private val onRead: (String, CodeKind) -> Unit) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
    override fun captureOutput(output: AVCaptureOutput, didOutputMetadataObjects: List<*>, fromConnection: AVCaptureConnection) {
      //  چند بارکد در کادر ⇐ اندروید کدام‌یک را نمی‌داند و فریم را رد می‌کند (`Aim.bestOf`)؛ این‌جا هم
      val codes = didOutputMetadataObjects.filterIsInstance<AVMetadataMachineReadableCodeObject>()
      val read = codes.singleOrNull() ?: return
      val raw = read.stringValue ?: return
      val (value, kind) = iosScanValue(raw, read.type)
      onRead(value, kind)
    }
  }

  /** لایهٔ پیش‌نمایش همیشه هم‌اندازهٔ کادرِ Compose */
  private class PreviewView(private val layerOf: AVCaptureVideoPreviewLayer) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    init {
      backgroundColor = UIColor.blackColor
      clipsToBounds = true
      layer.addSublayer(layerOf)
    }

    override fun layoutSubviews() {
      super.layoutSubviews()
      CATransaction.begin()
      CATransaction.setDisableActions(true)
      layerOf.frame = bounds
      CATransaction.commit()
    }
  }

  companion object {
    val FORMATS = listOf(
      AVMetadataObjectTypeEAN13Code,
      AVMetadataObjectTypeEAN8Code,
      AVMetadataObjectTypeUPCECode,
      AVMetadataObjectTypeCode128Code,
      AVMetadataObjectTypeCode39Code,
      AVMetadataObjectTypeITF14Code,
      AVMetadataObjectTypeInterleaved2of5Code,
    )
  }
}

/**
 *  نوعِ iOS ⇐ همان `CodeKind` ِ سد، و UPC-A ِ iOS (EAN-13 با صفرِ آغازین) ⇐ ۱۲ رقمِ ML Kit.
 *  جدا و خالص، تا بی دوربین آزموده شود (‎iosTest/…/ScanValueIosTest‎).
 */
internal fun iosScanValue(raw: String, type: String?): Pair<String, CodeKind> = when (type) {
  AVMetadataObjectTypeEAN13Code -> (if (raw.length == 13 && raw.startsWith('0')) raw.substring(1) else raw) to CodeKind.CHECKED
  AVMetadataObjectTypeEAN8Code -> raw to CodeKind.CHECKED
  AVMetadataObjectTypeITF14Code, AVMetadataObjectTypeInterleaved2of5Code -> raw to CodeKind.ITF
  else -> raw to CodeKind.OTHER
}

/** همان قاعدهٔ اندروید: یک کد در یک بازهٔ کوتاه یک بار */
class ScanGate(private val windowMs: Long = 1200) {
  private var lastCode: String? = null
  private var lastAt = 0L

  fun accept(code: String, now: Long = ir.vil3ntec.tohid.ios.jvm.System.currentTimeMillis()): Boolean {
    if (code == lastCode && now - lastAt < windowMs) return false
    lastCode = code
    lastAt = now
    return true
  }

  fun reset() {
    lastCode = null
    lastAt = 0
  }
}

/** بازخوردِ اسکن با لرزشِ خودِ iOS (بوقِ اندروید ⇐ «درست» و «ناشناخته») */
object ScanFeedback {
  fun ok(context: Context) = UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
  fun unknown(context: Context) = UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeWarning)
  fun release() {}
}
