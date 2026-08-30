package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.LockStore
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  صفحهٔ قفل — پیش از هر چیزِ دیگر.
 *
 *  صفحه‌کلیدِ خودش را دارد و از صفحه‌کلیدِ گوشی استفاده نمی‌کند: رمزِ چهار
 *  رقمی با یک صفحه‌کلیدِ کاملِ متنی، هم کند است هم روی صفحهٔ کوچک نصفِ
 *  صفحه را می‌گیرد.
 */
@Composable
fun AppLockScreen(onUnlocked: () -> Unit) {
  val context = LocalContext.current
  val lock = remember { LockStore(context) }
  val colors = Shop.colors

  var pin by remember { mutableStateOf("") }
  var wrong by remember { mutableStateOf(false) }

  /*
   *  اثر انگشت — میان‌بُر، نه جایگزینِ رمز.
   *
   *  به‌محضِ باز شدنِ صفحه یک بار خودش می‌پرسد؛ کسی که هر روز برنامه را
   *  باز می‌کند، نباید هر بار دکمه بزند. اگر انصراف داد، صفحه‌کلیدِ رمز
   *  همان‌جا هست و اگر گوشی حسگر نداشته باشد، اصلاً چیزی نشان داده
   *  نمی‌شود.
   */
  val activity = context as? androidx.fragment.app.FragmentActivity
  val canFinger = remember(activity) {
    activity != null && ir.vil3ntec.tohid.data.Fingerprint.available(context)
  }
  var asked by rememberSaveable { mutableStateOf(false) }

  fun askFinger() {
    val where = activity ?: return
    ir.vil3ntec.tohid.data.Fingerprint.ask(where, onOk = onUnlocked)
  }

  LaunchedEffect(canFinger) {
    if (canFinger && !asked) { asked = true; askFinger() }
  }

  // تکانِ کوتاه هنگامِ رمزِ غلط — پیامِ متنی را کسی که عجله دارد نمی‌خواند
  val shake by animateFloatAsState(
    targetValue = if (wrong) 1f else 0f,
    animationSpec = keyframes {
      durationMillis = 320
      0f at 0; 1f at 60; -1f at 120; 0.6f at 190; 0f at 320
    },
    label = "shake",
  )

  fun submit(value: String) {
    if (lock.matches(value)) {
      onUnlocked()
    } else {
      wrong = true
      pin = ""
    }
  }

  LaunchedEffect(wrong) {
    if (wrong) { kotlinx.coroutines.delay(700); wrong = false }
  }

  Box(
    Modifier.fillMaxSize().background(colors.bg),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      Modifier.widthIn(max = 340.dp).padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      LockMark()
      Spacer(Modifier.height(18.dp))
      Text(
        "رمز برنامه",
        style = MaterialTheme.typography.titleMedium,
        color = colors.text,
        fontWeight = FontWeight.Bold,
      )
      Spacer(Modifier.height(6.dp))
      Text(
        if (wrong) "رمز درست نیست" else "برای باز شدن دکان، رمز را بزنید",
        style = MaterialTheme.typography.bodySmall,
        color = if (wrong) colors.danger else colors.muted,
        textAlign = TextAlign.Center,
      )

      Spacer(Modifier.height(22.dp))

      // نقطه‌ها — به‌اندازهٔ رقم‌هایی که زده شده پر می‌شوند
      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.graphicsLayer { translationX = shake * 14f },
      ) {
        repeat(MAX_PIN) { index ->
          val filled = index < pin.length
          Box(
            Modifier
              .size(if (filled) 15.dp else 13.dp)
              .clip(CircleShape)
              .background(
                when {
                  wrong -> colors.danger
                  filled -> colors.primary
                  else -> colors.surface2
                }
              )
          )
        }
      }

      Spacer(Modifier.height(28.dp))

      Keypad(
        onDigit = { digit ->
          if (pin.length < MAX_PIN) {
            pin += digit
            if (pin.length == MAX_PIN) submit(pin)
          }
        },
        onBack = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
      )

      if (canFinger) {
        Spacer(Modifier.height(18.dp))
        Row(
          Modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable { askFinger() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            Icons.Filled.Fingerprint,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(20.dp),
          )
          Text(
            "باز کردن با اثر انگشت",
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
}

/** نشانِ قفل، با هاله‌ای که نفس می‌کشد */
@Composable
private fun LockMark() {
  val colors = Shop.colors
  val motion = rememberInfiniteTransition(label = "lock")
  val breathe by motion.animateFloat(
    initialValue = 0.25f,
    targetValue = 0.6f,
    animationSpec = infiniteRepeatable(
      tween(if (Motion.enabled) 2200 else 1, easing = EaseInOutSine),
      RepeatMode.Reverse,
    ),
    label = "breathe",
  )
  Box(contentAlignment = Alignment.Center) {
    Box(
      Modifier
        .size(84.dp)
        .clip(RoundedCornerShape(28.dp))
        .background(colors.primary.copy(alpha = breathe * 0.35f))
    )
    Box(
      Modifier
        .size(62.dp)
        .clip(RoundedCornerShape(21.dp))
        .background(colors.surface)
        .border(1.dp, colors.primary.copy(alpha = 0.45f), RoundedCornerShape(21.dp)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Filled.Lock,
        contentDescription = null,
        tint = colors.primary,
        modifier = Modifier.size(27.dp),
      )
    }
  }
}

@Composable
private fun Keypad(onDigit: (Char) -> Unit, onBack: () -> Unit) {
  Column(
    verticalArrangement = Arrangement.spacedBy(10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    listOf("123", "456", "789").forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        row.forEach { digit -> Key(digit.toString()) { onDigit(digit) } }
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Spacer(Modifier.size(KEY))
      Key("0") { onDigit('0') }
      Key(icon = Icons.Filled.Backspace, onClick = onBack)
    }
  }
}

private val KEY = 68.dp

@Composable
private fun Key(
  text: String = "",
  icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
  onClick: () -> Unit,
) {
  val colors = Shop.colors
  Box(
    Modifier
      .size(KEY)
      .clip(RoundedCornerShape(22.dp))
      .background(colors.surface)
      .border(1.dp, colors.border, RoundedCornerShape(22.dp))
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    if (icon != null) {
      Icon(icon, contentDescription = "پاک کردن", tint = colors.muted, modifier = Modifier.size(21.dp))
    } else {
      Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = colors.text,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

const val MAX_PIN = 4
