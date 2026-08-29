package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Space
import ir.vil3ntec.tohid.ui.theme.glassSurface

/**
 *  پیام‌های برنامه — کارتی که از پایینِ صفحه بالا می‌آید.
 *
 *  نوارِ پیشینِ متریال یک خطِ باریکِ تیره بود که هم به سبکِ بقیهٔ برنامه
 *  نمی‌خورد و هم پشتِ نوارِ ناوبری می‌رفت. حالا همان چیزی است که کاربر
 *  انتظار دارد: یک کارتِ گِرد که از پایین می‌لغزد و می‌آید.
 *
 *  رنگش را از خودِ متن می‌فهمد — پیامِ خطا قرمز، پیامِ موفقیت سبز، بقیه
 *  آبی. نوشتنِ نوعِ پیام در همهٔ صدها جای برنامه، کارِ بی‌هوده‌ای بود؛
 *  این‌طوری هیچ‌کدام از صداکردن‌های موجود عوض نمی‌شوند.
 */
@Composable
fun TohidSnackbar(host: SnackbarHostState, modifier: Modifier = Modifier) {
  SnackbarHost(hostState = host, modifier = modifier) { data ->
    val message = data.visuals.message
    val tint = when {
      message.contains("نشد") || message.contains("نیست") || message.contains("نبود") ||
        message.contains("ناموفق") || message.contains("خطا") || message.contains("معتبر") ->
        Shop.colors.danger
      message.contains("شد") || message.contains("موفق") -> Shop.colors.success
      else -> Shop.colors.primary
    }

    AnimatedVisibility(
      visible = true,
      enter = slideInVertically(tween(if (Motion.enabled) 280 else 0, easing = FastOutSlowInEasing)) { it } +
        fadeIn(tween(if (Motion.enabled) 200 else 0)),
      exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
    ) {
      Row(
        Modifier
          .fillMaxWidth()
          .padding(horizontal = Space.sm, vertical = Space.xs)
          .glassSurface(
            shape = Shape.card,
            tint = Shop.colors.surface,
            sheen = Shop.colors.sheen,
            border = Shop.colors.border,
            strong = true,
            glow = Shop.colors.glow,
          )
          .padding(start = Space.md, end = Space.xs, top = Space.sm, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // نوارِ رنگیِ کنارِ پیام: نوعِ خبر را پیش از خواندنِ متن می‌گوید
        Box(
          Modifier
            .width(4.dp)
            .height(34.dp)
            .clip(Shape.badge)
            .background(tint)
        )
        Spacer(Modifier.width(Space.sm))
        Text(
          message,
          style = MaterialTheme.typography.bodyMedium,
          color = Shop.colors.text,
          modifier = Modifier.weight(1f),
        )
        val action = data.visuals.actionLabel
        if (action != null) {
          TextButton(onClick = { data.performAction() }) {
            Text(action, color = Shop.colors.primary, fontWeight = FontWeight.Bold)
          }
        } else {
          IconButton(onClick = { data.dismiss() }, modifier = Modifier.size(36.dp)) {
            Icon(
              Icons.Filled.Close,
              contentDescription = "بستن",
              tint = Shop.colors.muted2,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }
    }
  }
}
