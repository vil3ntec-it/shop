package ir.vil3ntec.tohid.scan

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
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
 *    • تصویرِ زنده است و بس؛ هیچ‌جا عکس گرفته نمی‌شود. بارکد از همان
 *      فریم‌های زندهٔ `ImageAnalysis` خوانده می‌شود.
 *
 *  ── چرا فوکوس کند بود و اینجا چه شد ───────────────────────────────
 *  سه چیز روی هم جمع شده بود و کاربر باید منتظر می‌ماند تا تصویر جا
 *  بیفتد:
 *
 *   ۱. **هیچ‌جا در برنامه فوکوس تنظیم نشده بود.** نه حالتِ فوکوس، نه
 *      ناحیه‌اش. یعنی هر چه بود، پیش‌فرضِ خودِ گوشی بود. CameraX در
 *      حالتِ عادی «فوکوسِ پیوستهٔ عکس» را می‌خواهد، ولی این حالت منتظر
 *      می‌ماند تا **خودش** بفهمد صحنه عوض شده و آن‌وقت جست‌وجو را شروع
 *      کند؛ روی گوشی‌های ارزان همین فهمیدن نیم تا یک ثانیه طول می‌کشد.
 *      حالا هم حالتِ فوکوس صریح خواسته می‌شود و هم یک بار، همان لحظه‌ای
 *      که دوربین باز شد، فوکوس روی **ناحیهٔ مرکزی** هُل داده می‌شود.
 *
 *   ۲. **فریمِ تحلیل ۶۴۰×۴۸۰ بود** — پیش‌فرضِ `ImageAnalysis`. با این
 *      اندازه، بارکدِ یک قوطیِ کوچک تا وقتی نصفِ فریم را نگیرد خوانده
 *      نمی‌شود، و کاربر ناچار بود کالا را بچسباند به شیشهٔ دوربین؛
 *      آن‌جا دیگر **زیرِ کمترین فاصلهٔ فوکوسِ** لنز است و دوربین
 *      اصلاً نمی‌تواند فوکوس کند. تقصیر فوکوس نبود، تقصیر رزولوشن بود.
 *      حالا ۱۲۸۰×۷۲۰ خواسته می‌شود: همان بارکد از بیست‌سی سانتی خوانده
 *      می‌شود، جایی که لنز راحت فوکوس می‌کند.
 *
 *   ۳. **خطِ لولهٔ تصویر تنظیمِ کیفیت داشت نه تنظیمِ سرعت.** لرزش‌گیرِ
 *      ویدیو (روی بعضی گوشی‌ها پیش‌فرض روشن) چند فریم تصویر را عقب
 *      می‌اندازد و نویزگیر و لبه‌تیزکنِ باکیفیت هم چند میلی‌ثانیه به هر
 *      فریم اضافه می‌کنند. هر سه روی `FAST`/خاموش رفتند.
 *
 *  و آن کاری که **نکردیم**: حلقهٔ فوکوس. دستورِ فوکوس گران است و پشتِ
 *  سرِ هم فرستادنش لنز را به تلوتلو می‌اندازد و کار را کندتر می‌کند —
 *  دقیقاً همان دامی که باید از آن دور ماند. قاعده‌اش پایین‌تر، سرِ
 *  `nudgeFocus`، نوشته شده.
 *
 *  `implementationMode = COMPATIBLE` هم عمداً سرِ جایش ماند: خطِ اسکن و
 *  نوارِ وضعیت با کامپوز **روی** همین نما کشیده می‌شوند و حالتِ
 *  `PERFORMANCE` که از `SurfaceView` استفاده می‌کند، آن‌ها را می‌خورد.
 *  ──────────────────────────────────────────────────────────────────
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
 *  اندازهٔ فریمِ خواسته‌شده — برای هم نما و هم تحلیل.
 *
 *  یکی بودنشان مهم است: آنچه کاربر می‌بیند باید همان چیزی باشد که خوانده
 *  می‌شود. اگر نسبتِ دو تا یکی نباشد، بارکدی که در نما پیداست ممکن است
 *  بیرونِ فریمِ تحلیل مانده باشد.
 */
private val FRAME = Size(1280, 720)

/** ناحیهٔ فوکوس: ۲۲٪ میانهٔ کادر — همان‌جا که خطِ اسکن است */
private const val CENTER_REGION = 0.22f

