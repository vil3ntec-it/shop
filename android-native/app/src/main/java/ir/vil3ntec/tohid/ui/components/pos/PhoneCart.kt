package ir.vil3ntec.tohid.ui.components.pos

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.PhotoPrefs
import ir.vil3ntec.tohid.ui.theme.PhoneSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 *  سبدِ گوشی — نوارِ تیرهٔ پایین و برگهٔ تیرهٔ تمام‌قد (phone-ui، تصویرهای مرجعِ ۵ و ۶).
 *
 *  فقط چیدمان است: داده، حساب و کارها از صفحهٔ صدازننده می‌آیند (همان `CartStore`،
 *  `SalesEngine` و `CheckoutDialog`ِ پیشین). رنگ‌ها از `PhoneSheet`، انیمیشن با
 *  `Motion.enabled` («کاهشِ حرکت» ⇐ بی‌حرکت). فقط گوشی (`Screen.PHONE`)؛ تبلت و
 *  کامپیوتر همان سبدِ پیشین را دارند.
 */

/**
 *  فقط برای عکسِ خودکارِ برنامهٔ کامپیوتر (`DevShot` ⇐ `phone:cart-open`)، مثلِ `PosShot`:
 *  برگهٔ سبدِ «انتخاب محصول» را باز می‌کند. در نسخهٔ دستِ کاربر کسی آن را نمی‌گذارد.
 */
object PhoneCartShot {
  val open = mutableStateOf(false)
}

/**
 *  برگهٔ سبد همین حالا باز است؟ — `AppRoot` تا وقتی باز است نوارِ پایینِ قرصی را
 *  نمی‌کشد تا برگه مثلِ تصویرِ مرجعِ ۶ تا تهِ صفحه برسد و چیزی رویش نیفتد.
 */
object PhoneCartUi {
  val sheetOpen = mutableStateOf(false)
}

