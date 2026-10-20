package ir.vil3ntec.tohid.ui.components.pos

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.PhotoPrefs
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 *  سبدِ گوشی، نسخهٔ ۳ — بازسازیِ دقیقِ r3 (بسته) و r2 (باز)، آینه‌شده برای راست‌به‌چپ.
 *
 *  ┌ سطحِ روشنِ کالاها (گوشه‌های پایینِ ۳۶) ┐   ← بسته: تا بالای نوار
 *  │  …                                       │
 *  └──────────────────────────────────────────┘
 *     سبد  ◯ ◯ ◯ +۲                       (۴)     ← نوار روی خودِ زمینهٔ سیاه؛ نه قاب، نه قرص
 *
 *  باز: همان سطح با فنر بالا می‌رود تا فقط ۹۶dpِ پایینش دیده بماند، و سبد روی زمینهٔ
 *  سیاه پیدا می‌شود (ردیف‌ها پله‌پله می‌آیند). زدنِ تکهٔ روشنِ بالا، کشیدن به پایین یا
 *  «برگشت» می‌بندد. فقط چیدمان است: داده، حساب و کارها از صفحهٔ صدازننده می‌آیند
 *  (همان `CartStore`، `SalesEngine` و `CheckoutDialog`). رنگ‌ها فقط از `PhoneV3`؛
 *  «کاهشِ حرکت» (`Motion.enabled`) ⇐ همهٔ مدت‌ها صفر.
 */

/**
 *  فقط برای عکسِ خودکارِ برنامهٔ کامپیوتر (`PhonePreview`): سبد را باز می‌کند.
 *  در نسخهٔ دستِ کاربر کسی آن را نمی‌گذارد.
 */
object PhoneCartShot {
  val open = mutableStateOf(false)

  /** ردیفی که −/+ش در عکس باز باشد (شناسهٔ کالا) */
  val expand = mutableStateOf<String?>(null)

  /** نمای کاتالوگ در عکس: true فهرست، false کاشی (ذخیره نمی‌شود) */
  val list = mutableStateOf<Boolean?>(null)

  /** متنِ جستجو در عکس */
  val search = mutableStateOf<String?>(null)

  /** کالایی که صفحه‌کلیدِ مقدارش در عکس باز باشد */
  val keypad = mutableStateOf<String?>(null)
}

/** حالِ صندوقِ گوشی برای `AppRoot` و نوارِ پایین (بی آن‌که این‌جا به آن‌ها دست بخورد). */
object PhoneCartUi {
  /** سبد همین حالا باز است */
  val sheetOpen = mutableStateOf(false)

  /**
   *  صندوقِ تمام‌صفحهٔ گوشی همین حالا روی صفحه است — سربرگ و نوارِ پایینِ برنامه باید
   *  کنار بروند؛ این صفحه سربرگ (برگشت، عنوان، اسکن) و نوارِ سبدِ خودش را دارد (r3).
   */
  val saleActive = mutableStateOf(false)

  /** درخواستِ «اسکن» از بیرون (دکمهٔ سیاهِ بارکد کنارِ نوار): صندوق با دوربینِ باز می‌آید */
  val scanRequest = mutableStateOf(false)
}

/** بلندیِ نوارِ سبد (r3) */
val PHONE_CART_STRIP = 96.dp

/** فاصلهٔ نوار تا لبهٔ پایین، روی فاصلهٔ نوارِ سیستم (r3: سیاهِ زیرِ نوار) */
private val STRIP_BOTTOM = 16.dp

/** کمینهٔ سیاهِ زیرِ نوار و زیرِ دکمهٔ «پرداخت» (r3/r2: محتوای نوار ۴۸dp زیرِ لبهٔ روشن، ~۸۸dp بالای لبهٔ پایین) */
private val BOTTOM_MIN = 39.dp

/** تکهٔ پیدای سطحِ کالاها وقتی سبد باز است (r2) */
private val PEEK = 96.dp

private val SHEET_SPRING = spring<Float>(dampingRatio = 0.86f, stiffness = 420f)

/** عکسِ کالا از دیسک (فقط اگر عکس‌ها روشن‌اند)؛ نبود ⇐ null و جایش حرفِ اول */
@Composable
internal fun rememberProductImage(id: String, wanted: Boolean): ImageBitmap? {
  val context = LocalContext.current
  PhotoPrefs.load(context)
  val show = PhotoPrefs.show.value && wanted
  val synced = ir.vil3ntec.tohid.sync.PhotoSync.version.value
  var image by remember(id, synced, show) { mutableStateOf<ImageBitmap?>(null) }
  LaunchedEffect(id, synced, show) {
    image = if (show) {
      withContext(Dispatchers.IO) { runCatching { PhotoStore.load(context, id)?.asImageBitmap() }.getOrNull() }
    } else null
  }
  return image
}

