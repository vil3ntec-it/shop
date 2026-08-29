package ir.vil3ntec.tohid.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
            // کادرِ راهنما، همان چیزی که در صفحهٔ فروش هست
            Box(
              Modifier
                .fillMaxWidth(0.72f)
                .height(120.dp)
                .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(Radius.sm)),
            ) {
              val sweep = rememberInfiniteTransition(label = "scan")
              val y by sweep.animateFloat(
                initialValue = 0.12f,
                targetValue = 0.88f,
                animationSpec = infiniteRepeatable(
                  tween(if (Motion.enabled) 1600 else 1, easing = LinearEasing),
                  RepeatMode.Reverse,
                ),
                label = "scanline",
              )
              Box(
                Modifier
                  .fillMaxWidth()
                  .height(2.dp)
                  .align(Alignment.TopStart)
                  .graphicsLayer { translationY = y * 120.dp.toPx() }
                  .background(Shop.colors.primary)
              )
            }
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