/** عکسِ گردِ یک قلم، یا حرفِ اولِ نامش وقتی عکسی نیست (یا عکس‌ها خاموش‌اند) */
@Composable
fun PhoneCartThumb(thumb: CartSummary.Thumb, size: Dp = 36.dp) {
  val context = LocalContext.current
  val s = PhoneSheet.colors
  PhotoPrefs.load(context)
  val show = PhotoPrefs.show.value
  val synced = ir.vil3ntec.tohid.sync.PhotoSync.version.value
  var bitmap by remember(thumb.id, synced, show) { mutableStateOf<android.graphics.Bitmap?>(null) }
  LaunchedEffect(thumb.id, synced, show) {
    bitmap = if (show && thumb.hasProduct) {
      withContext(Dispatchers.IO) { runCatching { PhotoStore.load(context, thumb.id) }.getOrNull() }
    } else null
  }
  Box(
    Modifier
      .size(size)
      .clip(CircleShape)
      .background(s.onSheet)
      .border(2.dp, s.sheet, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    val b = bitmap
    if (b != null) {
      Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
      Text(thumb.initials, color = s.sheet, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
  }
}

/**
 *  نوارِ تیرهٔ جمع‌شده — «سبد»، عکسِ گردِ تازه‌ترین قلم‌ها، مبلغ و دایرهٔ شمار.
 *  کلِ نوار یک دکمه است (≥ ۶۴dp) و برگه را باز می‌کند.
 */
@Composable
fun PhoneCartBar(model: CartSummary.Model, onOpen: () -> Unit, modifier: Modifier = Modifier) {
  val s = PhoneSheet.colors
  val shape = RoundedCornerShape(28.dp)
  val spoken = "سبد، ${plain(model.count)} قلم، ${money(model.total)} ؋"
  Row(
    modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 8.dp)
      .shadow(14.dp, shape, clip = false)
      .clip(shape)
      .background(s.sheet)
      .clickable(role = Role.Button, onClickLabel = "باز کردنِ سبد", onClick = onOpen)
      .semantics(mergeDescendants = true) { contentDescription = spoken }
      .heightIn(min = 64.dp)
      .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("سبد", style = MaterialTheme.typography.titleMedium, color = s.onSheet, fontWeight = FontWeight.Bold)
    Spacer(Modifier.width(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      model.thumbs.forEach { PhoneCartThumb(it, 36.dp) }
    }
    if (model.more > 0) {
      Spacer(Modifier.width(6.dp))
      Text("+${plain(model.more)}", style = MaterialTheme.typography.labelMedium, color = s.onSheetMuted)
    }
    Spacer(Modifier.weight(1f))
    Text(
      "${money(model.total)} ؋",
      style = MaterialTheme.typography.titleMedium,
      color = s.onSheet,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
    )
    Spacer(Modifier.width(10.dp))
    Box(
      Modifier.size(44.dp).clip(CircleShape).background(s.accent),
      contentAlignment = Alignment.Center,
    ) {
      Text(plain(model.count), style = MaterialTheme.typography.titleMedium, color = s.onAccent, fontWeight = FontWeight.Bold)
    }
  }
}

/**
 *  نوار و برگه روی هم — باید آخرین فرزندِ `Box`ِ تمام‌صفحهٔ صفحهٔ فروش باشد.
 *
 *  بسته: فقط نوار (اگر `model.visible`). باز: برگهٔ تیره از پایین بالا می‌آید و
 *  بالای صفحه (آن تکه که دیده می‌ماند) گوشه‌های پایینِ گرد می‌گیرد — دو گوشهٔ
 *  تیره روی همان مرز کشیده می‌شوند، پس صفحه دوباره چیده نمی‌شود و جای اسکرولش
 *  نمی‌پرد. زدنِ بالای صفحه یا «برگشت» برگه را می‌بندد.
 */
@Composable
fun BoxScope.PhoneCartLayer(
  model: CartSummary.Model,
  open: Boolean,
  onOpen: () -> Unit,
  onClose: () -> Unit,
  sheet: @Composable ColumnScope.() -> Unit,
) {
  val s = PhoneSheet.colors
  val ms = if (Motion.enabled) 320 else 0

  AnimatedVisibility(
    visible = model.visible && !open,
    enter = if (Motion.enabled) slideInVertically(tween(220)) { it } + fadeIn(tween(220)) else fadeIn(tween(0)),
    exit = if (Motion.enabled) slideOutVertically(tween(180)) { it } + fadeOut(tween(180)) else fadeOut(tween(0)),
    modifier = Modifier.align(Alignment.BottomCenter),
  ) {
    PhoneCartBar(model, onOpen)
  }

  val showing = open && model.visible
  LaunchedEffect(showing) { PhoneCartUi.sheetOpen.value = showing }
  DisposableEffect(Unit) { onDispose { PhoneCartUi.sheetOpen.value = false } }

  val p by animateFloatAsState(
    targetValue = if (showing) 1f else 0f,
    animationSpec = tween(ms, easing = FastOutSlowInEasing),
    label = "cartSheet",
  )
  if (p <= 0f) return
  BackHandler(enabled = open, onBack = onClose)

  BoxWithConstraints(Modifier.matchParentSize()) {
    val full = maxHeight
    val sheetH = full * 0.86f
    val visible = full - sheetH * p
    val radius = 28.dp
    //  بالای صفحه: زدنش برگه را می‌بندد
    Box(
      Modifier
        .fillMaxWidth()
        .height(visible)
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClickLabel = "بستنِ سبد",
          onClick = onClose,
        ),
    )
    //  دو گوشهٔ تیره روی مرز ⇐ گوشه‌های پایینِ گردِ صفحه
    Canvas(
      Modifier
        .fillMaxWidth()
        .height(radius)
        .offset { IntOffset(0, (visible - radius).toPx().roundToInt()) },
    ) {
      val r = size.height
      fun corner(left: Boolean) {
        val x = if (left) 0f else size.width - r
        val square = Path().apply { addRect(Rect(x, 0f, x + r, r)) }
        val round = Path().apply {
          addOval(Rect(Offset(if (left) x else x - r, -r), androidx.compose.ui.geometry.Size(r * 2, r * 2)))
        }
        drawPath(Path.combine(PathOperation.Difference, square, round), s.sheet.copy(alpha = p))
      }
      corner(true); corner(false)
    }
    Column(
      Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .height(sheetH)
        .offset { IntOffset(0, (sheetH * (1f - p)).toPx().roundToInt()) }
        .background(s.sheet)
        .windowInsetsPadding(WindowInsets.navigationBars)
        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
    ) {
      sheet()
    }
  }
}

/** سرِ برگه: «سبد» درشت، شمارِ قلم‌ها و دکمهٔ بستن */
@Composable
fun PhoneCartHeader(count: Int, onClose: () -> Unit) {
  val s = PhoneSheet.colors
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
      "سبد",
      style = MaterialTheme.typography.headlineMedium,
      color = s.onSheet,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.width(10.dp))
    Text("${plain(count)} قلم", style = MaterialTheme.typography.bodyMedium, color = s.onSheetMuted)
    Spacer(Modifier.weight(1f))
    IconButton(onClick = onClose) {
      Icon(Icons.Filled.Close, contentDescription = "بستنِ سبد", tint = s.onSheet)
    }
  }
}

