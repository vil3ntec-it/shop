package ir.vil3ntec.tohid.scan

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.util.Log
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner as MlKitScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 *  اسکنِ بارکد با دوربینِ خودِ اندروید.
 *
 *  چرا این‌طوری و نه مثل قبل:
 *    • موتورِ خواندن روی خودِ گوشی است (ML Kit با مدلِ داخلِ برنامه). نه
 *      اینترنت می‌خواهد نه کتابخانه‌ای که از اینترنت بار شود — دکان ممکن
 *      است نت نداشته باشد.
 *    • هر فریم **همیشه** بسته می‌شود، چه بارکد پیدا شود چه نه. باگی که
 *      گزارش شد («بعد از یک اسکن از کار می‌افتد») دقیقاً همین بود: فریمِ
 *      بسته‌نشده صف را پر می‌کند و دوربین دیگر فریمِ تازه نمی‌دهد.
 *    • فقط همان قالب‌های بارکدی خوانده می‌شود که نسخهٔ وب می‌خواند، تا
 *      کالایی که آنجا شناخته می‌شد اینجا هم شناخته شود.
 *    • تصویرِ زنده است و بس؛ هیچ‌جا عکس گرفته نمی‌شود.
 *
 *  ── درسِ گران: چرا نسخهٔ قبلی تصویر را **تارتر** کرد ────────────────
 *  در نسخهٔ قبل دو کار کردیم که روی کاغذ درست بود و در واقعیت غلط:
 *
 *   ۱. `CONTROL_AF_MODE = CONTINUOUS_PICTURE` را با `Camera2Interop`
 *      **به زور** روی درخواستِ دوربین نشاندیم. این تنظیم‌ها بر تنظیمِ
 *      خودِ CameraX اولویت دارند، پس از آن لحظه CameraX دیگر نمی‌توانست
 *      حالتِ فوکوس را عوض کند.
 *   ۲. کنارش `startFocusAndMetering` می‌فرستادیم.
 *
 *      و اینجا دامِ کار است: در حالتِ **پیوسته**، فرمانِ فوکوس یعنی
 *      «همین حالا **قفل** کن» — نه «برو دنبالِ تیزی». دوربین جست‌وجوی
 *      پیوسته‌اش را می‌برید و لنز را همان‌جا که بود قفل می‌کرد؛ اگر آن
 *      لحظه تار بود، تار قفل می‌شد. و ما هر ۲٫۵ ثانیه دوباره قفلش
 *      می‌کردیم. یعنی لنز بیشترِ وقت **قفلِ تار** بود. همان ده ثانیه‌ای
 *      که کاربر منتظر می‌ماند.
 *
 *  حالا هیچ حالتِ ۳A را زورچپان نمی‌کنیم؛ CameraX خودش فوکوسِ پیوسته را
 *  می‌خواهد و صاحبِ فوکوس می‌ماند. فرمانِ فوکوس هم کورکورانه فرستاده
 *  نمی‌شود.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ── جایش چه آمد: سه چیز که با هم کار می‌کنند ──────────────────────
 *
 *  **۱) فوکوس از رویِ حالِ واقعیِ دوربین، نه از روی حدس.**
 *  با `setSessionCaptureCallback` هر فریم `CONTROL_AF_STATE` خوانده
 *  می‌شود — یعنی خودِ دوربین می‌گوید تیز است، در حالِ جست‌وجو است، یا
 *  ناموفق مانده. تا وقتی جست‌وجو می‌کند دست به آن نمی‌زنیم (بریدنِ همان
 *  جست‌وجو بود که کار را کند می‌کرد) و فقط جایی فرمان می‌دهیم که خودش
 *  گفته «نشد». شرحِ کاملش سرِ `FocusPilot`.
 *
 *  **۲) بزرگ‌نماییِ خودکارِ ML Kit.**
 *  ریشهٔ آن تاریِ عکسِ گزارش‌شده این بود: بارکدِ ریز تا نصفِ کادر را
 *  نگیرد خوانده نمی‌شود، پس کاربر کالا را می‌چسباند به لنز — و آن‌جا
 *  **زیرِ کمترین فاصلهٔ فوکوسِ** لنز است. هیچ تنظیمی در دنیا آن فاصله را
 *  فوکوس نمی‌کند؛ فیزیکِ لنز است. راهِ درست این است که کالا در فاصلهٔ
 *  راحت بماند و **دوربین** نزدیک شود: ML Kit وقتی بارکدی را می‌بیند که
 *  برای خواندن کوچک است، خودش نسبتِ بزرگ‌نمایی پیشنهاد می‌کند و ما همان
 *  را روی دوربین می‌گذاریم. کالا از بیست‌سی سانتی خوانده می‌شود، جایی که
 *  لنز راحت فوکوس می‌کند.
 *
 *  **۳) زدنِ انگشت روی بارکد = فوکوسِ همان‌جا.**
 *  اگر باز هم جایی گیر کرد، کاربر روی بارکد در تصویر می‌زند و فوکوس و
 *  نورسنجی همان نقطه را می‌گیرد. اینجا فرمانِ فوکوس بجاست، چون خودِ آدم
 *  گفته کجا.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ── و دو دستهٔ دستی، چون «هر گوشی فرق دارد» ────────────────────────
 *  گزارشِ کاربر این بود که اسکنر روی هر گوشی و هر دوربین جورِ دیگری
 *  رفتار می‌کند. درست هم هست: کمترین فاصلهٔ فوکوس، سقفِ بزرگ‌نمایی و
 *  حساسیتِ لنز از گوشی تا گوشی فرق دارد و هیچ تنظیمِ خودکاری همهٔ آن‌ها
 *  را نمی‌پوشاند. پس دو دسته گذاشته شده که همه‌جا یکسان کار می‌کنند:
 *
 *   • **دو انگشت** بزرگ‌نمایی را دستِ خودِ فروشنده می‌دهد. کالا در
 *     فاصلهٔ راحت می‌ماند و کادر نزدیک می‌شود — همان کاری که بزرگ‌نماییِ
 *     خودکار می‌کند، ولی این‌بار به اندازه‌ای که خودِ آدم می‌بیند.
 *   • **چراغ** تاریکی را می‌بندد. نورِ کم دو جور ضربه می‌زند: نوردهی
 *     بلند می‌شود و تکانِ دست تصویر را تار می‌کند، و کنتراستِ خط‌ها کم
 *     می‌شود و نیمی از کد خوانده می‌شود و نیمِ دیگر نه.
 *
 *  تا دوازده ثانیه پس از دو انگشت، بزرگ‌نماییِ خودکار دست به آن نمی‌زند؛
 *  وگرنه کاربر نزدیک می‌کند و برنامه همان دم برش می‌گرداند.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ── و آخر: هر چه خوانده شد، خوانده نیست ────────────────────────────
 *  خوانشِ هر فریم از `BarcodeGuard` رد می‌شود. آنجاست که خوانشِ نصفه و
 *  کدِ تصادفی گرفته می‌شود — با ساختارِ خودِ قالب و با تکرار. قاعده‌اش
 *  آنجا نوشته شده؛ همین‌قدر بدانید که **قالبِ** خوانش هم به سد داده
 *  می‌شود، چون ITF جای دیگری می‌ایستد تا EAN.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  و دو تنظیمِ بی‌خطر که تأخیر را کم می‌کنند: لرزش‌گیرِ ویدیو خاموش (چند
 *  فریم تأخیر، و برای اسکن بی‌فایده) و نویزگیر و لبه‌تیزکن روی `FAST`.
 *  این‌ها کارِ ۳A را دست نمی‌گیرند، فقط خطِ لوله را سبک می‌کنند.
 *
 *  `implementationMode = COMPATIBLE` عمداً سرِ جایش است: خطِ اسکن و نوارِ
 *  وضعیت با کامپوز **روی** همین نما کشیده می‌شوند و `PERFORMANCE` که از
 *  `SurfaceView` است آن‌ها را می‌خورد.
 */

