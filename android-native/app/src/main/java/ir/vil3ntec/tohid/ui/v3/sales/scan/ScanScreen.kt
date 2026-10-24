package ir.vil3ntec.tohid.ui.v3.sales.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.scan.CameraScanner
import ir.vil3ntec.tohid.scan.ScanFeedback
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Springs
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import kotlinx.coroutines.delay

/**
 *  برگهٔ اسکنِ بارکد (`scan?for=…`) — تمام‌قد و تیره، مثلِ دوربینِ iOS: تصویرِ زنده تا
 *  لبه‌ها، کادرِ گردِ ۲۴ با خطِ اسکنِ روان (`ScanViewfinder`)، راهنما پایین و «بستن» در
 *  آغازِ بالا. با خواندنِ اولین کد کادر رنگِ برند می‌گیرد، بوق و لرزشِ «خواند»
 *  (`ScanFeedback.ok`) و برگه پایین می‌رود؛ بعد کد به صفحهٔ صدازننده می‌رسد (`onCode`).
 *
 *  اجازهٔ دوربین سه حال دارد (`ScanModel`): پرسیدن، رد (فقط از تنظیماتِ گوشی، با پیوندِ
 *  مستقیم) و زنده. برگه با فنر بالا می‌آید و پایین می‌رود؛ «کاهشِ حرکت» ⇒ بی‌درنگ.
 *
 *  @param camera false ⇒ بی تصویرِ دوربین (عکس‌های پیش‌نمایش)
 *  @param overlay false ⇒ بی کادر و پرده (کامپیوتر: بارکدخوانِ سخت‌افزاری کادرِ خودش را دارد)
 *  @param start حالتِ آغازِ دلخواه (عکس‌ها)؛ null ⇒ اجازهٔ واقعیِ دستگاه
 *  @param continuous پیوسته (صندوق): هر خواندن ⇒ `onCode` و کادر لحظه‌ای رنگ می‌گیرد، برگه
 *    باز می‌ماند؛ `bottom` کنترل‌های همان صفحه (بارکدِ دستی، «هر اسکن»، پاسخِ آخر)
 *  @param qr QRِ رسید هم خوانده شود (RET-01، فقط اسکنِ رسید؛ فروش همان قالب‌های بارکد)
 */
