package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.components.pos.CartSummary
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartUi
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import kotlinx.coroutines.launch

/*
 *  صندوقِ گوشی — **حالتِ تمرکز** (صاحب‌کار ۱۴۰۵/۰۸/۲۴: «سبد سیاه و تیره است اما … دکمه‌های شناورِ پایین سفید — خیلی مسخره شده»).
 *
 *    ┌ سربرگ: برگشت · «فروش» · جستجو · کاشی/فهرست · «…» ┐
 *    │  دسته‌ها، پرفروش‌ها، کالاها                          │
 *    │                                                      │
 *    ├╭────────────────────────────────────────────────────╮┤
 *    ││ (اسکن)   ۳ قلم / ۵۷۰ ؋   ˄            [ پرداخت ]   ││  ← تنها چیزِ پایین، چسبیده به لبهٔ پایین («سبد باید زیرِ صفحه باشه»):
 *    └┴────────────────────────────────────────────────────┴┘     کارتِ روشن، گوشه‌های بالا گرد، مرزِ مویی، سایهٔ نرم
 *
 *  نوارِ شناورِ برنامه این‌جا نیست (`ShellFrame.FOCUS`). سبدِ باز برگهٔ همان رنگِ کارت روی پرده است — در روز روشن، در شب سطحِ
 *  برآمدهٔ تیره — نه تختهٔ سیاه؛ رنگِ برند فقط روی «پرداخت». برگه درونِ همین صفحه است (نه پنجرهٔ جدا) تا بارکدخوانِ سخت‌افزاری
 *  (`hidScanner`) با سبدِ باز هم بخواند. کشیدنِ دستگیره به پایین، زدنِ پرده یا «برگشت» می‌بندد؛ «کاهشِ حرکت» ⇒ بی انیمیشن.
 */

/** اندازه‌ها و نشان‌های آزمونِ صندوقِ گوشی */
object TillSpec {
  val BAR_HEIGHT: Dp = 72.dp
  /** جای لازم زیرِ آخرین کالا تا پشتِ نوار نماند (نوار به لبهٔ پایین چسبیده؛ نوارِ سیستم جدا افزوده می‌شود) */
  val BAR_SPACE: Dp = BAR_HEIGHT + 8.dp
  /** بالای برگهٔ سبد همیشه کمی از کالاها دیده بماند */
  val SHEET_TOP_GAP: Dp = 56.dp
  const val BAR_TAG = "till-bar"
  const val SHEET_TAG = "till-sheet"
  const val MORE_TAG = "till-more"
  const val ROW_TAG = "till-cart-row"
}

private val SHEET_SPRING = spring<Float>(dampingRatio = 0.9f, stiffness = 520f)

/**
 *  @param catalog کالاها؛ `bottomGap` جای نوار زیرِ آخرین کالا
 *  @param sheet سبدِ باز؛ `dragHandle` برای سرِ سبد (کشیدن به پایین ⇐ بستن)
 */
@Composable
fun TillFrame(
  model: CartSummary.Model,
  open: Boolean,
  onOpen: () -> Unit,
  onClose: () -> Unit,
  pulse: Int,
  onPay: () -> Unit,
  onScan: () -> Unit,
  catalog: @Composable (bottomGap: Dp) -> Unit,
  sheet: @Composable ColumnScope.(dragHandle: Modifier) -> Unit,
) {
  val c = PhoneV3.colors
  val motion = Motion.enabled
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  //  پوسته‌ای که خودش نوارِ سیستم را گرفته `LocalV3SystemBottom = 0.dp` می‌دهد (بی دوباره‌شماری)
  val navBottom = LocalV3SystemBottom.current ?: WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val showing = open && model.visible

  DisposableEffect(Unit) {
    PhoneCartUi.saleActive.value = true
    onDispose {
      PhoneCartUi.saleActive.value = false
      PhoneCartUi.sheetOpen.value = false
    }
  }
  LaunchedEffect(showing) { PhoneCartUi.sheetOpen.value = showing }
  //  Ctrl+N (`AppNav.addAction`) همان «اسکن» — همان که دکمهٔ شناورِ پیشین می‌داد
  val scan by rememberUpdatedState(onScan)
  DisposableEffect(Unit) {
    val action = { scan() }
    AppNav.addAction.value = action
    onDispose { if (AppNav.addAction.value === action) AppNav.addAction.value = null }
  }
  BackHandler(enabled = showing, onBack = onClose)

  val progress = remember { Animatable(if (showing) 1f else 0f) }
  LaunchedEffect(showing, motion) {
    val target = if (showing) 1f else 0f
    if (motion) progress.animateTo(target, SHEET_SPRING) else progress.snapTo(target)
  }

  BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
    val fullPx = constraints.maxHeight.toFloat()
    catalog(TillSpec.BAR_SPACE + navBottom)
    TillBar(model, pulse, onOpen, onPay, onScan, navBottom, Modifier.align(Alignment.BottomCenter))

    val p = progress.value
    if (p > 0.001f) {
      var sheetH by remember { mutableIntStateOf(0) }
      val travel = (if (sheetH > 0) sheetH.toFloat() else fullPx).coerceAtLeast(1f)
      fun settle(velocity: Float) {
        val keep = when {
          velocity > 900f -> false
          velocity < -900f -> true
          else -> progress.value > 0.6f
        }
        scope.launch { if (motion) progress.animateTo(if (keep) 1f else 0f, SHEET_SPRING) else progress.snapTo(if (keep) 1f else 0f) }
        if (!keep) onClose()
      }
      val drag = rememberDraggableState { delta -> scope.launch { progress.snapTo((progress.value - delta / travel).coerceIn(0f, 1f)) } }
      val handle = Modifier.draggable(drag, Orientation.Vertical, enabled = showing, onDragStopped = { settle(it) })

      //  پرده: همان پردهٔ برگه‌های نسخهٔ ۳ (`V3Sheet`)؛ زدن ⇐ بستن
      Box(
        Modifier
          .fillMaxSize()
          .graphicsLayer { alpha = p }
          .background(c.black.copy(alpha = if (c.source.isDark) 0.6f else 0.32f))
          .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = StrSale.closeCart, onClick = onClose)
          .semantics { contentDescription = StrSale.closeCart },
      )
      val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
      Column(
        Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .heightIn(max = maxHeight - TillSpec.SHEET_TOP_GAP)
          .onSizeChanged { sheetH = it.height }
          .graphicsLayer { translationY = (1f - p) * travel }
          .shadow(16.dp, shape, clip = false, ambientColor = c.source.shadow, spotColor = c.source.shadow)
          .clip(shape)
          .background(c.card)
          .border(1.dp, c.hairline, shape)
          .testTag(TillSpec.SHEET_TAG)
          .padding(start = 20.dp, end = 20.dp, bottom = 16.dp + navBottom),
      ) {
        //  دستگیره: کشیدنی؛ صفحه‌خوان آن را دکمهٔ «بستنِ سبد» می‌شنود
        Box(Modifier.fillMaxWidth().then(handle).padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
          Box(
            Modifier.size(width = 38.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(c.hairline)
              .semantics { role = Role.Button; contentDescription = StrSale.closeCart; onClick(StrSale.closeCart) { onClose(); true } },
          )
        }
        sheet(handle)
      }
    }
  }
}