/** بعد از این‌قدر ثانیه، فوکوسِ نقطه‌ای خودش رها می‌شود و پیوسته برمی‌گردد */
private const val FOCUS_HOLD_SEC = 3L

/** کمترین فاصلهٔ دو دستورِ فوکوس، به میلی‌ثانیه */
private const val REFOCUS_GAP_MS = 2500L

/** تا این مدت پس از آخرین خواندنِ موفق، کاری با فوکوس نداریم */
private const val SETTLED_MS = 1800L

/** بیشترین هُلِ فوکوس تا وقتی چیزی خوانده نشده — تا حلقه نشود */
private const val MAX_NUDGES = 6

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

  DisposableEffect(Unit) {
    val scanner = BarcodeScanning.getClient(
      BarcodeScannerOptions.Builder().setBarcodeFormats(FORMATS).build()
    )
    var provider: ProcessCameraProvider? = null
    //  ادارهٔ فوکوس: دستگیره‌ی دوربین و حساب‌وکتابِ اینکه کِی هُل بدهیم
    val focus = FocusPilot()

    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({
      val cameraProvider = runCatching { future.get() }.getOrNull()
      if (cameraProvider == null) {
        latestStatus("دوربین در دسترس نیست", true)
        return@addListener
      }
      provider = cameraProvider

      /*
       *  نما و تحلیل، هر دو ۱۲۸۰×۷۲۰ و هر دو ۱۶:۹.
       *  `FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER` یعنی اگر گوشی این
       *  اندازه را نداشت، نزدیک‌ترینِ بالاتر و اگر نبود نزدیک‌ترینِ
       *  پایین‌تر — نه اینکه دست خالی برگردد.
       */
      val resolution = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(
          ResolutionStrategy(FRAME, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
        )
        .build()

      val previewBuilder = Preview.Builder().setResolutionSelector(resolution)

      /*
       *  اینجا با Camera2Interop حرفِ آخر را به خودِ دوربین می‌زنیم.
       *  این تنظیم‌ها روی درخواستِ تکرارشوندهٔ نشست می‌نشینند، پس یک بار
       *  گفته می‌شوند و تا آخرِ کار برجایند — نه با هر فریم.
       */
      Camera2Interop.Extender(previewBuilder)
        //  فوکوسِ پیوستهٔ عکس: تیزیِ عکسِ ثابت با به‌روزرسانیِ همیشگی —
        //  همان چیزی که برای بارکد می‌خواهیم، نه فوکوسِ تک‌ضربِ AUTO که
        //  تا دستور ندهی لنز را تکان نمی‌دهد
        .setCaptureRequestOption(
          CaptureRequest.CONTROL_AF_MODE,
          CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
        )
        .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
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

      val preview = previewBuilder.build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

      val analysis = ImageAnalysis.Builder()
        // فقط تازه‌ترین فریم؛ فریمِ کهنه به درد اسکن نمی‌خورد و صف را پر می‌کند
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        // فریمِ درشت‌تر یعنی بارکد از فاصلهٔ بیشتری خوانده می‌شود؛ همان
        // فاصله‌ای که لنز در آن فوکوس می‌کند
        .setResolutionSelector(resolution)
        .build()

      analysis.setAnalyzer(executor) { proxy ->
        val media = proxy.image
        if (media == null) {
          proxy.close()
          return@setAnalyzer
        }
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        scanner.process(image)
          .addOnSuccessListener { codes ->
            val value = codes.firstOrNull()?.rawValue
            if (!value.isNullOrBlank()) {
              focus.readOk()
              latestCode(value)
            } else {
              focus.nothingRead()
            }
          }
          .addOnFailureListener { Log.d("Tohid", "خواندن فریم ناموفق", it) }
          // هرچه پیش بیاید فریم بسته می‌شود؛ وگرنه اسکن بعدی هرگز نمی‌آید
          .addOnCompleteListener { proxy.close() }
      }

      runCatching {
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
          lifecycleOwner,
          CameraSelector.DEFAULT_BACK_CAMERA,
          preview,
          analysis,
        )
      }.onSuccess { camera ->
        focus.attach(camera.cameraControl)
        latestStatus("آماده اسکن — بارکد را جلوی دوربین بگیرید", false)
      }.onFailure {
        latestStatus("دوربین باز نشد: ${it.message ?: "دلیل نامعلوم"}", true)
      }
    }, ContextCompat.getMainExecutor(context))

    onDispose {
      runCatching { provider?.unbindAll() }
      runCatching { scanner.close() }
      executor.shutdown()
    }
  }

  AndroidView(factory = { previewView }, modifier = modifier)
}

