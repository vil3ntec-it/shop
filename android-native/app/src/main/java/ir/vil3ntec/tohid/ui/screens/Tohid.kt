package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.money
import androidx.compose.ui.draw.shadow
import ir.vil3ntec.tohid.ui.theme.Elevation
import ir.vil3ntec.tohid.ui.theme.glassSurface
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Space

/**
 *  اجزای مشترکِ رابط کاربری — یک جا، برای همهٔ صفحه‌ها.
 *
 *  چرا این فایل هست: تا حالا هر صفحه دکمه و کارت و کادرِ خودش را
 *  می‌ساخت. نتیجه‌اش این بود که دو دکمهٔ «حذف» در دو صفحه دو رنگِ کمی
 *  متفاوت داشتند و کسی هم متوجه نمی‌شد. حالا هر صفحهٔ تازه از همین‌ها
 *  استفاده می‌کند و اگر روزی گِردیِ دکمه‌ها عوض شود، یک جا عوض می‌شود.
 *
 *  همه‌شان راست‌به‌چپ‌اند و اندازهٔ لمسشان دستِ‌کم ۴۸ نقطه است — انگشتِ
 *  کسی که وسطِ فروش عجله دارد، روی دکمهٔ ۳۰ نقطه‌ای نمی‌نشیند.
 */

/* ============================== دکمه‌ها ============================== */

/** دکمهٔ اصلی — کارِ اصلیِ هر صفحه، در هر صفحه فقط یکی */
@Composable
fun TohidButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  icon: ImageVector? = null,
  busy: Boolean = false,
) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val colors = Shop.colors

  Button(
    onClick = onClick,
    enabled = enabled && !busy,
    shape = Shape.button,
    interactionSource = interaction,
    contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
    colors = ButtonDefaults.buttonColors(
      containerColor = Color.Transparent,
      contentColor = Color(0xFF04121F),
      disabledContainerColor = Color.Transparent,
      disabledContentColor = colors.muted2,
    ),
    modifier = modifier
      .heightIn(min = 52.dp)
      .pressScale(pressed)
      .then(if (enabled && !busy) Modifier.softGlow(Shape.button, colors.glow) else Modifier)
      .background(
        brush = if (enabled && !busy) {
          Brush.horizontalGradient(listOf(colors.primaryDark, colors.primary))
        } else {
          Brush.horizontalGradient(listOf(colors.surface2, colors.surface2))
        },
        shape = Shape.button,
      ),
  ) {
    if (busy) {
      CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
    } else {
      if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.xs))
      }
      Text(text, style = MaterialTheme.typography.titleSmall)
    }
  }
}

/** دکمهٔ دوم — کاری که هست ولی اصلی نیست */
@Composable
fun TohidSecondaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  icon: ImageVector? = null,
) {
  OutlinedButton(
    onClick = onClick,
    enabled = enabled,
    shape = Shape.button,
    colors = ButtonDefaults.outlinedButtonColors(
      containerColor = Shop.colors.surface2.copy(alpha = 0.6f),
      contentColor = Shop.colors.text,
    ),
    border = androidx.compose.foundation.BorderStroke(0.8.dp, Shop.colors.border),
    modifier = modifier.heightIn(min = 52.dp),
  ) {
    if (icon != null) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(Space.xs))
    }
    Text(text, style = MaterialTheme.typography.titleSmall)
  }
}

/** دکمهٔ خطرناک — حذف و پاک‌سازی. عمداً پُر نیست: پُر بودن دعوت به زدن است */
@Composable
fun TohidDangerButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  OutlinedButton(
    onClick = onClick,
    enabled = enabled,
    shape = Shape.button,
    colors = ButtonDefaults.outlinedButtonColors(
      containerColor = Shop.colors.dangerTint,
      contentColor = Shop.colors.danger,
    ),
    border = androidx.compose.foundation.BorderStroke(0.8.dp, Shop.colors.danger.copy(alpha = 0.45f)),
    modifier = modifier.heightIn(min = 52.dp),
  ) {
    Text(text, style = MaterialTheme.typography.titleSmall)
  }
}

/* =============================== کارت‌ها =============================== */

