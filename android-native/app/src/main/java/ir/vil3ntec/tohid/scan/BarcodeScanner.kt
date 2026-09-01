package ir.vil3ntec.tohid.scan

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.util.Log
import android.util.Size
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraControl
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraScanner(
  onCode: (String) -> Unit,
  onStatus: (String, Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  // مقدارِ تازهٔ onCode بدونِ بستنِ دوباره‌ی دوربین
  val latestCode by rememberUpdatedState(onCode)
  val latestStatus by rememberUpdatedState(onStatus)

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
        pilot.attach(camera.cameraControl)

        /*
         *  خواننده **پس از** باز شدنِ دوربین ساخته می‌شود، چون سقفِ
         *  بزرگ‌نمایی را باید از خودِ همین دوربین پرسید؛ ML Kit بیش از آن
         *  پیشنهاد نمی‌دهد.
         */
        val maxZoom = camera.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
        val client = buildScanner(maxZoom) { wanted -> pilot.zoomTo(wanted) }
        scanner = client

        analysis.setAnalyzer(executor) { proxy ->
          val media = proxy.image
          if (media == null) {
            proxy.close()
            return@setAnalyzer
          }
          val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
          client.process(image)
            .addOnSuccessListener { codes ->
              val value = codes.firstOrNull()?.rawValue
              if (!value.isNullOrBlank()) {
                pilot.readOk()
                latestCode(value)
              } else {
                pilot.nothingRead()
              }
            }
            .addOnFailureListener { Log.d("Tohid", "خواندن فریم ناموفق", it) }
            // هرچه پیش بیاید فریم بسته می‌شود؛ وگرنه اسکن بعدی هرگز نمی‌آید
            .addOnCompleteListener { proxy.close() }
        }

        latestStatus("آماده اسکن — بارکد را حدود ۲۰ سانتی از دوربین بگیرید", false)
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
   *  زدنِ انگشت روی بارکد، فوکوس را همان‌جا می‌برد.
   *
   *  `detectTapGestures` فقط «زدن» را می‌گیرد و کشیدن را رد می‌کند، پس
   *  اسکرولِ صفحهٔ فروش که این نما داخلش است دست‌نخورده می‌ماند.
   *
   *  نقطه با `previewView.meteringPointFactory` ساخته می‌شود نه دستی:
   *  همان است که چرخشِ گوشی و بریدگیِ `FILL_CENTER` را حساب می‌کند —
   *  یعنی جایی که کاربر زده، همان جایی است که دوربین فوکوس می‌کند.
   */
  Box(
    modifier.pointerInput(Unit) {
      detectTapGestures { at ->
        pilot.focusOnTap(previewView, at.x, at.y)
      }
    }
  ) {
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
  }
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
 *  کِی فوکوس بدهیم، کِی دست نگه داریم، و کِی بزرگ‌نمایی کنیم.
 *
 *  ── چرا از رویِ حالِ دوربین، نه از روی ساعت ────────────────────────
 *  فرمانِ فوکوس ارزان نیست و — مهم‌تر — در حالتِ پیوسته یعنی «قفل کن».
 *  اگر همین‌طور بی‌حساب فرستاده شود، جست‌وجوی خودِ دوربین بریده می‌شود و
 *  لنز تار قفل می‌ماند؛ همان چیزی که نسخهٔ قبل را کندتر کرد.
 *
 *  پس تصمیم را از خودِ دوربین می‌پرسیم. `CONTROL_AF_STATE` هر فریم
 *  می‌گوید کجای کار است:
 *
 *   • **در حالِ جست‌وجو** (`PASSIVE_SCAN` / `ACTIVE_SCAN`) → دست نزن.
 *     دارد کارش را می‌کند؛ هر فرمانی همین را می‌بُرد.
 *   • **تیز** (`PASSIVE_FOCUSED` / `FOCUSED_LOCKED`) → کاری لازم نیست.
 *     شمارشِ تلاش‌ها هم صفر می‌شود.
 *   • **خوابیده یا ناموفق** (`INACTIVE` / `PASSIVE_UNFOCUSED` /
 *     `NOT_FOCUSED_LOCKED`) → **حالا** فرمان بجاست: فوکوس روی ناحیهٔ
 *     مرکزی، با فاصلهٔ کمینهٔ ۱٫۲ ثانیه از فرمانِ قبلی.
 *   • **هیچ** (`null`) → این دوربین فوکوسِ متحرک ندارد (لنزِ ثابت). هر
 *     فرمانی بی‌فایده است.
 *
 *  و چهار تلاشِ ناموفق پشتِ سرِ هم، بس است: یعنی مشکل فوکوس نیست —
 *  کالا نزدیک‌تر از کمترین فاصلهٔ فوکوس است یا بارکد پاک شده. آن‌جا کارِ
 *  بزرگ‌نمایی و انگشتِ کاربر است، نه حلقهٔ فوکوس. یک خواندنِ موفق یا یک
 *  فریمِ تیز، این سهمیه را از نو پر می‌کند.
 *
 *  مهلتِ خودرهایی هم روی دو ثانیه است، پس فوکوسِ نقطه‌ای **قفل نمی‌ماند**
 *  و دوربین به جست‌وجوی پیوسته برمی‌گردد. قفل کردنش (`disableAutoCancel`)
 *  دقیقاً همان اشکالی است که کالای بعدی را تار می‌کند.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  همه‌ی متدها از رشته‌های مختلف صدا زده می‌شوند — فوکوس از رشتهٔ دوربین،
 *  خواندن از رشتهٔ تحلیل، و انگشت از رشتهٔ اصلی — پس هر چه حالت دارد
 *  `@Volatile` است و هیچ‌کدام کارِ سنگینی نمی‌کنند.
 */
private class FocusPilot {

  @Volatile private var control: CameraControl? = null
  @Volatile private var lastFocusAt = 0L
  @Volatile private var lastReadAt = 0L
  @Volatile private var lastZoomAt = 0L
  @Volatile private var zoom = 1f
  @Volatile private var tries = 0

  fun attach(cameraControl: CameraControl) {
    control = cameraControl
    lastReadAt = 0L
    tries = 0
    zoom = 1f
  }

  fun detach() {
    control = null
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
        return
      }
      else -> nudge()
    }
  }

  /** خوانده شد — فوکوس همان است که باید باشد */
  fun readOk() {
    lastReadAt = System.currentTimeMillis()
    tries = 0
  }

  /**
   *  فریم آمد و چیزی خوانده نشد.
   *
   *  اینجا فقط بزرگ‌نمایی برمی‌گردد سرِ جایش: اگر ML Kit برای یک بارکدِ
   *  ریز نزدیک کرده بود و آن کالا رفته، ماندنِ بزرگ‌نمایی یعنی کالای
   *  بعدی از کادر بیرون می‌افتد. فوکوس اینجا کاری ندارد؛ تصمیمش از
   *  `onFocusState` می‌آید.
   */
  fun nothingRead() {
    if (zoom <= 1.01f) return
    val now = System.currentTimeMillis()
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
    if (now - lastZoomAt < ZOOM_GAP_MS) return false
    lastZoomAt = now
    zoom = wanted
    //  با نزدیک شدنِ کادر، صحنه عوض می‌شود؛ سهمیهٔ فوکوس هم از نو
    tries = 0
    return runCatching { cameraControl.setZoomRatio(wanted) }.isSuccess
  }

  /** انگشتِ کاربر روی تصویر: فوکوس و نورسنجی همان نقطه */
  fun focusOnTap(view: PreviewView, x: Float, y: Float) {
    val cameraControl = control ?: return
    lastFocusAt = System.currentTimeMillis()
    tries = 0
    runCatching {
      val point = view.meteringPointFactory.createPoint(x, y)
      val action = FocusMeteringAction
        .Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
        .setAutoCancelDuration(TAP_HOLD_SEC, TimeUnit.SECONDS)
        .build()
      cameraControl.startFocusAndMetering(action)
    }
  }

  private fun nudge() {
    val cameraControl = control ?: return
    val now = System.currentTimeMillis()
    if (now - lastFocusAt < REFOCUS_GAP_MS) return
    if (tries >= MAX_TRIES) return
    tries++
    lastFocusAt = now
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
      //  و ناخوانا می‌شود
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

  private fun playBeep(context: Context) {
    runCatching {
      val existing = player
      if (existing != null) {
        // اگر هنوز در حال پخش است، از اول شروع کن — دو اسکنِ سریع نباید
        // صدای هم را بخورند
        existing.seekTo(0)
        existing.start()
        return
      }
      val fresh = android.media.MediaPlayer.create(context.applicationContext, ir.vil3ntec.tohid.R.raw.scan_beep)
        ?: return
      fresh.setVolume(1f, 1f)
      player = fresh
      fresh.start()
    }.onFailure {
      // اگر فایل به هر دلیلی پخش نشد، دستِ‌کم یک بوق بزن
      beep(android.media.ToneGenerator.TONE_PROP_BEEP, 120)
    }
  }

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