/**
 *  یک ردیفِ برگه: عکس، نام، «مقدار × قیمت» و جمعِ ردیف، و − مقدار +.
 *  زدنِ مقدار همان کادرِ «تعداد و قیمت» را باز می‌کند (`onEdit`؛ null ⇐ فقط متن).
 *  وقتی یک پله مانده، «−» سطلِ حذف می‌شود — همان حذفِ پیشین.
 */
@Composable
fun PhoneCartLine(
  thumb: CartSummary.Thumb,
  name: String,
  detail: String,
  qtyText: String,
  lastStep: Boolean,
  onMinus: () -> Unit,
  onPlus: () -> Unit,
  onEdit: (() -> Unit)?,
) {
  val s = PhoneSheet.colors
  Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    PhoneCartThumb(thumb, 44.dp)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(name, style = MaterialTheme.typography.titleSmall, color = s.onSheet, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(detail, style = MaterialTheme.typography.labelMedium, color = s.onSheetMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.width(8.dp))
    Row(
      Modifier.clip(RoundedCornerShape(percent = 50)).background(s.raised),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onMinus) {
        Icon(
          if (lastStep) Icons.Filled.DeleteOutline else Icons.Filled.Remove,
          contentDescription = if (lastStep) "حذفِ $name از سبد" else "یکی کمتر $name",
          tint = s.onSheet,
        )
      }
      Text(
        qtyText,
        style = MaterialTheme.typography.titleSmall,
        color = s.onSheet,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .then(if (onEdit != null) Modifier.clickable(role = Role.Button, onClickLabel = "نوشتنِ مقدار و قیمت", onClick = onEdit) else Modifier)
          .padding(horizontal = 6.dp, vertical = 10.dp),
      )
      IconButton(onClick = onPlus) {
        Icon(Icons.Filled.Add, contentDescription = "یکی بیشتر $name", tint = s.onSheet)
      }
    }
  }
}

/** «به حساب قرض‌دار» روی برگه — همان انتخاب و برداشتنِ قرض‌دارِ نوارِ پیشین */
@Composable
fun PhoneCartDebtor(name: String?, note: String?, onPick: () -> Unit, onClear: () -> Unit) {
  val s = PhoneSheet.colors
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(s.raised)
      .clickable(role = Role.Button, onClick = onPick)
      .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
      .heightIn(min = 48.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Filled.PersonOutline, contentDescription = null, tint = if (name != null) s.accent else s.onSheetMuted)
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
      Text(
        if (name != null) "به حساب: $name" else "به حساب قرض‌دار بگذار",
        style = MaterialTheme.typography.titleSmall,
        color = s.onSheet,
      )
      Text(note ?: "برای فروش نسیه، قرض‌دار را انتخاب کنید", style = MaterialTheme.typography.labelSmall, color = s.onSheetMuted)
    }
    if (name != null) {
      IconButton(onClick = onClear) {
        Icon(Icons.Filled.Close, contentDescription = "حذف قرض‌دار", tint = s.onSheetMuted)
      }
    }
  }
}

/** جمعِ درشت و دکمهٔ اصلیِ تمام‌عرض (رنگِ `accent`ِ برگه) */
@Composable
fun PhoneCartFooter(total: Double, primary: String, onPrimary: () -> Unit, enabled: Boolean = true) {
  val s = PhoneSheet.colors
  Box(Modifier.fillMaxWidth().height(1.dp).background(s.line))
  Spacer(Modifier.height(12.dp))
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text("مجموع", style = MaterialTheme.typography.titleMedium, color = s.onSheetMuted)
    Spacer(Modifier.weight(1f))
    Text("${money(total)} ؋", style = MaterialTheme.typography.headlineMedium, color = s.onSheet, fontWeight = FontWeight.Bold)
  }
  Spacer(Modifier.height(14.dp))
  Box(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 56.dp)
      .clip(RoundedCornerShape(percent = 50))
      .background(if (enabled) s.accent else s.raised)
      .clickable(enabled = enabled, role = Role.Button, onClick = onPrimary),
    contentAlignment = Alignment.Center,
  ) {
    Text(primary, style = MaterialTheme.typography.titleMedium, color = if (enabled) s.onAccent else s.onSheetMuted, fontWeight = FontWeight.Bold)
  }
}