/** کارتِ پایه — همان `.panel` سایت */
@Composable
fun TohidCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  glow: Boolean = false,
  content: @Composable ColumnScope.() -> Unit,
) {
  val colors = Shop.colors
  Column(
    modifier
      .then(if (glow) Modifier.softGlow(Shape.card, colors.glow) else Modifier)
      .glassSurface(Shape.card, colors.surface, colors.sheen, colors.border)
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(Space.md),
    content = content,
  )
}

/**
 *  هالهٔ آبیِ زیرِ یک سطح.
 *
 *  سایهٔ سیاهِ معمولی روی زمینهٔ سرمه‌ای دیده نمی‌شود و فقط عنصر را کدر
 *  می‌کند. به‌جایش یک هالهٔ آبیِ کم‌رنگ زیرِ کارت گذاشته می‌شود تا انگار
 *  خودش کمی نور دارد.
 */
fun Modifier.softGlow(shape: androidx.compose.ui.graphics.Shape, color: Color): Modifier =
  this.shadow(
    elevation = 18.dp,
    shape = shape,
    ambientColor = color,
    spotColor = color,
  )

/** کاشیِ عدد — یک عدد بزرگ و یک برچسب */
@Composable
fun TohidStatCard(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  tint: Color = Shop.colors.primary,
  hint: String? = null,
  onClick: (() -> Unit)? = null,
) {
  TohidCard(modifier = modifier, onClick = onClick, glow = true) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
    Spacer(Modifier.height(Space.xxs))
    Text(
      value,
      style = MaterialTheme.typography.headlineSmall,
      color = tint,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (hint != null) {
      Spacer(Modifier.height(2.dp))
      Text(hint, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    }
  }
}

/* ============================ کادرهای ورودی ============================ */

/** کادرِ متن — با برچسب، راهنما و خطا در یک جا */
@Composable
fun TohidTextField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  placeholder: String? = null,
  supporting: String? = null,
  error: String? = null,
  singleLine: Boolean = true,
  keyboardOptions: androidx.compose.foundation.text.KeyboardOptions =
    androidx.compose.foundation.text.KeyboardOptions.Default,
  trailing: @Composable (() -> Unit)? = null,
) {
  Column(modifier) {
    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      label = { Text(label) },
      placeholder = placeholder?.let { { Text(it) } },
      isError = error != null,
      singleLine = singleLine,
      shape = Shape.field,
      keyboardOptions = keyboardOptions,
      trailingIcon = trailing,
      modifier = Modifier.fillMaxWidth(),
    )
    val note = error ?: supporting
    if (note != null) {
      Text(
        note,
        style = MaterialTheme.typography.labelSmall,
        color = if (error != null) Shop.colors.danger else Shop.colors.muted2,
        modifier = Modifier.padding(start = Space.sm, top = Space.xxs),
      )
    }
  }
}

/** نوارِ جستجوی مشترک */
@Composable
fun TohidSearchBar(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    placeholder = { Text(placeholder) },
    singleLine = true,
    shape = Shape.field,
    leadingIcon = {
      Icon(Icons.Filled.Search, contentDescription = null, tint = Shop.colors.muted)
    },
    trailingIcon = if (value.isEmpty()) null else {
      {
        IconButton(onClick = { onValueChange("") }) {
          Icon(Icons.Filled.Close, contentDescription = "پاک کردن جستجو", tint = Shop.colors.muted)
        }
      }
    },
    modifier = modifier.fillMaxWidth(),
  )
}

/** انتخابِ یکی از چند گزینه — کشویی */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TohidDropdown(
  label: String,
  options: List<String>,
  selected: String,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
  placeholder: String = "— انتخاب کنید —",
) {
  var open by remember { mutableStateOf(false) }
  ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
    OutlinedTextField(
      value = selected.ifBlank { placeholder },
      onValueChange = {},
      readOnly = true,
      label = { Text(label) },
      shape = Shape.field,
      trailingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
      modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
    )
    ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      options.forEach { option ->
        DropdownMenuItem(
          text = { Text(option) },
          onClick = { onSelect(option); open = false },
        )
      }
    }
  }
}

/** ردیفِ تراشه‌های فیلتر، افقی اسکرول‌شونده */
@Composable
fun TohidFilterRow(
  options: List<Pair<String, String>>,
  selected: String,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(Space.xxs + 2.dp),
  ) {
    options.forEach { (id, label) ->
      TohidFilterChip(label = label, selected = selected == id, onClick = { onSelect(id) })
    }
  }
}