/** عکسِ گردِ سفیدِ یک قلم (r2/r3)، یا حرفِ اولِ نامش */
@Composable
fun PhoneCartThumb(thumb: CartSummary.Thumb, size: Dp = 42.dp) {
  val v = PhoneV3.colors
  val image = rememberProductImage(thumb.id, thumb.hasProduct)
  Box(
    Modifier.size(size).clip(CircleShape).background(v.onBlack),
    contentAlignment = Alignment.Center,
  ) {
    if (image != null) {
      Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
      //  دایره همیشه سفید است ⇐ حرف همیشه تیره (در تمِ شب هم)
      Text(thumb.initials, color = v.black, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
  }
}

/** پریدنِ چیزِ تازه: از ۰٫۶ با فنر و محوشدن (بند G) */
@Composable
private fun PopIn(content: @Composable () -> Unit) {
  val motion = Motion.enabled
  val a = remember { Animatable(if (motion) 0f else 1f) }
  LaunchedEffect(Unit) {
    if (motion) a.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 520f)) else a.snapTo(1f)
  }
  Box(
    Modifier.graphicsLayer {
      val s = 0.6f + 0.4f * a.value
      scaleX = s; scaleY = s
      alpha = a.value.coerceIn(0f, 1f)
    },
  ) { content() }
}

/**
 *  نوارِ سبد (r3): «سبد»، تا سه عکسِ گردِ تازه‌ترین قلم‌ها، «+N» و دایرهٔ زردِ شمار.
 *  روی خودِ زمینهٔ سیاه است — بی قاب و بی سایه. کلِ نوار یک دکمه است و سبد را باز
 *  می‌کند. `pulse` با هر افزودن بالا می‌رود ⇐ دایرهٔ شمار و نوار کمی می‌پرند.
 */
