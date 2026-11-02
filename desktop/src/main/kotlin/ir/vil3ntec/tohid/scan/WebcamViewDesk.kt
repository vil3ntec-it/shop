package ir.vil3ntec.tohid.scan

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.hw.WebcamScan
import ir.vil3ntec.tohid.core.i18n.StrDevices
import javax.swing.SwingUtilities

/** حالِ وب‌کمِ کادرِ اسکن */
internal sealed interface WebcamState {
  data object Opening : WebcamState
  data object Live : WebcamState
  data object NoCamera : WebcamState
  data class Failed(val reason: String) : WebcamState
}

/**
 *  یک نشستِ وب‌کم: دوربینِ برگزیده (`WebcamScan.pickCamera`) با بهترین حالتِ تصویر (`WebcamScan.pickMode`)، رشتهٔ
 *  پس‌زمینه‌ای که فریم می‌گیرد و هر فریم را به [WebcamLoop] می‌دهد. پیش‌نمایش حداکثر ۱۵ بار در ثانیه به صفحه می‌رود.
 *  همهٔ خبرها روی رشتهٔ رابط (Swing) می‌رسند. `stop` دوربین را همان دم آزاد می‌کند.
 */
internal class WebcamSession(
  private val cameraId: String,
  private val qr: Boolean,
  private val qrOnly: Boolean,
  private val known: (String) -> Boolean,
  private val onFrame: (ImageBitmap) -> Unit,
  private val onCode: (String) -> Unit,
  private val onState: (WebcamState) -> Unit,
) {
  @Volatile private var running = false
  private var thread: Thread? = null

  fun start() {
    if (running) return
    running = true
    thread = Thread(::run, "webcam-scan").apply { isDaemon = true; start() }
  }

  fun stop() {
    running = false
    thread?.interrupt()
    thread = null
  }

  private fun ui(block: () -> Unit) = SwingUtilities.invokeLater { if (running) block() }

  private fun state(s: WebcamState) = ui { onState(s) }

  private fun run() {
    state(WebcamState.Opening)
    val ctx = Webcams.context() ?: return state(WebcamState.Failed(Webcams.loadError ?: "—"))
    val devices = runCatching { ctx.devices.orEmpty() }.getOrDefault(emptyList())
    val cameras = devices.map(Webcams::cameraOf)
    val pick = WebcamScan.pickCamera(cameras, cameraId) ?: return state(WebcamState.NoCamera)
    val device = devices[cameras.indexOf(pick)]
    val formats = runCatching { device.formats.orEmpty() }.getOrDefault(emptyList())
    val modes = formats.map { WebcamScan.Mode(it.formatInfo.width, it.formatInfo.height, it.formatInfo.fps) }
    val mode = WebcamScan.pickMode(modes) ?: return state(WebcamState.Failed(pick.name))
    val format = formats[modes.indexOf(mode)]
    val stream = runCatching { device.openStream(format) }
      .getOrElse { return state(WebcamState.Failed(it.message ?: pick.name)) }
    val wanted = WebcamDecoder.formats(qr, qrOnly)
    val loop = WebcamLoop(qrOnly, BarcodeGuard(known = known)) { WebcamDecoder.read(it, wanted) }
    var shownAt = 0L
    try {
      state(WebcamState.Live)
      while (running) {
        if (!stream.hasNewFrame()) { Thread.sleep(8); continue }
        val frame = stream.capture() ?: continue
        val now = System.currentTimeMillis()
        if (now - shownAt >= PREVIEW_GAP_MS) {
          shownAt = now
          val bitmap = frame.toComposeImageBitmap()
          ui { onFrame(bitmap) }
        }
        loop.offer(frame, now)?.let { code -> ui { onCode(code) } }
      }
    } catch (_: InterruptedException) {
      //  بستنِ کادر
    } catch (e: Exception) {
      state(WebcamState.Failed(e.message ?: e.javaClass.simpleName))
    } finally {
      runCatching { stream.close() }
    }
  }

  private companion object {
    const val PREVIEW_GAP_MS = 66L
  }
}

/** متنِ وضعیتِ وب‌کم برای سطرِ کادرِ اسکن و `onStatus` ِ صفحه */
internal fun webcamText(s: WebcamState): Pair<String, Boolean> = when (s) {
  WebcamState.Opening -> StrDevices.webcamOpening to false
  WebcamState.Live -> StrDevices.webcamLive to false
  WebcamState.NoCamera -> StrDevices.webcamNone to true
  is WebcamState.Failed -> StrDevices.webcamFailed(s.reason) to true
}

/**
 *  تصویرِ زندهٔ وب‌کم درونِ کادرِ اسکنِ کامپیوتر. کدِ پذیرفته همان `onCode` ِ دوربین را صدا می‌زند (همان بوق و همان
 *  `ScanGate` ِ صفحه)؛ بستنِ کادر یا خاموش کردنِ کلید دوربین را آزاد می‌کند.
 */
@Composable
internal fun WebcamPane(
  cameraId: String,
  qr: Boolean,
  qrOnly: Boolean,
  isKnown: (String) -> Boolean,
  onCode: (String) -> Unit,
  onState: (WebcamState) -> Unit,
  modifier: Modifier = Modifier,
) {
  val latestCode by rememberUpdatedState(onCode)
  val latestKnown by rememberUpdatedState(isKnown)
  val latestState by rememberUpdatedState(onState)
  var frame by remember { mutableStateOf<ImageBitmap?>(null) }
  DisposableEffect(cameraId, qr, qrOnly) {
    val session = WebcamSession(
      cameraId, qr, qrOnly,
      known = { code -> runCatching { latestKnown(code) }.getOrDefault(false) },
      onFrame = { frame = it },
      onCode = { latestCode(it) },
      onState = { latestState(it) },
    )
    session.start()
    onDispose { session.stop(); frame = null }
  }
  Box(modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
    frame?.let { Image(it, contentDescription = StrDevices.webcamTitle, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth()) }
  }
}
