package ir.vil3ntec.tohid.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import ir.vil3ntec.tohid.scan.CameraScanner
import ir.vil3ntec.tohid.scan.ScanFeedback
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  اسکنِ بارکد برای پُر کردنِ یک کادر.
 *
 *  تا حالا دوربین فقط در صفحهٔ فروش بود. یعنی کسی که صد قلم جنسِ تازه را
 *  ثبت می‌کرد، بارکدِ هر کدام را باید رقم‌به‌رقم می‌خواند و تایپ می‌کرد —
 *  کاری که هم وقت می‌برد هم غلط از آب درمی‌آید، و بارکدِ غلط یعنی همان
 *  کالا سرِ فروش پیدا نمی‌شود.
 *
 *  یک بار اسکن، و بسته می‌شود. اینجا برخلافِ صفحهٔ فروش، کاربر **یک**
 *  بارکد می‌خواهد نه یک صف؛ باز ماندنِ دوربین بعد از خواندن فقط باعث
 *  می‌شود بارکدِ بعدی که جلوی دوربین بیاید کادر را عوض کند.
 */
@Composable
fun BarcodeScanSheet(
  onDismiss: () -> Unit,
  onCode: (String) -> Unit,
  title: String = "اسکن بارکد",
) {
  val context = LocalContext.current

  var granted by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var status by remember { mutableStateOf("در حال آماده‌سازی دوربین…") }
  var statusError by remember { mutableStateOf(false) }
  // بعد از اولین خواندن، فریم‌های بعدی نادیده گرفته می‌شوند تا تا لحظهٔ
  // بسته شدن، کادر دوباره عوض نشود
  var taken by remember { mutableStateOf(false) }

  val askCamera = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { ok ->
    granted = ok
    if (!ok) {
      status = "اجازهٔ دوربین داده نشد — از تنظیمات گوشی اجازه دهید"
      statusError = true
    }
  }

  LaunchedEffect(Unit) {
    if (!granted) askCamera.launch(Manifest.permission.CAMERA)
  }

  // بلندیِ نوارهای سیستم بیرونِ Dialog خوانده می‌شود؛ داخلِ پنجرهٔ Dialog
  // صفر گزارش می‌شود. همان چیزی که دکمهٔ «ذخیره همه» را زیرِ نوار می‌برد.
  val bars = WindowInsets.systemBars.asPaddingValues()

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = false,
    ),
  ) {
    Surface(color = Shop.colors.bg, modifier = Modifier.fillMaxSize()) {
      Column(
        Modifier
          .fillMaxSize()
          .padding(top = bars.calculateTopPadding(), bottom = bars.calculateBottomPadding())
          .padding(16.dp)
      ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = Shop.colors.text,
            modifier = Modifier.weight(1f),
          )
          TextButton(onClick = onDismiss) { Text("انصراف") }
        }
        Spacer(Modifier.height(12.dp))

        Box(
          Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(Radius.md))
            .background(if (granted) Color.Black else Shop.colors.surface2),
          contentAlignment = Alignment.Center,
        ) {
          if (granted) {
            CameraScanner(
              onCode = { code ->
                if (!taken) {
                  taken = true
                  ScanFeedback.ok(context)
                  onCode(code.trim())
                  onDismiss()
                }
              },
              onStatus = { text, isError -> status = text; statusError = isError },
              modifier = Modifier.fillMaxSize(),
            )
            //  دیگر کادری نیست — فقط خطِ اسکن روی تصویرِ زنده
            ScanLine(
              Modifier
                .fillMaxWidth(0.82f)
                .height(150.dp)
            )
            Box(
              Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
              Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                color = if (statusError) Color(0xFFFFB4AE) else Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
              )
            }
          } else {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.padding(24.dp),
            ) {
              Text(
                "برای اسکن، به دوربین اجازه بدهید",
                style = MaterialTheme.typography.bodyMedium,
                color = Shop.colors.muted,
                textAlign = TextAlign.Center,
              )
              Spacer(Modifier.height(10.dp))
              TohidButton(
                text = "اجازهٔ دوربین",
                onClick = { askCamera.launch(Manifest.permission.CAMERA) },
              )
            }
          }
        }

        Spacer(Modifier.height(12.dp))
        Text(
          "بارکد را جلوی دوربین بگیرید — با اولین خواندن، کادر پر می‌شود",
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted2,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}

