package ir.vil3ntec.tohid.scan

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.sin

/**
 *  «دوربینِ اسکن» روی کامپیوتر — بارکدخوانِ دستیِ USB یا بلوتوث.
 *
 *  روی گوشی این‌جا تصویرِ زندهٔ دوربین است. کامپیوترِ دکان بارکدخوانِ
 *  سخت‌افزاری دارد، که یک **صفحه‌کلید** است: رقم‌ها را تایپ می‌کند و
 *  Enter می‌زند. پس این کادر یک گیرندهٔ نامرئیِ همیشه‌متمرکز است: هر
 *  چیزی که بارکدخوان تایپ کند با Enter همان `onCode`ِ دوربین را صدا
 *  می‌زند — همان راه، همان سدِ تکرار، همان بوق.
 *
 *  و بی بارکدخوان؟ همان کادرِ «بارکد را دستی وارد کنید»ِ صفحه.
 *
 *  ⚠️ `isKnown` فقط برای سدِ خوانشِ دروغینِ دوربین است؛ بارکدخوانِ
 *  سخت‌افزاری نصفه نمی‌خواند، پس این‌جا به کار نمی‌آید.
 */
@Composable
fun CameraScanner(
  onCode: (String) -> Unit,
  onStatus: (String, Boolean) -> Unit,
  modifier: Modifier = Modifier,
  isKnown: (String) -> Boolean = { false },
) {
  val latest by rememberUpdatedState(onCode)
  val status by rememberUpdatedState(onStatus)
  var typed by remember { mutableStateOf("") }
  var focused by remember { mutableStateOf(false) }
  val focus = remember { FocusRequester() }

  LaunchedEffect(Unit) {
    status(READY_TEXT, false)
    runCatching { focus.requestFocus() }
  }

  fun submit() {
    val code = typed.trim()
    typed = ""
    if (code.isNotEmpty()) latest(code)
  }

  Box(modifier.background(Color(0xFF0E1116)), contentAlignment = Alignment.Center) {
    Column(
      Modifier.padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(
        Icons.Filled.QrCodeScanner,
        contentDescription = null,
        tint = if (focused) Color(0xFF34D399) else Color(0xFF94A3B8),
        modifier = Modifier.size(56.dp),
      )
      Spacer(Modifier.height(10.dp))
      Text(
        if (focused) "بارکدخوان آماده است — کالا را اسکن کنید" else "برای اسکن، یک بار روی این کادر بزنید",
        color = Color.White,
        style = MaterialTheme.typography.titleSmall,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        "بارکدخوانِ USB یا بلوتوث را به کامپیوتر وصل کنید؛ نصب لازم ندارد.",
        color = Color(0xFFCBD5E1),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(12.dp))
      //  گیرنده: متنِ تایپ‌شده دیده می‌شود تا فروشنده بداند چیزی رسید
      BasicTextField(
        value = typed,
        onValueChange = { typed = it.filter { c -> !c.isISOControl() }.take(64) },
        singleLine = true,
        textStyle = TextStyle(color = Color.White, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(Color(0xFF34D399)),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier
          .fillMaxWidth(0.8f)
          .background(Color(0x22FFFFFF), RoundedCornerShape(10.dp))
          .padding(10.dp)
          .focusRequester(focus)
          .onFocusChanged { focused = it.isFocused }
          .onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) {
              submit(); true
            } else false
          },
      )
    }
  }
}

private const val READY_TEXT = "آماده اسکن — بارکدخوان را روی کالا بگیرید"

/**
 *  جلوگیری از اسکنِ تکراری — همان پنجرهٔ ۱۲۰۰ میلی‌ثانیه‌ای.
 *  (همان کلاسِ اندروید؛ این‌جا تکرار شده چون فایلِ اندروید دوربینِ
 *  CameraX را هم در خود دارد.)
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
 *  بوقِ اسکن روی بلندگوی کامپیوتر.
 *
 *  فایلِ `scan_beep.mp3`ِ گوشی را جاوا بی کتابخانه پخش نمی‌کند؛ پس همان
 *  حس با یک بوقِ کوتاهِ ساخته‌شده: «خواند» یک بوقِ زیر، «ناشناس» دو بوقِ
 *  بم — تا فروشنده بی نگاه کردن فرقشان را بفهمد.
 */
object ScanFeedback {
  fun ok(context: Context) = tone(2700.0, 90)
  fun unknown(context: Context) {
    Thread {
      play(620.0, 140); Thread.sleep(70); play(620.0, 140)
    }.apply { isDaemon = true }.start()
  }
  fun release() {}

  private fun tone(hz: Double, ms: Int) =
    Thread { play(hz, ms) }.apply { isDaemon = true }.start()

  private fun play(hz: Double, ms: Int) {
    runCatching {
      val rate = 44_100f
      val n = (rate * ms / 1000).toInt()
      val buf = ByteArray(n * 2)
      for (i in 0 until n) {
        //  لبه‌ها نرم، تا «تق» نکند
        val env = minOf(1.0, i / 200.0, (n - i) / 200.0)
        val v = (sin(2 * PI * hz * i / rate) * 0.45 * env * Short.MAX_VALUE).toInt()
        buf[2 * i] = (v and 0xFF).toByte()
        buf[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
      }
      val fmt = AudioFormat(rate, 16, 1, true, false)
      val line = AudioSystem.getSourceDataLine(fmt)
      line.open(fmt); line.start(); line.write(buf, 0, buf.size); line.drain(); line.close()
    }.onFailure { runCatching { java.awt.Toolkit.getDefaultToolkit().beep() } }
  }
}