/**
 *  کِی به فوکوس هُل بدهیم — و کِی دست نگه داریم.
 *
 *  ── قاعده ─────────────────────────────────────────────────────────
 *  دستورِ فوکوس ارزان نیست: لنز راه می‌افتد، جست‌وجو می‌کند و تا جا
 *  نیفتد تصویر تار است. اگر این دستور با هر فریم — یا حتی هر چند فریم —
 *  فرستاده شود، لنز مدام از نو شروع می‌کند و نتیجه‌اش **کندتر** شدنِ
 *  اسکن است، نه تندتر شدنش. پس:
 *
 *   • **یک بار** همان لحظه‌ای که دوربین باز شد، روی ناحیهٔ مرکزی.
 *     همان‌جا که خطِ اسکن است و کاربر بارکد را همان‌جا می‌گیرد.
 *   • بعد از آن، فقط وقتی **چیزی خوانده نمی‌شود** و دستِ‌کم دو و نیم
 *     ثانیه از دستورِ قبلی گذشته. یعنی وقتی تصویر واقعاً تار است.
 *   • با اولین خواندنِ موفق، دست از فوکوس برمی‌داریم: کارش را کرده.
 *   • و اگر شش بار هُل دادیم و باز چیزی خوانده نشد، بس است — یعنی
 *     مشکل فوکوس نیست (کالایی جلوی دوربین نیست، یا بارکد پاک شده) و
 *     ادامه‌اش می‌شد همان حلقه‌ای که نباید باشد. از آن به بعد فوکوسِ
 *     پیوستهٔ خودِ دوربین کار را دست می‌گیرد. یک خواندنِ موفق، این
 *     سهمیه را از نو پر می‌کند.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  نکته‌ی ریز ولی مهم: مهلتِ خودرهاییِ فوکوس روی سه ثانیه است. یعنی
 *  فوکوسِ نقطه‌ای **قفل نمی‌ماند**؛ سرِ سه ثانیه دوربین به فوکوسِ پیوسته
 *  برمی‌گردد. قفل کردنش (`disableAutoCancel`) دقیقاً همان اشکالی است که
 *  کالای بعدی را تار می‌کند.
 */
private class FocusPilot {

  @Volatile private var control: CameraControl? = null
  @Volatile private var lastFocusAt = 0L
  @Volatile private var lastReadAt = 0L
  @Volatile private var nudges = 0

  /** دوربین باز شد: همین حالا یک بار روی میانهٔ کادر فوکوس کن */
  fun attach(cameraControl: CameraControl) {
    control = cameraControl
    lastReadAt = 0L
    nudges = 0
    focusCenter()
  }

  /** خوانده شد — فوکوس همان است که باید باشد، کاری نکن */
  fun readOk() {
    lastReadAt = System.currentTimeMillis()
    nudges = 0
  }

  /** فریم آمد و چیزی خوانده نشد؛ شاید تار است، شاید چیزی جلویش نیست */
  fun nothingRead() {
    val now = System.currentTimeMillis()
    if (now - lastReadAt < SETTLED_MS) return
    if (now - lastFocusAt < REFOCUS_GAP_MS) return
    if (nudges >= MAX_NUDGES) return
    nudges++
    focusCenter()
  }

  private fun focusCenter() {
    val cameraControl = control ?: return
    lastFocusAt = System.currentTimeMillis()
    runCatching {
      /*
       *  نقطه بر حسبِ کسری از خودِ فریم داده می‌شود، پس نه به اندازهٔ
       *  نما بند است و نه به چرخشِ گوشی — و از هر رشته‌ای می‌شود ساختش،
       *  برخلافِ `previewView.meteringPointFactory` که مالِ رشتهٔ اصلی است.
       */
      val center = SurfaceOrientedMeteringPointFactory(1f, 1f)
        .createPoint(0.5f, 0.5f, CENTER_REGION)
      //  نور را هم همان‌جا بسنج: بارکدِ سفید زیرِ نورِ تندِ دکان می‌سوزد
      //  و ناخوانا می‌شود
      val flags = FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
      val action = FocusMeteringAction.Builder(center, flags)
        .setAutoCancelDuration(FOCUS_HOLD_SEC, TimeUnit.SECONDS)
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