/** همان فهرستِ SUPPORTED_BARCODE_FORMATS نسخهٔ وب */
private const val FORMATS = Barcode.FORMAT_EAN_13 or
  Barcode.FORMAT_EAN_8 or
  Barcode.FORMAT_UPC_A or
  Barcode.FORMAT_UPC_E or
  Barcode.FORMAT_CODE_128 or
  Barcode.FORMAT_CODE_39 or
  Barcode.FORMAT_ITF

/**
 *  نما درشت‌تر از فریمِ تحلیل است، و این عمدی است.
 *
 *  نما را چشمِ آدم می‌بیند — روی صفحهٔ بزرگِ تبلت، ۷۲۰ خط کشیده می‌شود و
 *  نرم و بی‌کیفیت به نظر می‌آید. فریمِ تحلیل را ML Kit می‌خواند و ۱۲۸۰×۷۲۰
 *  برایش بس است؛ درشت‌تر کردنش فقط هر فریم را کندتر می‌کند.
 */
private val PREVIEW_FRAME = Size(1920, 1080)
private val ANALYSIS_FRAME = Size(1280, 720)

/** ناحیهٔ فوکوسِ خودکار: ۲۵٪ میانهٔ کادر — همان‌جا که خطِ اسکن است */
private const val CENTER_REGION = 0.25f

/** مهلتِ خودرهاییِ فوکوسِ خودکار و فوکوسِ با انگشت، به ثانیه */
private const val AUTO_HOLD_SEC = 2L
private const val TAP_HOLD_SEC = 4L

/** کمترین فاصلهٔ دو فرمانِ فوکوس، به میلی‌ثانیه */
private const val REFOCUS_GAP_MS = 1200L

/** بیش از این‌قدر فرمانِ ناموفق پشتِ سرِ هم، یعنی مشکل جای دیگری است */
private const val MAX_TRIES = 4

/** بزرگ‌نمایی پس از این‌قدر بی‌خبری به یک برمی‌گردد */
private const val ZOOM_RESET_MS = 3500L

/** فاصلهٔ کمینهٔ دو تغییرِ بزرگ‌نمایی — تا دوربین تلوتلو نخورد */
private const val ZOOM_GAP_MS = 700L

/** پس از برگشتن از ماکرو، این‌قدر دوباره سراغش نمی‌رویم */
private const val MACRO_COOLDOWN_MS = 6_000L

/**
 *  پس از بزرگ‌نماییِ دستی (دو انگشت)، این‌قدر دستِ خودکار به آن نمی‌خورد.
 *
 *  وگرنه کاربر نزدیک می‌کند و نیم‌ثانیه بعد، خودکار برش می‌گرداند — و
 *  حس می‌کند برنامه با او لج کرده.
 */
private const val MANUAL_ZOOM_HOLD_MS = 12_000L

/** متنِ حالتِ عادی — از دو جا نوشته می‌شود، پس یک جا تعریف شده */
private const val READY_TEXT = "آماده اسکن — بارکد را جلوی دوربین بگیرید"