/** دکمهٔ کوچکِ کنارِ کادرِ بارکد */
@Composable
fun ScanIconButton(onClick: () -> Unit) {
  IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
    Icon(
      Icons.Filled.QrCodeScanner,
      contentDescription = "اسکن بارکد",
      tint = Shop.colors.primary,
      modifier = Modifier.size(21.dp),
    )
  }
}

/**
 *  خطِ اسکن — تنها چیزی که روی تصویرِ دوربین کشیده می‌شود.
 *
 *  ── چه چیزی برداشته شد ────────────────────────────────────────────
 *  کادرِ سفیدِ دورِ ناحیهٔ اسکن. دو اشکال داشت: تصویرِ دوربین را
 *  قاب‌بندی می‌کرد و کاربر گمان می‌کرد بارکد **باید** داخلِ همان کادر
 *  بیفتد، در حالی که خواندن از کلِ فریم است؛ و آن سفیدیِ تندِ دو
 *  پیکسلی، در رابطِ تیرهٔ برنامه مثل وصله می‌زد.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ── چه چیزی جایش آمد ──────────────────────────────────────────────
 *  یک خطِ افقی که نرم بالا و پایین می‌رود و هرگز نمی‌ایستد. سه چیز
 *  مدرنش می‌کند:
 *   • **مغزِ سفید و دو سرِ محو.** خط از میانه سفیدِ داغ است و به دو
 *     کناره می‌رسد و تمام می‌شود؛ پس لبه‌ای ندارد که مثل کادر دیده شود.
 *   • **هالهٔ نرم.** پشتِ خط یک درخششِ کم‌رنگ به رنگِ برنامه می‌آید،
 *     مثل نورِ خودِ بارکدخوان روی جنس.
 *   • **آرام گرفتن سرِ دو سر.** با `FastOutSlowInEasing` و
 *     `RepeatMode.Reverse` خط سرِ بالا و پایین آرام می‌گیرد و برمی‌گردد،
 *     نه اینکه بپرد — همان نرمیِ خواسته‌شده.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  و هزینه‌اش: یک `animateFloat` و دو مستطیل در `drawBehind`. نه لایهٔ
 *  تازه‌ای ساخته می‌شود نه چیدمانی از نو حساب می‌شود، پس فریم‌های دوربین
 *  و اسکن دست‌نخورده می‌مانند.
 */
@Composable
fun ScanLine(modifier: Modifier = Modifier) {
  val tint = Shop.colors.primary

  //  انیمیشن خاموش باشد، خط سرِ جایش می‌ماند — نه اینکه ناپدید شود
  if (!Motion.enabled) {
    Box(modifier.drawBehind { scanLine(0.5f, tint) })
    return
  }

  val glide = rememberInfiniteTransition(label = "scan")
  val y by glide.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      tween(SCAN_SWEEP_MS, easing = FastOutSlowInEasing),
      RepeatMode.Reverse,
    ),
    label = "scanline",
  )
  Box(modifier.drawBehind { scanLine(y, tint) })
}

/** یک رفت یا یک برگشتِ خط، به میلی‌ثانیه */
private const val SCAN_SWEEP_MS = 2100

/**
 *  @param pos جای خط، از ۰ (بالا) تا ۱ (پایین)
 */
private fun DrawScope.scanLine(pos: Float, tint: Color) {
  if (size.height <= 0f || size.width <= 0f) return

  //  کمی از بالا و پایین فاصله می‌گیرد تا به لبهٔ تصویر نچسبد
  val at = size.height * (0.05f + pos.coerceIn(0f, 1f) * 0.90f)

  //  هالهٔ نرم، پشتِ خط
  val halo = 26.dp.toPx()
  drawRect(
    brush = Brush.verticalGradient(
      0f to Color.Transparent,
      0.5f to tint.copy(alpha = 0.20f),
      1f to Color.Transparent,
      startY = at - halo,
      endY = at + halo,
    ),
    topLeft = Offset(0f, at - halo),
    size = Size(size.width, halo * 2f),
  )

  //  خودِ خط: میانه سفید، دو سر محو
  val core = 2.dp.toPx()
  drawRect(
    brush = Brush.horizontalGradient(
      0f to Color.Transparent,
      0.16f to tint.copy(alpha = 0.90f),
      0.5f to Color.White.copy(alpha = 0.95f),
      0.84f to tint.copy(alpha = 0.90f),
      1f to Color.Transparent,
    ),
    topLeft = Offset(0f, at - core / 2f),
    size = Size(size.width, core),
  )
}