@Composable
fun ScanScreen(
  onCode: (String) -> Unit,
  onClose: () -> Unit,
  title: String = StrKit.scanBarcode,
  camera: Boolean = true,
  overlay: Boolean = true,
  start: CameraAccess? = null,
  continuous: Boolean = false,
  bottom: (@Composable ColumnScope.() -> Unit)? = null,
  qr: Boolean = false,
) {
  val context = LocalContext.current
  val c = PhoneV3.colors
  var access by remember { mutableStateOf(start ?: ScanModel.start(granted(context))) }
  var failure by remember { mutableStateOf<String?>(null) }
  var read by remember { mutableStateOf<String?>(null) }
  //  پیوسته: هر خواندن یک بار کادر را رنگ می‌کند
  var flashes by remember { mutableIntStateOf(0) }
  var flashing by remember { mutableStateOf(false) }
  LaunchedEffect(flashes) {
    if (flashes == 0) return@LaunchedEffect
    flashing = true
    delay(MotionTokens.ms(FLASH_MS).toLong())
    flashing = false
  }
  var leaving by remember { mutableStateOf(false) }
  val shown = remember { MutableTransitionState(false) }
  shown.targetState = !leaving

  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> access = ScanModel.afterAsk(ok) }
  LaunchedEffect(Unit) { if (start == null && access == CameraAccess.ASK) ask.launch(Manifest.permission.CAMERA) }
  //  پس از برگشت از تنظیماتِ گوشی: هر ثانیه یک نگاهِ ارزان، فقط تا وقتی اجازه نیست
  if (start == null && access == CameraAccess.DENIED) {
    LaunchedEffect(Unit) {
      while (access != CameraAccess.GRANTED) {
        delay(RECHECK_MS)
        access = ScanModel.recheck(access, granted(context))
      }
    }
  }
  //  خوانده شد ⇒ کادر رنگ می‌گیرد، لحظه‌ای بعد برگه پایین می‌رود
  LaunchedEffect(read) {
    if (read == null) return@LaunchedEffect
    delay(MotionTokens.ms(FLASH_MS).toLong())
    leaving = true
  }
  //  حرکتِ پایین‌رفتن تمام شد ⇒ نتیجه یا بستن
  LaunchedEffect(leaving, shown.isIdle, shown.currentState) {
    if (leaving && shown.isIdle && !shown.currentState) read?.let(onCode) ?: onClose()
  }

  //  نوارهای سیستم بیرونِ پنجرهٔ Dialog خوانده می‌شوند؛ درونش صفر است
  val bars = WindowInsets.systemBars.asPaddingValues()
  Dialog(
    onDismissRequest = { leaving = true },
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    AnimatedVisibility(
      visibleState = shown,
      enter = slideInVertically(V3Springs.ui()) { it } + fadeIn(MotionSpec.tweenOf(MotionTokens.SHORT)),
      exit = slideOutVertically(MotionSpec.tweenOf(MotionTokens.MEDIUM, MotionTokens.EmphasizedAccelerate)) { it } +
        fadeOut(MotionSpec.tweenOf(MotionTokens.MEDIUM)),
    ) {
      Box(Modifier.fillMaxSize().background(c.black)) {
        when (access) {
          CameraAccess.GRANTED -> {
            if (camera) {
              CameraScanner(
                onCode = { raw ->
                  val code = ScanModel.code(raw)
                  when {
                    code == null -> Unit
                    //  صدا و پیام را صندوق می‌دهد (خواند یا ناشناس)
                    continuous -> { flashes++; onCode(code) }
                    read == null -> { read = code; ScanFeedback.ok(context) }
                  }
                },
                onStatus = { text, error -> failure = if (error) text else null },
                modifier = Modifier.fillMaxSize(),
                qr = qr,
              )
            }
            if (overlay) ScanViewfinder(read = read != null || flashing, modifier = Modifier.fillMaxSize())
          }
          CameraAccess.ASK -> Prompt(Icons.Outlined.PhotoCamera, StrKit.cameraNeeded, StrKit.cameraAllow) {
            ask.launch(Manifest.permission.CAMERA)
          }
          CameraAccess.DENIED -> Prompt(Icons.Outlined.NoPhotography, StrSale.cameraFromSettings, StrSale.openPhoneSettings) {
            openAppSettings(context)
          }
        }

        Row(
          Modifier.fillMaxWidth().padding(top = bars.calculateTopPadding() + 8.dp, start = 12.dp, end = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(c.blackRaised)
              .v3Pressable(label = StrKit.close, scale = 0.92f) { leaving = true },
            contentAlignment = Alignment.Center,
          ) { Icon(Icons.Outlined.Close, contentDescription = null, tint = c.onBlack, modifier = Modifier.size(22.dp)) }
          Text(title, style = V3Type.headline, color = c.onBlack, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
          Spacer(Modifier.size(44.dp))
        }

        if (access == CameraAccess.GRANTED && overlay) {
          Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
              .padding(start = 24.dp, end = 24.dp, bottom = bars.calculateBottomPadding() + 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(StrSale.scanAim, style = V3Type.body, color = c.onBlack, textAlign = TextAlign.Center)
            failure?.let {
              Spacer(Modifier.height(6.dp))
              Text(it, style = V3Type.footnote, color = PhoneV3Roles.dangerOnInverse, textAlign = TextAlign.Center)
            }
            if (bottom != null) {
              Spacer(Modifier.height(16.dp))
              bottom()
            }
          }
        } else if (bottom != null) {
          //  بی دوربین هم بارکدِ دستی کار می‌کند
          Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
              .padding(start = 24.dp, end = 24.dp, bottom = bars.calculateBottomPadding() + 24.dp),
          ) { bottom() }
        }
      }
    }
  }
}

/** میانهٔ برگه وقتی دوربین نیست: آیکن در دایرهٔ برآمده، توضیح و یک کار */
@Composable
private fun Prompt(icon: ImageVector, text: String, action: String, onAction: () -> Unit) {
  val c = PhoneV3.colors
  Column(
    Modifier.fillMaxSize().padding(horizontal = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Box(Modifier.size(72.dp).clip(CircleShape).background(c.blackRaised), contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = null, tint = c.onBlackMuted, modifier = Modifier.size(34.dp))
    }
    Spacer(Modifier.height(16.dp))
    Text(text, style = V3Type.body, color = c.onBlack, textAlign = TextAlign.Center)
    Spacer(Modifier.height(20.dp))
    V3Button(action, onAction, Modifier.fillMaxWidth(), haptic = true)
  }
}

private fun granted(context: Context): Boolean =
  ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** صفحهٔ «اجازه‌ها»ی همین برنامه در تنظیماتِ گوشی */
private fun openAppSettings(context: Context) {
  runCatching {
    context.startActivity(
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
  }
}

/** رنگِ برندِ کادر پس از خواندن، پیش از پایین رفتنِ برگه */
private const val FLASH_MS = 260

/** نگاهِ دوباره به اجازه پس از رفتن به تنظیمات */
private const val RECHECK_MS = 1000L