/**
 *  نوارِ سبد — کارتِ روشنِ مرزدار (همان خانوادهٔ `QuickCartStrip`): اسکن در آغاز (کارِ اصلیِ صندوق، زیرِ شست)، «۳ قلم» و
 *  جمع (زدن ⇐ سبد باز می‌شود) و «پرداخت» با رنگِ برند. سبدِ خالی ⇒ فقط اسکن و «سبد خالی است». `pulse` ⇒ پرشِ کوچکِ جمع.
 */
@Composable
private fun TillBar(model: CartSummary.Model, pulse: Int, onOpen: () -> Unit, onPay: () -> Unit, onScan: () -> Unit, navBottom: Dp, modifier: Modifier) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(topStart = PhoneV3.groupRadius, topEnd = PhoneV3.groupRadius)
  val bump = remember { Animatable(1f) }
  val first = remember { pulse }
  LaunchedEffect(pulse) {
    if (pulse == first || !Motion.enabled) return@LaunchedEffect
    bump.snapTo(1.12f)
    bump.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 650f))
  }
  Row(
    modifier
      .fillMaxWidth()
      .shadow(12.dp, shape, clip = false, ambientColor = c.source.shadow, spotColor = c.source.shadow)
      .clip(shape)
      .background(c.card)
      .border(1.dp, c.hairline, shape)
      .testTag(TillSpec.BAR_TAG)
      .padding(start = 12.dp, end = 12.dp, bottom = navBottom)
      .height(TillSpec.BAR_HEIGHT),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TillScanButton(onScan)
    Spacer(Modifier.width(4.dp))
    if (!model.visible) {
      Text(StrSale.emptyCartStrip, style = V3Type.callout, color = c.inkMuted, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
    } else TillBarCart(model, bump.value, onOpen, onPay)
  }
}

/** «۳ قلم / جمع» (زدن ⇐ سبد) و «پرداخت» — فقط با سبدِ پُر */
@Composable
private fun androidx.compose.foundation.layout.RowScope.TillBarCart(model: CartSummary.Model, bump: Float, onOpen: () -> Unit, onPay: () -> Unit) {
  val c = PhoneV3.colors
  val total = rememberCountUp(model.total)
    Row(
      Modifier
        .weight(1f)
        .fillMaxHeight()
        .padding(vertical = 6.dp)
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClickLabel = StrSale.openCart, role = Role.Button, onClick = onOpen)
        .semantics(mergeDescendants = true) { contentDescription = StrSale.cartSpoken(plain(model.count), PhoneSaleText.amount(model.total)) }
        .padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text(StrSale.linesCount(plain(model.count)), style = V3Type.footnote, color = c.inkMuted, maxLines = 1)
        Text(
          PhoneSaleText.amount(total),
          style = V3Type.headline,
          color = c.ink,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.graphicsLayer { scaleX = bump; scaleY = bump },
        )
      }
      Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(22.dp))
    }
    Spacer(Modifier.width(6.dp))
    V3Button(StrSale.pay, onPay, height = 48.dp, haptic = true)
}

/** دکمهٔ گردِ اسکن — خنثی (رنگِ برند فقط مالِ «پرداخت» است) */
@Composable
private fun TillScanButton(onScan: () -> Unit) {
  val pair = PhoneV3Roles.button(PhoneV3.colors, V3ButtonKind.NEUTRAL)
  Box(
    Modifier
      .size(48.dp)
      .v3Pressable(label = StrKit.scanBarcode, scale = 0.92f, onClick = onScan)
      .clip(CircleShape)
      .background(pair.bg)
      .semantics { contentDescription = StrKit.scanBarcode },
    contentAlignment = Alignment.Center,
  ) {
    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, tint = pair.fg, modifier = Modifier.size(22.dp))
  }
}