@Composable
fun TohidFilterChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(label) },
    shape = Shape.chip,
    modifier = modifier.heightIn(min = 40.dp),
  )
}

/* ============================== حالت‌ها ============================== */

/**
 *  صفحهٔ خالی.
 *
 *  «چیزی نیست» به‌تنهایی کاربر را سرِ دوراهی می‌گذارد. هر حالتِ خالی
 *  می‌گوید اینجا چه چیزی قرار است باشد و دکمه‌اش را همان‌جا می‌دهد.
 */
@Composable
fun TohidEmptyState(
  title: String,
  description: String,
  icon: ImageVector? = null,
  actionText: String? = null,
  onAction: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier.fillMaxWidth().padding(vertical = Space.xxl, horizontal = Space.md),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (icon != null) {
      Box(
        Modifier.size(56.dp).clip(Shape.card).background(Shop.colors.surface2),
        contentAlignment = Alignment.Center,
      ) {
        Icon(icon, contentDescription = null, tint = Shop.colors.muted, modifier = Modifier.size(26.dp))
      }
      Spacer(Modifier.height(Space.sm))
    }
    Text(title, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text, textAlign = TextAlign.Center)
    Spacer(Modifier.height(Space.xxs))
    Text(
      description,
      style = MaterialTheme.typography.bodySmall,
      color = Shop.colors.muted,
      textAlign = TextAlign.Center,
    )
    if (actionText != null && onAction != null) {
      Spacer(Modifier.height(Space.md))
      TohidButton(text = actionText, onClick = onAction)
    }
  }
}

/** حالتِ خطا — چه شد، چرا، و دکمهٔ تلاش دوباره */
@Composable
fun TohidErrorState(
  title: String,
  description: String,
  onRetry: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier.fillMaxWidth().padding(vertical = Space.xl, horizontal = Space.md),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(
      Icons.Filled.ErrorOutline,
      contentDescription = null,
      tint = Shop.colors.danger,
      modifier = Modifier.size(30.dp),
    )
    Spacer(Modifier.height(Space.xs))
    Text(title, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text, textAlign = TextAlign.Center)
    Spacer(Modifier.height(Space.xxs))
    Text(
      description,
      style = MaterialTheme.typography.bodySmall,
      color = Shop.colors.muted,
      textAlign = TextAlign.Center,
    )
    if (onRetry != null) {
      Spacer(Modifier.height(Space.md))
      TohidSecondaryButton(text = "تلاش دوباره", onClick = onRetry)
    }
  }
}

/**
 *  اسکلتِ در حال بارگذاری.
 *
 *  چرخِ چرخان وسطِ صفحه، جای خالی را نشان نمی‌دهد؛ اسکلت می‌گوید چند
 *  ردیف قرار است بیاید و کجا، پس صفحه موقعِ آمدنِ داده نمی‌پرد.
 */
@Composable
fun TohidLoadingState(rows: Int = 4, modifier: Modifier = Modifier) {
  val colors = Shop.colors
  val shimmer = rememberInfiniteTransition(label = "shimmer")
  val slide by shimmer.animateFloat(
    initialValue = -1f,
    targetValue = 2f,
    animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
    label = "slide",
  )
  val x = if (Motion.enabled) slide else 0.5f

  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
    repeat(rows) {
      Box(
        Modifier
          .fillMaxWidth()
          .height(76.dp)
          .clip(Shape.card)
          .background(colors.surface)
          .drawBehind {
            // یک نوارِ نورِ یخی که از راست به چپ می‌گذرد
            val w = size.width
            drawRect(
              brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, colors.primary.copy(alpha = 0.16f), Color.Transparent),
                startX = x * w - w * 0.4f,
                endX = x * w + w * 0.4f,
              )
            )
          }
          .semantics { contentDescription = "در حال بارگذاری" }
      )
    }
  }
}

/**
 *  عددی که به مقدارِ تازه‌اش می‌لغزد، نه اینکه بپرد.
 *
 *  وقتی فروشی ثبت می‌شود و «فروش امروز» یک‌آن عوض شود، چشم متوجه نمی‌شود
 *  چه چیزی تغییر کرد. شمردنِ کوتاه، نگاه را همان‌جا نگه می‌دارد.
 */