/**
 *  @param isKnown آیا این کد در فهرستِ کالاهای دکان هست. سد از همین
 *    می‌فهمد کجا سخت بگیرد و کجا نه — شرحش سرِ `BarcodeGuard`. پیش‌فرضش
 *    «نمی‌دانم» است، پس صفحه‌ای که فهرست ندارد هم کار می‌کند.
 */
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraScanner(
  onCode: (String) -> Unit,
  onStatus: (String, Boolean) -> Unit,
  modifier: Modifier = Modifier,
  isKnown: (String) -> Boolean = { false },
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  // مقدارِ تازهٔ onCode بدونِ بستنِ دوباره‌ی دوربین
  val latestCode by rememberUpdatedState(onCode)
  val latestStatus by rememberUpdatedState(onStatus)
  val latestKnown by rememberUpdatedState(isKnown)

  //  چراغ فقط وقتی نشان داده می‌شود که این دوربین داشته باشد؛ دکمه‌ای
  //  که کاری نمی‌کند، بدتر از نبودنش است
  var flashReady by remember { mutableStateOf(false) }
  var flashOn by remember { mutableStateOf(false) }

  val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
  val previewView = remember {
    PreviewView(context).apply {
      scaleType = PreviewView.ScaleType.FILL_CENTER
      implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    }
  }
  //  ادارهٔ فوکوس و بزرگ‌نمایی. بیرونِ DisposableEffect ساخته می‌شود تا
  //  زدنِ انگشت روی تصویر هم به همین یکی برسد.
  val pilot = remember { FocusPilot() }

  DisposableEffect(Unit) {
    var provider: ProcessCameraProvider? = null
    var scanner: MlKitScanner? = null

    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({
      val cameraProvider = runCatching { future.get() }.getOrNull()
      if (cameraProvider == null) {
        latestStatus("دوربین در دسترس نیست", true)
        return@addListener
      }
      provider = cameraProvider

      val previewBuilder = Preview.Builder().setResolutionSelector(sizeWanted(PREVIEW_FRAME))

      /*
       *  اینجا فقط دو چیز به دوربین گفته می‌شود و هیچ‌کدام ۳A را دست
       *  نمی‌گیرد. درسِ نسخهٔ قبل بالاتر نوشته شده: هر چیزی که با
       *  Camera2Interop گفته شود بر CameraX اولویت دارد، پس حالتِ فوکوس
       *  را اینجا نمی‌نویسیم — وگرنه فرمانِ فوکوس تبدیل به «قفلِ تار»
       *  می‌شود.
       */
      Camera2Interop.Extender(previewBuilder)
        //  لرزش‌گیر چند فریم تصویر را عقب می‌اندازد؛ برای اسکن به کار
        //  نمی‌آید و فقط تأخیر است
        .setCaptureRequestOption(
          CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
          CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
        )
        //  کیفیت را برای سرعت می‌دهیم: بارکد خط است، نه پرتره
        .setCaptureRequestOption(
          CaptureRequest.NOISE_REDUCTION_MODE,
          CameraMetadata.NOISE_REDUCTION_MODE_FAST,
        )
        .setCaptureRequestOption(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_FAST)
        //  و اینجا حالِ واقعیِ فوکوس، فریم به فریم، از زبانِ خودِ دوربین
        .setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
          override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult,
          ) {
            pilot.onFocusState(result.get(CaptureResult.CONTROL_AF_STATE))
          }
        })

      val preview = previewBuilder.build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

      val analysis = ImageAnalysis.Builder()
        // فقط تازه‌ترین فریم؛ فریمِ کهنه به درد اسکن نمی‌خورد و صف را پر می‌کند
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setResolutionSelector(sizeWanted(ANALYSIS_FRAME))
        .build()

      runCatching {
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
          lifecycleOwner,
          CameraSelector.DEFAULT_BACK_CAMERA,
          preview,
          analysis,
        )
      }.onSuccess { camera ->
        /*
         *  سقفِ بزرگ‌نمایی را باید از خودِ همین دوربین پرسید — هم ML Kit
         *  بیش از آن پیشنهاد نمی‌دهد، هم دو انگشتِ کاربر نباید از آن
         *  بگذرد.
         */
        val maxZoom = camera.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f

        pilot.attach(
          camera.cameraControl,
          macroCapable = macroCapable(camera.cameraInfo),
          maxZoom = maxZoom,
        ) { text -> latestStatus(text, false) }

        //  چراغ: اگر این دوربین ندارد، دکمه هم نباید باشد
        flashReady = runCatching { camera.cameraInfo.hasFlashUnit() }.getOrDefault(false)
        flashOn = false

        val client = buildScanner(maxZoom) { wanted -> pilot.zoomTo(wanted) }
        scanner = client
        //  سدِ خوانشِ دروغین. یکی برای هر بار باز شدنِ دوربین، چون
        //  شمارشِ تکرارش مالِ همین نشست است. فهرستِ کالاها را از راهِ
        //  `latestKnown` می‌پرسد تا با هر تغییرِ کالاها تازه بماند.
        val guard = BarcodeGuard(known = { code -> latestKnown(code) })

        analysis.setAnalyzer(executor) { proxy ->
          val media = proxy.image
          if (media == null) {
            proxy.close()
            return@setAnalyzer
          }
          val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
          client.process(image)
            .addOnSuccessListener { codes ->
              //  خودِ بارکد نگه داشته می‌شود نه فقط متنش: **قالبِ** خوانش
              //  مهم‌ترین سرنخِ سد است — ITF جای دیگری می‌ایستد تا EAN
              val read = codes.firstOrNull { !it.rawValue.isNullOrBlank() }
              val value = read?.rawValue
              if (value == null) {
                pilot.nothingRead()
              } else {
                //  بارکد دیده شد، پس فوکوس سرِ جایش است — چه این خوانش
                //  پذیرفته شود چه نه
                pilot.sawBarcode()
                if (guard.trust(value, kindOf(read.format))) latestCode(value)
              }
            }
            .addOnFailureListener { Log.d("Tohid", "خواندن فریم ناموفق", it) }
            // هرچه پیش بیاید فریم بسته می‌شود؛ وگرنه اسکن بعدی هرگز نمی‌آید
            .addOnCompleteListener { proxy.close() }
        }

        latestStatus(READY_TEXT, false)
      }.onFailure {
        latestStatus("دوربین باز نشد: ${it.message ?: "دلیل نامعلوم"}", true)
      }
    }, ContextCompat.getMainExecutor(context))

    onDispose {
      runCatching { provider?.unbindAll() }
      runCatching { scanner?.close() }
      pilot.detach()
      executor.shutdown()
    }
  }

  /*
   *  دو حرکتِ دست روی تصویر — و هیچ‌کدام اسکرولِ صفحه را نمی‌خورد.
   *
   *  **زدن** فوکوس را همان‌جا می‌برد. نقطه با
   *  `previewView.meteringPointFactory` ساخته می‌شود نه دستی: همان است
   *  که چرخشِ گوشی و بریدگیِ `FILL_CENTER` را حساب می‌کند — یعنی جایی که
   *  کاربر زده، همان جایی است که دوربین فوکوس می‌کند.
   *
   *  **دو انگشت** بزرگ‌نمایی را دستِ خودِ کاربر می‌دهد. این مهم‌ترین
   *  چیزی است که به گزارشِ «هر گوشی فرق دارد» جواب می‌دهد: بزرگ‌نماییِ
   *  خودکار از هر لنزی به اندازهٔ خودش برمی‌آید، ولی دو انگشتِ فروشنده
   *  همه‌جا یکسان کار می‌کند. و راهِ درستِ خواندنِ بارکدِ ریز همین است —
   *  **دوربین** نزدیک شود، نه کالا؛ کالا که به لنز بچسبد، زیرِ کمترین
   *  فاصلهٔ فوکوس می‌رود و هیچ‌وقت تیز نمی‌شود.
   */
  Box(
    modifier
      .pointerInput(Unit) {
        detectTapGestures { at ->
          pilot.focusOnTap(previewView, at.x, at.y)
        }
      }
      .pointerInput(Unit) {
        detectPinch { factor -> pilot.pinch(factor) }
      }
  ) {
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

    /*
     *  چراغِ دوربین.
     *
     *  نورِ کم دو جور به اسکن ضربه می‌زند و هر دو در گزارشِ کاربر بود:
     *  دوربین برای جبرانِ تاریکی، مدتِ نوردهی را بلند می‌کند و کوچک‌ترین
     *  تکانِ دست تصویر را **تار** می‌کند؛ و کنتراستِ خط‌های بارکد کم
     *  می‌شود، پس نیمی از کد خوانده می‌شود و نیمِ دیگرش نه. چراغ هر دو
     *  را می‌بندد.
     */
    if (flashReady) {
      IconButton(
        onClick = {
          flashOn = !flashOn
          pilot.torch(flashOn)
        },
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(8.dp)
          .size(38.dp)
          .clip(CircleShape)
          .background(Color.Black.copy(alpha = 0.45f)),
      ) {
        Icon(
          if (flashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
          contentDescription = if (flashOn) "خاموش کردن چراغ" else "روشن کردن چراغ",
          tint = if (flashOn) Color(0xFFFFD54F) else Color.White,
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}

/**
 *  بزرگ‌نمایی با دو انگشت — و فقط با دو انگشت.
 *
 *  `detectTransformGestures`ِ آماده اینجا به کار نمی‌آید: آن، کشیدنِ
 *  **تک‌انگشتی** را هم می‌گیرد و مصرف می‌کند، و این نما وسطِ یک فهرستِ
 *  اسکرول‌شونده است — یعنی صفحهٔ فروش دیگر بالا و پایین نمی‌رفت.
 *
 *  اینجا تا وقتی دو انگشت روی صفحه نیامده، هیچ رویدادی مصرف نمی‌شود؛
 *  پس زدن و اسکرول دست‌نخورده می‌مانند.
 */
private suspend fun PointerInputScope.detectPinch(onZoom: (Float) -> Unit) {
  awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false)
    do {
      val event = awaitPointerEvent()
      if (event.changes.size >= 2) {
        val factor = event.calculateZoom()
        if (factor != 1f) {
          onZoom(factor)
          event.changes.forEach { it.consume() }
        }
      }
    } while (event.changes.any { it.pressed })
  }
}

/**
 *  قالبِ ML Kit → خانواده‌ای که سد می‌فهمد.
 *
 *  چرا ترجمه لازم است: سد باید روی JVM و بدونِ دوربین سنجیده شود، پس
 *  نباید به کتابخانهٔ ML Kit بند باشد. تنها جایی که این دو به هم
 *  می‌رسند همین یک تابع است.
 */
private fun kindOf(format: Int): CodeKind = when (format) {
  Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A -> CodeKind.CHECKED
  Barcode.FORMAT_ITF -> CodeKind.ITF
  else -> CodeKind.OTHER
}

/**
 *  اندازهٔ خواسته‌شده، با راهِ فرار.
 *
 *  `FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER` یعنی اگر گوشی این اندازه را
 *  نداشت، نزدیک‌ترینِ بالاتر و اگر نبود نزدیک‌ترینِ پایین‌تر — نه اینکه
 *  دست خالی برگردد.
 */
private fun sizeWanted(size: Size): ResolutionSelector =
  ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(
      ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
    )
    .build()

/**
 *  خوانندهٔ بارکد، با بزرگ‌نماییِ پیشنهادیِ خودش.
 *
 *  `setZoomSuggestionOptions` قابلیتِ خودِ ML Kit است: وقتی بارکدی در
 *  تصویر هست ولی برای رمزگشایی کوچک است، به‌جای «نخواندم»، می‌گوید «این‌قدر
 *  نزدیک شو». ما همان را روی دوربین می‌گذاریم و فریمِ بعد خوانده می‌شود.
 *
 *  چرا این مهم‌تر از هر تنظیمِ فوکوس است: بدونِ آن، کاربر باید کالا را
 *  بچسباند به لنز تا بارکد بزرگ شود — و آن فاصله زیرِ کمترین فاصلهٔ فوکوس
 *  است و **هیچ‌وقت** تیز نمی‌شود.
 */
private fun buildScanner(maxZoom: Float, onZoom: (Float) -> Boolean): MlKitScanner {
  val builder = BarcodeScannerOptions.Builder().setBarcodeFormats(FORMATS)
  //  اگر دوربینِ گوشی بزرگ‌نمایی ندارد، این قابلیت هم معنایی ندارد
  if (maxZoom > 1.05f) {
    val callback = ZoomSuggestionOptions.ZoomCallback { wanted -> onZoom(wanted) }
    runCatching {
      builder.setZoomSuggestionOptions(
        ZoomSuggestionOptions.Builder(callback)
          .setMaxSupportedZoomRatio(maxZoom)
          .build()
      )
    }
  }
  return BarcodeScanning.getClient(builder.build())
}

/**
 *  آیا این دوربین حالتِ فوکوسِ **ماکرو** دارد.
 *
 *  ماکرو یعنی «نزدیک را ببین». روی گوشی‌هایی که دارند، همان چیزی است که
 *  بارکدِ چسبیده به لنز را ممکن می‌کند؛ روی آن‌هایی که ندارند، فرستادنِ
 *  این فرمان بی‌فایده است و نباید فرستاده شود. پس از خودِ دوربین پرسیده
 *  می‌شود، نه فرض.
 */
@SuppressLint("UnsafeOptInUsageError")
private fun macroCapable(info: CameraInfo): Boolean = runCatching {
  Camera2CameraInfo.from(info)
    .getCameraCharacteristic(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
    ?.contains(CameraMetadata.CONTROL_AF_MODE_MACRO) == true
}.getOrDefault(false)

/**
 *  کِی فوکوس بدهیم، کِی ماکرو برویم، کِی حرف بزنیم، و کِی دست نگه داریم.
 *
 *  ── قاعده ─────────────────────────────────────────────────────────
 *  فرمانِ فوکوس ارزان نیست و — مهم‌تر — در حالتِ پیوسته یعنی «قفل کن».
 *  اگر بی‌حساب فرستاده شود، جست‌وجوی خودِ دوربین بریده می‌شود و لنز تار
 *  قفل می‌ماند؛ همان چیزی که یک نسخه پیش کار را کندتر کرد.
 *
 *  پس تصمیم را از خودِ دوربین می‌پرسیم. `CONTROL_AF_STATE` هر فریم
 *  می‌گوید کجای کار است:
 *
 *   • **در حالِ جست‌وجو** (`PASSIVE_SCAN` / `ACTIVE_SCAN`) → دست نزن.
 *   • **تیز** (`PASSIVE_FOCUSED` / `FOCUSED_LOCKED`) → کاری لازم نیست.
 *   • **خوابیده یا ناموفق** (`INACTIVE` / `PASSIVE_UNFOCUSED` /
 *     `NOT_FOCUSED_LOCKED`) → فوکوس روی ناحیهٔ مرکزی، با فاصلهٔ کمینهٔ
 *     ۱٫۲ ثانیه از فرمانِ قبلی.
 *   • **هیچ** (`null`) → این دوربین فوکوسِ متحرک ندارد؛ فرمان بی‌فایده.
 *
 *  ── و اگر چهار بار نشد: نردبانِ سه‌پله ─────────────────────────────
 *  چهار تلاشِ ناموفق پشتِ سرِ هم یعنی «چیزی این‌جا با فوکوسِ معمولی درست
 *  نمی‌شود». گزارشِ کاربر هم همین بود: «هرچه نزدیک می‌برم فوکوس نمی‌شود».
 *  علتش تقریباً همیشه یکی است — کالا نزدیک‌تر از **کمترین فاصلهٔ فوکوسِ**
 *  لنز است. آن‌جا:
 *
 *   ۱. اگر دوربین حالتِ **ماکرو** دارد، می‌رویم روی ماکرو و یک فرمانِ
 *      فوکوس می‌فرستیم. ماکرو حالتِ تک‌ضرب است، پس فرمان این‌جا سرِ جای
 *      خودش است — برخلافِ حالتِ پیوسته که فرمان یعنی قفل.
 *   ۲. اگر ماکرو هم نشد، برمی‌گردیم روی پیوسته و **به کاربر می‌گوییم**
 *      کالا را عقب‌تر بگیرد. این مهم‌ترین پلهٔ نردبان است: تا دیروز
 *      تصویر بی‌صدا تار می‌ماند و آدم ده ثانیه منتظر چیزی می‌شد که
 *      هیچ‌وقت نمی‌آمد. حالا در همان یکی‌دو ثانیه می‌فهمد باید چه کند.
 *   ۳. و شش ثانیه سراغِ ماکرو نمی‌رویم، وگرنه دوربین بین دو حالت
 *      تلوتلو می‌خورد.
 *
 *  با اولین بارکدی که دیده شود، همه‌ی این‌ها از نو صفر می‌شوند و متنِ
 *  عادی برمی‌گردد.
 *
 *  مهلتِ خودرهایی روی دو ثانیه است، پس فوکوسِ نقطه‌ای **قفل نمی‌ماند**.
 *  قفل کردنش (`disableAutoCancel`) همان اشکالی است که کالای بعدی را تار
 *  می‌کند.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  همه‌ی متدها از رشته‌های مختلف صدا زده می‌شوند — فوکوس از رشتهٔ دوربین،
 *  خواندن از رشتهٔ تحلیل، انگشت از رشتهٔ اصلی — پس هر چه حالت دارد
 *  `@Volatile` است و هیچ‌کدام کارِ سنگینی نمی‌کنند.
 */
private class FocusPilot {

  @Volatile private var control: CameraControl? = null
  @Volatile private var hint: ((String) -> Unit)? = null

  @Volatile private var macroReady = false
  @Volatile private var macroOn = false
  @Volatile private var macroLeftAt = 0L

  @Volatile private var lastFocusAt = 0L
  @Volatile private var lastReadAt = 0L
  @Volatile private var lastZoomAt = 0L
  @Volatile private var zoom = 1f
  @Volatile private var maxZoom = 1f
  @Volatile private var tries = 0
  @Volatile private var told = false

  /** تا این لحظه، بزرگ‌نمایی مالِ کاربر است و خودکار به آن دست نمی‌زند */
  @Volatile private var manualUntil = 0L

  fun attach(
    cameraControl: CameraControl,
    macroCapable: Boolean,
    maxZoom: Float,
    onHint: (String) -> Unit,
  ) {
    control = cameraControl
    hint = onHint
    macroReady = macroCapable
    macroOn = false
    macroLeftAt = 0L
    lastReadAt = 0L
    tries = 0
    told = false
    zoom = 1f
    this.maxZoom = if (maxZoom > 1f) maxZoom else 1f
    manualUntil = 0L
    focusCenter(cameraControl)
  }

  fun detach() {
    //  حالتِ ماکرو مالِ همین نشست بود؛ با خودش می‌رود
    if (macroOn) runCatching { setAfMode(null) }
    //  و چراغ هم — روشن ماندنش بعد از بسته شدنِ صفحه، باتری می‌خورد
    runCatching { control?.enableTorch(false) }
    control = null
    hint = null
  }

  /** چراغِ دوربین — دستِ کاربر است، نه خودکار */
  fun torch(on: Boolean) {
    runCatching { control?.enableTorch(on) }
  }

  /** حالِ فوکوس، از زبانِ خودِ دوربین — هر فریم یک بار */
  fun onFocusState(state: Int?) {
    when (state) {
      null -> return
      CameraMetadata.CONTROL_AF_STATE_PASSIVE_SCAN,
      CameraMetadata.CONTROL_AF_STATE_ACTIVE_SCAN,
      -> return
      CameraMetadata.CONTROL_AF_STATE_PASSIVE_FOCUSED,
      CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED,
      -> {
        tries = 0
        clearHint()
        return
      }
      else -> nudge()
    }
  }

  /** بارکد دیده شد — فوکوس همان است که باید باشد */
  fun sawBarcode() {
    lastReadAt = System.currentTimeMillis()
    tries = 0
    clearHint()
  }

  /**
   *  فریم آمد و بارکدی دیده نشد.
   *
   *  اینجا فقط بزرگ‌نمایی برمی‌گردد سرِ جایش: اگر ML Kit برای یک بارکدِ
   *  ریز نزدیک کرده بود و آن کالا رفته، ماندنِ بزرگ‌نمایی یعنی کالای
   *  بعدی از کادر بیرون می‌افتد. تصمیمِ فوکوس از `onFocusState` می‌آید.
   */
  fun nothingRead() {
    if (zoom <= 1.01f) return
    val now = System.currentTimeMillis()
    //  بزرگ‌نمایی‌ای که خودِ کاربر گذاشته، برگردانده نمی‌شود
    if (now < manualUntil) return
    if (now - lastReadAt < ZOOM_RESET_MS) return
    if (now - lastZoomAt < ZOOM_GAP_MS) return
    lastZoomAt = now
    zoom = 1f
    runCatching { control?.setZoomRatio(1f) }
  }

  /** پیشنهادِ بزرگ‌نماییِ ML Kit — درست است اگر روی دوربین نشست */
  fun zoomTo(wanted: Float): Boolean {
    val cameraControl = control ?: return false
    val now = System.currentTimeMillis()
    //  تا وقتی کاربر خودش تنظیم کرده، پیشنهادِ خودکار پس زده می‌شود
    if (now < manualUntil) return false
    if (now - lastZoomAt < ZOOM_GAP_MS) return false
    lastZoomAt = now
    zoom = wanted
    //  با نزدیک شدنِ کادر، صحنه عوض می‌شود؛ سهمیهٔ فوکوس هم از نو
    tries = 0
    return runCatching { cameraControl.setZoomRatio(wanted) }.isSuccess
  }

  /**
   *  دو انگشتِ کاربر.
   *
   *  `factor` نسبتِ همین لحظه است نه نسبتِ کل، پس در بزرگ‌نماییِ حالا
   *  ضرب می‌شود. بینِ یک و سقفِ خودِ دوربین بریده می‌شود — و از این لحظه
   *  تا `MANUAL_ZOOM_HOLD_MS` بعد، دستِ خودکار به آن نمی‌رسد.
   */
  fun pinch(factor: Float) {
    val cameraControl = control ?: return
    if (factor <= 0f || !factor.isFinite()) return
    val wanted = (zoom * factor).coerceIn(1f, maxZoom)
    manualUntil = System.currentTimeMillis() + MANUAL_ZOOM_HOLD_MS
    if (kotlin.math.abs(wanted - zoom) < 0.01f) return
    zoom = wanted
    //  صحنه عوض شد؛ سهمیهٔ فوکوس از نو
    tries = 0
    runCatching { cameraControl.setZoomRatio(wanted) }
  }

  /** انگشتِ کاربر روی تصویر: فوکوس و نورسنجی همان نقطه */
  fun focusOnTap(view: PreviewView, x: Float, y: Float) {
    val cameraControl = control ?: return
    lastFocusAt = System.currentTimeMillis()
    tries = 0
    clearHint()
    runCatching {
      val point = view.meteringPointFactory.createPoint(x, y)
      val flags = FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
      val action = FocusMeteringAction.Builder(point, flags)
        .setAutoCancelDuration(TAP_HOLD_SEC, TimeUnit.SECONDS)
        .build()
      cameraControl.startFocusAndMetering(action)
    }
  }

  private fun nudge() {
    val cameraControl = control ?: return
    val now = System.currentTimeMillis()
    if (now - lastFocusAt < REFOCUS_GAP_MS) return
    if (tries >= MAX_TRIES) {
      escalate(now)
      return
    }
    tries++
    lastFocusAt = now
    focusCenter(cameraControl)
  }

  /** پلهٔ بعدیِ نردبان: ماکرو، و اگر آن هم نشد، حرف زدن */
  private fun escalate(now: Long) {
    val cameraControl = control ?: return
    if (!macroOn) {
      if (!macroReady || now - macroLeftAt < MACRO_COOLDOWN_MS) {
        tellTooClose()
        return
      }
      macroOn = true
      tries = 0
      lastFocusAt = now
      setAfMode(CameraMetadata.CONTROL_AF_MODE_MACRO)
      focusCenter(cameraControl)
      return
    }
    //  ماکرو هم نتوانست: برگرد سرِ پیوسته و بگو چه کند
    macroOn = false
    macroLeftAt = now
    lastFocusAt = now
    setAfMode(null)
    tellTooClose()
  }

  private fun tellTooClose() {
    if (told) return
    told = true
    hint?.invoke("فوکوس نمی‌شود — کالا را کمی عقب‌تر، حدود ۲۰ سانتی، بگیرید")
  }

  private fun clearHint() {
    if (!told) return
    told = false
    hint?.invoke(READY_TEXT)
  }

  /**
   *  حالتِ فوکوس را در **زمانِ اجرا** عوض می‌کند؛ `null` یعنی دست بردار
   *  و اختیار را به CameraX برگردان.
   *
   *  `setCaptureRequestOptions` هر چه پیش‌تر از این راه گفته شده بود پاک
   *  می‌کند، و ما از این راه چیزی جز همین یک تنظیم نمی‌گوییم — پس
   *  سازندهٔ خالی یعنی «هیچ».
   */
  @SuppressLint("UnsafeOptInUsageError")
  private fun setAfMode(mode: Int?) {
    val cameraControl = control ?: return
    runCatching {
      val options = CaptureRequestOptions.Builder()
      if (mode != null) options.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, mode)
      Camera2CameraControl.from(cameraControl).setCaptureRequestOptions(options.build())
    }
  }

  private fun focusCenter(cameraControl: CameraControl) {
    lastFocusAt = System.currentTimeMillis()
    runCatching {
      /*
       *  نقطه بر حسبِ کسری از خودِ فریم داده می‌شود، پس نه به اندازهٔ نما
       *  بند است و نه به چرخشِ گوشی — و از رشتهٔ دوربین هم می‌شود ساختش،
       *  برخلافِ `meteringPointFactory` که مالِ رشتهٔ اصلی است. میانهٔ کادر
       *  در هر چرخشی همان میانه است.
       */
      val center = SurfaceOrientedMeteringPointFactory(1f, 1f)
        .createPoint(0.5f, 0.5f, CENTER_REGION)
      //  نور را هم همان‌جا بسنج: بارکدِ سفید زیرِ نورِ تندِ دکان می‌سوزد
      //  و ناخوانا می‌شود — و همین سنجشِ ناحیه‌ای است که وقتی لامپ سرِ
      //  نصفِ بارکد افتاده، آن نصفِ دیگر را قابلِ خواندن نگه می‌دارد
      val flags = FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
      val action = FocusMeteringAction.Builder(center, flags)
        .setAutoCancelDuration(AUTO_HOLD_SEC, TimeUnit.SECONDS)
        .build()
      cameraControl.startFocusAndMetering(action)
    }
  }
}

/**
 *  جلوگیری از اسکنِ تکراری.
 *
 *  دوربین یک بارکد را در یک ثانیه ده‌ها بار می‌بیند. بدونِ این، یک بار
 *  گرفتنِ کالا جلوی دوربین ده‌ها عدد به سبد اضافه می‌کرد.
 *  پنجرهٔ زمانی همان ۱۲۰۰ میلی‌ثانیهٔ نسخهٔ وب است.
 */
class ScanGate(private val windowMs: Long = 1200) {
  private var lastCode: String? = null
  private var lastAt = 0L

  fun accept(code: String, now: Long = System.currentTimeMillis()): Boolean {
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

/**
 *  بوقِ اسکن + لرزش — بازخوردی که فروشنده بدونِ نگاه‌کردن به صفحه می‌فهمد.
 *
 *  صدا همان بوقِ آشنای بارکدخوانِ دکان است (`res/raw/scan_beep.mp3`)، نه
 *  بوقِ ساختگیِ `ToneGenerator`. فروشنده این صدا را از دکان‌های دیگر
 *  می‌شناسد؛ شنیدنش یعنی «خواند».
 *
 *  پخش‌کننده یک‌بار ساخته و نگه داشته می‌شود: ساختنِ `MediaPlayer` برای هر
 *  اسکن، چند ده میلی‌ثانیه طول می‌کشد و در اسکنِ پشتِ‌سرِ هم، صدا عقب
 *  می‌افتاد.
 */
object ScanFeedback {

  private var player: android.media.MediaPlayer? = null

  fun ok(context: Context) {
    playBeep(context)
    vibrate(context, 60)
  }

  fun unknown(context: Context) {
    // بارکدِ ناشناس صدای خودش را دارد تا با «خواند» اشتباه نشود
    beep(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
    vibrate(context, 200)
  }

  /**
   *  بوقِ اسکن.
   *
   *  ── باگی که اینجا بسته شد ────────────────────────────────────────
   *  گزارش شد «موقعِ اسکن صدا نمی‌دهد». دو راهِ خروجِ **بی‌صدا** در این
   *  تابع بود:
   *
   *   ۱. `MediaPlayer.create(...) ?: return` — این `return` از دلِ
   *      `runCatching` بیرون می‌پرید و به `onFailure` نمی‌رسید. یعنی اگر
   *      ساختنِ پخش‌کننده `null` برمی‌گرداند (که پیش می‌آید: خطای کدک،
   *      کمبودِ منبع، یا گوشی‌ای که آن فایل را باز نمی‌کند)، هیچ صدایی
   *      پخش نمی‌شد و بوقِ جایگزین هم زده نمی‌شد.
   *   ۲. `existing.start()` روی پخش‌کننده‌ای که آزاد شده باشد استثنا
   *      می‌دهد؛ آن استثنا گرفته می‌شد ولی پخش‌کننده‌ی خراب سرِ جایش
   *      می‌ماند، پس از آن به بعد **هر** اسکن بی‌صدا بود.
   *
   *  حالا هر دو راه بسته است: نتیجه‌ی هر تلاش سنجیده می‌شود و اگر نشد،
   *  پخش‌کننده‌ی خراب دور انداخته می‌شود و بوقِ ساختگی زده می‌شود.
   *  فروشنده باید بی نگاه کردن به صفحه بفهمد که خوانده شد.
   *  ──────────────────────────────────────────────────────────────────
   */
  private fun playBeep(context: Context) {
    if (restart()) return
    if (fresh(context)) return
    //  نه فایل، نه پخش‌کننده — دستِ‌کم یک بوق
    beep(android.media.ToneGenerator.TONE_PROP_BEEP, 120)
  }

  /** پخش‌کننده‌ی موجود را از اول راه بیندازد؛ نشد، دورش می‌اندازد */
  private fun restart(): Boolean {
    val existing = player ?: return false
    //  دو اسکنِ سریع نباید صدای هم را بخورند، پس از اول
    val ok = runCatching {
      existing.seekTo(0)
      existing.start()
      true
    }.getOrDefault(false)
    if (!ok) {
      runCatching { existing.release() }
      player = null
    }
    return ok
  }

  /** پخش‌کننده‌ی تازه؛ `false` یعنی این گوشی این فایل را پخش نمی‌کند */
  private fun fresh(context: Context): Boolean = runCatching {
    val made = android.media.MediaPlayer.create(
      context.applicationContext, ir.vil3ntec.tohid.R.raw.scan_beep,
    ) ?: return false
    made.setVolume(1f, 1f)
    player = made
    made.start()
    true
  }.getOrDefault(false)

  /** وقتی برنامه بسته می‌شود، پخش‌کننده را آزاد کن */
  fun release() {
    runCatching { player?.release() }
    player = null
  }

  private fun beep(tone: Int, ms: Int) {
    runCatching {
      val gen = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 85)
      gen.startTone(tone, ms)
      android.os.Handler(android.os.Looper.getMainLooper())
        .postDelayed({ runCatching { gen.release() } }, (ms + 80).toLong())
    }
  }

  @Suppress("DEPRECATION")
  private fun vibrate(context: Context, ms: Long) {
    runCatching {
      val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
      } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
      }
      if (android.os.Build.VERSION.SDK_INT >= 26) {
        vibrator.vibrate(android.os.VibrationEffect.createOneShot(ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
      } else {
        vibrator.vibrate(ms)
      }
    }
  }
}