@Composable
fun PhoneCartStrip(model: CartSummary.Model, pulse: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
  val v = PhoneV3.colors
  val density = LocalDensity.current
  val spoken = "سبد، ${plain(model.count)} قلم، ${PhoneSaleText.amount(model.total)}"
  val bump = remember { Animatable(1f) }
  val first = remember { pulse }
  LaunchedEffect(pulse) {
    if (pulse == first || !Motion.enabled) return@LaunchedEffect
    bump.snapTo(1.2f)
    bump.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 650f))
  }
  Row(
    modifier
      .fillMaxWidth()
      .height(PHONE_CART_STRIP)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        role = Role.Button,
        onClickLabel = "باز کردنِ سبد",
        onClick = onOpen,
      )
      .semantics(mergeDescendants = true) { contentDescription = spoken }
      .graphicsLayer { translationY = -(bump.value - 1f) * with(density) { 22.dp.toPx() } }
      .padding(start = 36.dp, end = 21.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("سبد", color = v.onBlack, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    Spacer(Modifier.width(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
      model.thumbs.forEach { t -> key(t.id) { PopIn { PhoneCartThumb(t, 42.dp) } } }
    }
    if (model.more > 0) {
      Spacer(Modifier.width(10.dp))
      Text("+${plain(model.more)}", color = v.onBlackMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
    Spacer(Modifier.weight(1f))
    Box(
      Modifier
        .size(52.dp)
        .graphicsLayer { scaleX = bump.value; scaleY = bump.value }
        .clip(CircleShape)
        .background(v.cart),
      contentAlignment = Alignment.Center,
    ) {
      Text(plain(model.count), color = v.onCart, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
  }
}

/**
 *  قابِ صندوقِ گوشی: زمینهٔ سیاه، سطحِ کالاها با گوشه‌های پایینِ گرد، نوارِ سبد، و
 *  سبدِ باز زیرِ سطح. تمام‌صفحه است؛ صدازننده هیچ سربرگ و نوارِ دیگری رویش نمی‌گذارد.
 *
 *  @param catalog سطحِ کالاها؛ `bottomGap` فاصلهٔ لازم زیرِ آخرین کارت است
 *  @param cart سبدِ باز روی سیاه؛ `revealed` برای پله‌پله آمدنِ ردیف‌ها و `dragHandle`
 *    برای سرِ سبد (کشیدن به پایین ⇐ بستن)
 */
@Composable
fun PhoneSaleFrame(
  model: CartSummary.Model,
  open: Boolean,
  onOpen: () -> Unit,
  onClose: () -> Unit,
  pulse: Int,
  catalog: @Composable (bottomGap: Dp) -> Unit,
  cart: @Composable ColumnScope.(revealed: Boolean, dragHandle: Modifier) -> Unit,
) {
  val v = PhoneV3.colors
  val motion = Motion.enabled
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  //  فاصلهٔ نوار/دکمه تا لبهٔ پایین: نوارِ سیستم + ۱۶، ولی نه کمتر از r3
  val bottomSpace = maxOf(BOTTOM_MIN, navBottom + STRIP_BOTTOM)
  val stripShown = model.visible
  val showing = open && stripShown

  DisposableEffect(Unit) {
    PhoneCartUi.saleActive.value = true
    onDispose {
      PhoneCartUi.saleActive.value = false
      PhoneCartUi.sheetOpen.value = false
    }
  }
  LaunchedEffect(showing) { PhoneCartUi.sheetOpen.value = showing }
  BackHandler(enabled = showing, onBack = onClose)

  val progress = remember { Animatable(if (showing) 1f else 0f) }
  LaunchedEffect(showing, motion) {
    val target = if (showing) 1f else 0f
    if (motion) progress.animateTo(target, SHEET_SPRING) else progress.snapTo(target)
  }

  BoxWithConstraints(Modifier.fillMaxSize().background(v.black)) {
    val full = maxHeight
    val stripSpace = PHONE_CART_STRIP + bottomSpace
    val surfaceH by animateDpAsState(
      targetValue = if (stripShown) full - stripSpace else full,
      animationSpec = if (motion) tween(260, easing = FastOutSlowInEasing) else snap(),
      label = "surfaceH",
    )
    val radius by animateDpAsState(
      targetValue = if (stripShown) PhoneV3.sheetRadius else 0.dp,
      animationSpec = if (motion) tween(260) else snap(),
      label = "surfaceRadius",
    )
    val travel = with(density) { (surfaceH - PEEK).toPx() }.coerceAtLeast(1f)
    val p = progress.value

    //  کشیدن: نوار به بالا باز می‌کند، تکهٔ روشن یا سرِ سبد به پایین می‌بندد
    fun settle(velocity: Float) {
      val target = when {
        velocity < -900f -> true
        velocity > 900f -> false
        else -> progress.value > 0.5f
      }
      scope.launch {
        if (motion) progress.animateTo(if (target) 1f else 0f, SHEET_SPRING) else progress.snapTo(if (target) 1f else 0f)
      }
      if (target != showing) { if (target) onOpen() else onClose() }
    }
    val drag = rememberDraggableState { delta ->
      scope.launch { progress.snapTo((progress.value - delta / travel).coerceIn(0f, 1f)) }
    }

    /* ---------------- سبدِ باز، روی خودِ سیاه (r2) ---------------- */
    if (p > 0.001f) {
      Column(
        Modifier
          .fillMaxSize()
          .padding(top = PEEK)
          .graphicsLayer {
            translationY = (1f - p) * with(density) { 56.dp.toPx() }
            alpha = p.coerceIn(0f, 1f)
          }
          .padding(start = 36.dp, end = 28.dp, bottom = bottomSpace),
      ) {
        //  سرِ سبد هم کشیدنی است (به پایین ⇐ بستن) — صدازننده آن را روی عنوان می‌گذارد
        cart(showing, Modifier.draggable(drag, Orientation.Vertical, enabled = showing, onDragStopped = { settle(it) }))
      }
    }

    /* ---------------- نوارِ سبد (r3) ---------------- */
    AnimatedVisibility(
      visible = stripShown && p < 0.5f,
      enter = if (motion) fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 } else fadeIn(snap()),
      exit = fadeOut(if (motion) tween(140) else snap()),
      modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomSpace),
    ) {
      PhoneCartStrip(
        model = model,
        pulse = pulse,
        onOpen = onOpen,
        modifier = Modifier
          .graphicsLayer { alpha = (1f - p * 2f).coerceIn(0f, 1f) }
          .draggable(drag, Orientation.Vertical, enabled = stripShown && !showing, onDragStopped = { settle(it) }),
      )
    }

    /* ---------------- سطحِ کالاها ---------------- */
    val surfaceShape = RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
    Box(
      Modifier
        .fillMaxWidth()
        .height(surfaceH)
        .graphicsLayer {
          translationY = -travel * progress.value
          shape = surfaceShape
          clip = true
        }
        .background(v.catalogPage),
    ) {
      catalog(if (stripShown) 24.dp else 24.dp + navBottom)
      if (p > 0.001f) {
        //  تکهٔ روشنِ پیدا: زدن یا کشیدن به پایین ⇐ بستن؛ کارت‌های زیرش زده نمی‌شوند
        Box(
          Modifier
            .fillMaxSize()
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onClickLabel = "بستنِ سبد",
              onClick = onClose,
            )
            .draggable(drag, Orientation.Vertical, onDragStopped = { settle(it) }),
        )
      }
    }
  }
}

/** آمدنِ پله‌پلهٔ ردیف‌های سبد پس از باز شدن (بند G) */
@Composable
private fun Modifier.reveal(index: Int, revealed: Boolean): Modifier {
  val motion = Motion.enabled
  val a by animateFloatAsState(
    targetValue = if (revealed || !motion) 1f else 0f,
    animationSpec = if (!motion) snap() else if (revealed) tween(240, delayMillis = 90 + index.coerceAtMost(8) * 45) else tween(120),
    label = "reveal",
  )
  val density = LocalDensity.current
  return this.graphicsLayer {
    alpha = a
    translationY = (1f - a) * with(density) { 14.dp.toPx() }
  }
}

/** «سبد» درشتِ سرِ سبدِ باز (r2) */
@Composable
fun PhoneCartTitle() {
  val v = PhoneV3.colors
  Text(
    "سبد",
    color = v.onBlack,
    fontSize = 30.sp,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.semantics { heading() },
  )
}

/**
 *  یک ردیفِ سبدِ باز (r2): عکسِ گرد، «مقدار ×»، نام (دو خط) و جمعِ ردیف به خاکستری.
 *  زدن ⇐ −/+ همین ردیف باز می‌شود (پلهٔ آخر ⇐ سطل)؛ نگه داشتن ⇐ حذف. زدنِ مقدار
 *  کادرِ «تعداد و قیمت» را باز می‌کند (`onEdit`؛ null ⇐ فقط متن).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhoneCartRow(
  row: CartSummary.Row,
  index: Int,
  revealed: Boolean,
  expanded: Boolean,
  onToggle: () -> Unit,
  onMinus: () -> Unit,
  onPlus: () -> Unit,
  onRemove: () -> Unit,
  onEdit: (() -> Unit)?,
) {
  val v = PhoneV3.colors
  val motion = Motion.enabled
  Column(
    Modifier
      .fillMaxWidth()
      .reveal(index, revealed)
      .clip(RoundedCornerShape(18.dp))
      .combinedClickable(
        onClickLabel = if (expanded) "بستنِ مقدار" else "کم و زیاد کردن",
        onLongClickLabel = "حذف از سبد",
        onLongClick = onRemove,
        onClick = onToggle,
      )
      .then(if (motion) Modifier.animateContentSize(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) else Modifier)
      .padding(vertical = 17.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      PhoneCartThumb(row.thumb, 42.dp)
      Spacer(Modifier.width(22.dp))
      Text("${qty(row.quantity)} ×", color = v.onBlack, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
      Spacer(Modifier.width(12.dp))
      Text(
        row.name,
        color = v.onBlack,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(12.dp))
      Text(PhoneSaleText.amount(row.lineTotal), color = v.onBlackMuted, fontSize = 14.sp, maxLines = 1)
    }
    if (expanded) {
      Spacer(Modifier.height(12.dp))
      Row(Modifier.padding(start = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
          "${PhoneSaleText.amount(row.unitPrice)}${if (row.unit.isNotBlank()) " / ${row.unit}" else ""}" +
            if (row.custom && !row.free) " · قیمتِ دستی" else "",
          color = v.onBlackMuted,
          fontSize = 12.sp,
          maxLines = 2,
          modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        PhoneCartStepper(row, onMinus, onPlus, onEdit)
      }
    }
  }
}

@Composable
private fun PhoneCartStepper(row: CartSummary.Row, onMinus: () -> Unit, onPlus: () -> Unit, onEdit: (() -> Unit)?) {
  val v = PhoneV3.colors
  Row(
    Modifier.clip(RoundedCornerShape(percent = 50)).background(v.blackRaised),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CartStepButton(
      if (row.lastStep) Icons.Outlined.DeleteOutline else Icons.Outlined.Remove,
      if (row.lastStep) "حذفِ ${row.name} از سبد" else "یکی کمتر ${row.name}",
      if (row.lastStep) v.negative else v.onBlack,
      onMinus,
    )
    Text(
      qty(row.quantity),
      color = v.onBlack,
      fontSize = 15.sp,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .then(if (onEdit != null) Modifier.clickable(role = Role.Button, onClickLabel = "نوشتنِ مقدار و قیمت", onClick = onEdit) else Modifier)
        .padding(horizontal = 8.dp, vertical = 10.dp),
    )
    CartStepButton(Icons.Outlined.Add, "یکی بیشتر ${row.name}", v.onBlack, onPlus)
  }
}

@Composable
private fun CartStepButton(icon: ImageVector, label: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
  Box(
    Modifier
      .size(44.dp)
      .clip(CircleShape)
      .clickable(role = Role.Button, onClick = onClick)
      .semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
  }
}

/**
 *  ردیفِ «Delivery»ِ r2 برای «مشتری» و «تخفیف»: دایرهٔ تیرهٔ برآمده با آیکنِ خطیِ
 *  زرد، عنوانِ سفید، توضیحِ خاکستری و چیزی در انتها (مبلغ یا ×).
 */
@Composable
fun PhoneCartInfoRow(
  icon: ImageVector,
  title: String,
  hint: String?,
  index: Int,
  revealed: Boolean,
  onClick: (() -> Unit)?,
  onClickLabel: String? = null,
  trailing: @Composable () -> Unit = {},
) {
  val v = PhoneV3.colors
  Row(
    Modifier
      .fillMaxWidth()
      .reveal(index, revealed)
      .clip(RoundedCornerShape(18.dp))
      .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick) else Modifier)
      .padding(vertical = 17.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Box(
      Modifier.size(42.dp).clip(CircleShape).background(v.blackRaised),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = null, tint = v.cart, modifier = Modifier.size(21.dp))
    }
    Spacer(Modifier.width(22.dp))
    Column(Modifier.weight(1f).heightIn(min = 42.dp), verticalArrangement = Arrangement.Center) {
      Text(title, color = v.onBlack, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (hint != null) {
        Spacer(Modifier.height(6.dp))
        Text(hint, color = v.onBlackMuted, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2)
      }
    }
    Spacer(Modifier.width(12.dp))
    Box(Modifier.heightIn(min = 42.dp), contentAlignment = Alignment.Center) { trailing() }
  }
}

/** × کوچکِ خاکستری برای برداشتنِ مشتری */
@Composable
fun PhoneCartClear(label: String, onClick: () -> Unit) {
  CartStepButton(Icons.Outlined.Close, label, PhoneV3.colors.onBlackMuted, onClick)
}

/** مبلغِ خاکستریِ انتهای ردیف (r2: «$30.00») */
@Composable
fun PhoneCartAmount(text: String) {
  Text(text, color = PhoneV3.colors.onBlackMuted, fontSize = 14.sp, maxLines = 1)
}

/** «جمع:» سبک در آغاز و مبلغِ درشت در انتها (r2: «Total: $59.97») */
@Composable
fun PhoneCartTotal(total: Double) {
  val v = PhoneV3.colors
  val text = PhoneSaleText.amount(total)
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text("جمع:", color = v.onBlack, fontSize = 24.sp, fontWeight = FontWeight.Normal, maxLines = 1)
    Spacer(Modifier.width(16.dp))
    Spacer(Modifier.weight(1f))
    Text(
      text,
      color = v.onBlack,
      fontSize = PhoneSaleText.totalSp(text).sp,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      softWrap = false,
    )
  }
}

/** دکمهٔ زردِ قرصیِ تمام‌عرض (r2: «Next») — همان پنجرهٔ تسویهٔ پیشین را باز می‌کند */
@Composable
fun PhoneCartPay(label: String, onClick: () -> Unit, enabled: Boolean = true) {
  val v = PhoneV3.colors
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (pressed && Motion.enabled) 0.97f else 1f,
    animationSpec = if (Motion.enabled) spring(stiffness = Spring.StiffnessMedium) else snap(),
    label = "payPress",
  )
  Box(
    Modifier
      .fillMaxWidth()
      .height(56.dp)
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .clip(RoundedCornerShape(percent = 50))
      .background(if (enabled) v.cart else v.blackRaised)
      .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      label,
      style = MaterialTheme.typography.titleMedium.merge(TextStyle(fontSize = 16.sp)),
      color = if (enabled) v.onCart else v.onBlackMuted,
      fontWeight = FontWeight.Bold,
    )
  }
}