@Composable
fun animatedMoney(target: Double): Double {
  if (!Motion.enabled) return target
  val value by androidx.compose.animation.core.animateFloatAsState(
    targetValue = target.toFloat(),
    animationSpec = tween(650, easing = androidx.compose.animation.core.FastOutSlowInEasing),
    label = "count",
  )
  return value.toDouble()
}

/* ============================== ریزه‌کاری ============================== */

@Composable
fun TohidSectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  actionText: String? = null,
  onAction: (() -> Unit)? = null,
) {
  Row(
    modifier.fillMaxWidth().padding(bottom = Space.xs),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
    if (actionText != null && onAction != null) {
      TextButton(onClick = onAction) { Text(actionText, color = Shop.colors.primary) }
    }
  }
}

/** نشانِ رنگی — «موجودی کم»، «نسیه»، «لغو‌شده» */
@Composable
fun TohidBadge(
  text: String,
  tint: Color,
  fill: Color,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier.clip(Shape.badge).background(fill).padding(horizontal = Space.xs, vertical = 3.dp),
  ) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.Bold)
  }
}

/**
 *  مبلغ.
 *
 *  عدد همیشه چپ‌به‌راست نوشته می‌شود، حتی وسطِ متنِ فارسی: رقمِ بلند در
 *  جهتِ راست‌به‌چپ وارونه دیده می‌شود و فروشنده اشتباه می‌خواند. واحد هم
 *  کوچک‌تر از خودِ عدد است تا نگاه اول روی رقم بیفتد.
 */
@Composable
fun TohidMoneyText(
  amount: Double,
  modifier: Modifier = Modifier,
  tint: Color = Shop.colors.text,
  style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleSmall,
  currency: String = "افغانی",
  bold: Boolean = true,
) {
  Row(modifier, verticalAlignment = Alignment.Bottom) {
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
      Text(
        money(amount),
        style = style,
        color = tint,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
      )
    }
    if (currency.isNotBlank()) {
      Spacer(Modifier.width(3.dp))
      Text(currency, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
  }
}

/** ردیفِ یک تراکنش — عنوان، شرح، و مبلغِ رنگی در طرفِ دیگر */
@Composable
fun TohidTransactionRow(
  title: String,
  subtitle: String,
  amount: Double,
  tint: Color,
  modifier: Modifier = Modifier,
  currency: String = "افغانی",
  onClick: (() -> Unit)? = null,
  trailing: @Composable (() -> Unit)? = null,
) {
  Row(
    modifier
      .fillMaxWidth()
      .clip(Shape.chip)
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(horizontal = Space.sm, vertical = Space.sm),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(
        title,
        style = MaterialTheme.typography.bodyMedium,
        color = Shop.colors.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (subtitle.isNotBlank()) {
        Text(
          subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    TohidMoneyText(amount = amount, tint = tint, currency = currency)
    if (trailing != null) {
      Spacer(Modifier.width(Space.xxs))
      trailing()
    }
  }
}

/** پنجرهٔ تأیید — یک شکل برای همهٔ «مطمئنی؟»های برنامه */
@Composable
fun TohidConfirmDialog(
  title: String,
  message: String,
  confirmText: String = "تأیید",
  dismissText: String = "انصراف",
  danger: Boolean = false,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  extra: @Composable (() -> Unit)? = null,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Shop.colors.surface,
    shape = Shape.dialog,
    title = { Text(title, color = Shop.colors.text) },
    text = {
      Column {
        Text(message, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
        if (extra != null) {
          Spacer(Modifier.height(Space.sm))
          extra()
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(confirmText, color = if (danger) Shop.colors.danger else Shop.colors.primary)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
  )
}

/** شیتِ پایین — یک شکل برای همهٔ فرم‌های متوسط */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TohidBottomSheet(
  title: String,
  onDismiss: () -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = Shop.colors.bg,
    shape = Shape.sheet,
  ) {
    Column(Modifier.fillMaxWidth().padding(start = Space.lg, end = Space.lg, bottom = Space.xl)) {
      Text(title, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
      Spacer(Modifier.height(Space.sm))
      content()
    }
  }
}
