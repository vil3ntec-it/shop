package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.runtime.CompositionLocalProvider
import ir.vil3ntec.tohid.ui.theme.LocalShopColors
import ir.vil3ntec.tohid.ui.theme.ShopTheme
import ir.vil3ntec.tohid.ui.theme.shopColors
import kotlin.math.abs
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/*
 *  نوارِ سبد پایین صندوق می‌ماند. برگه در همان صفحه باز می‌شود تا بارکدخوان و میان‌برها همچنان کار کنند.
 *  کشیدنِ صفحه به پایین، زدنِ پرده یا برگشت برگه را می‌بندد. کاهش حرکت، انیمیشن را کنار می‌گذارد.
 */

/** اندازه‌ها و نشان‌های آزمونِ صندوقِ گوشی */
object TillSpec {
  val BAR_HEIGHT: Dp = 72.dp
  /** جای لازم زیرِ آخرین کالا تا پشتِ نوار نماند (فاصلهٔ نوار سیستم جدا افزوده می‌شود) */
  val BAR_SPACE: Dp = BAR_HEIGHT + 16.dp
  /** بالای برگهٔ سبد همیشه کمی از کالاها دیده بماند */
  val SHEET_TOP_GAP: Dp = 64.dp
  const val CATALOG_TAG = "till-catalog-pane"
  const val BAR_TAG = "till-bar"
  const val SHEET_TAG = "till-sheet"
  const val MORE_TAG = "till-more"
  const val ROW_TAG = "till-cart-row"
}

private val SHEET_SPRING = spring<Float>(dampingRatio = 0.9f, stiffness = 520f)

/**
 *  @param catalog کالاها؛ `bottomGap` جای نوار زیرِ آخرین کالا
 *  @param sheet سبدِ باز
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
  sheet: @Composable ColumnScope.() -> Unit,
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

  //  Ctrl+N (`AppNav.addAction`) همان «اسکن» — همان که دکمهٔ شناورِ پیشین می‌داد
  val scan by rememberUpdatedState(onScan)
  DisposableEffect(Unit) {
    val action = { scan() }
    AppNav.addAction.value = action
    onDispose { if (AppNav.addAction.value === action) AppNav.addAction.value = null }
  }
  val progress = remember { Animatable(if (showing) 1f else 0f) }
  suspend fun animate(open: Boolean) {
    val target = if (open) 1f else 0f
    if (motion) progress.animateTo(target, SHEET_SPRING) else progress.snapTo(target)
  }
  LaunchedEffect(showing, motion) { animate(showing) }
  LaunchedEffect(showing) { PhoneCartUi.sheetOpen.value = showing }
  BackHandler(enabled = showing, onBack = onClose)

  val cartColors = remember {
    val ivory = shopColors(ShopTheme.IVORY, dark = true)
    val pay = ivory.button.primary.copy(bg = ivory.brandText, border = ivory.brandText, text = ivory.background)
    ivory.copy(button = ivory.button.copy(primary = pay))
  }
  BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
    val bottomGap = TillSpec.BAR_SPACE + navBottom
    val reserved = if (model.visible) bottomGap else navBottom
    val sheetHeight = (maxHeight - TillSpec.SHEET_TOP_GAP).coerceAtLeast(0.dp)
    val travel = with(density) { (sheetHeight - bottomGap).toPx() }.coerceAtLeast(1f)
    val catalogPeek = with(density) { 72.dp.toPx() }.coerceAtMost(travel * 0.22f)
    val openCart by rememberUpdatedState(onOpen)
    val closeCart by rememberUpdatedState(onClose)
    val swipe = Modifier.pointerInput(model.visible, showing, travel, motion) {
      if (!model.visible) return@pointerInput
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        val initial = progress.value
        var claimed = false
        try {
          while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.singleOrNull() ?: break
            if (change.id != down.id || change.isConsumed) break
            val distance = change.position - down.position
            tracker.addPosition(change.uptimeMillis, change.position)
            if (!claimed) {
              if (!change.pressed) break
              if (distance.getDistance() <= maxOf(viewConfiguration.touchSlop, 24.dp.toPx())) continue
              if (abs(distance.x) >= abs(distance.y) || (distance.y > 0) != showing) break
              claimed = true
            }
            change.consume()
            val fraction = (initial - distance.y / travel).coerceIn(0f, 1f)
            scope.launch(start = CoroutineStart.UNDISPATCHED) { progress.snapTo(fraction) }
            if (!change.pressed) {
              val velocity = tracker.calculateVelocity().y / density.density
              val expand = when {
                abs(velocity) >= 550f -> velocity < 0f
                showing -> fraction >= 0.7f
                else -> fraction >= 0.26f
              }
              claimed = false
              if (expand == showing) scope.launch { animate(showing) }
              else if (expand) openCart() else closeCart()
              break
            }
          }
        } finally {
          if (claimed) scope.launch { animate(showing) }
        }
      }
    }

    Box(Modifier.fillMaxSize().then(swipe)) {
      val p = if (model.visible) progress.value else 0f
      val paneShape = RoundedCornerShape(bottomStart = PhoneV3.sheetRadius, bottomEnd = PhoneV3.sheetRadius)
      Box(
        Modifier.fillMaxSize().padding(bottom = reserved)
          .graphicsLayer { translationY = -p * travel }
          .then(if (model.visible) Modifier.clip(paneShape) else Modifier).background(c.bg)
          .testTag(TillSpec.CATALOG_TAG),
      ) {
        //  چند ردیف پایینِ فهرست از پشتِ لبهٔ بالاییِ سبد پیدا می‌مانند.
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = p * catalogPeek }) { catalog(8.dp) }
      }
      CompositionLocalProvider(LocalShopColors provides cartColors) {
        AnimatedVisibility(
          visible = model.visible,
          modifier = Modifier.align(Alignment.BottomCenter)
            .graphicsLayer { alpha = (1f - 4f * p).coerceIn(0f, 1f) },
          enter = if (motion) slideInVertically { it } + fadeIn() else EnterTransition.None,
          exit = ExitTransition.None,
        ) {
          Box(Modifier.fillMaxWidth().background(cartColors.backgroundSubtle), contentAlignment = Alignment.Center) {
            TillBar(model, pulse, onOpen, onPay, onScan, navBottom, Modifier)
          }
        }

        if (p > 0.001f && sheetHeight > 0.dp) {
          //  با ضربه روی بخش باقی‌ماندهٔ صفحه، سبد بسته می‌شود.
          Box(
            Modifier.fillMaxSize()
              .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = StrSale.closeCart, onClick = onClose)
              .semantics { contentDescription = StrSale.closeCart },
          )
          Column(
            Modifier
              .align(Alignment.BottomCenter)
              .widthIn(max = 640.dp)
              .fillMaxWidth()
              .height(sheetHeight)
              .graphicsLayer { translationY = (1f - p) * travel }
              .background(cartColors.backgroundSubtle)
              .testTag(TillSpec.SHEET_TAG)
              .padding(start = 20.dp, end = 20.dp, bottom = 16.dp + navBottom),
          ) {
            Spacer(Modifier.height(22.dp))
            sheet()
          }
        }
      }
    }
  }
}

/**
 *  نوارِ سبدِ پُر: اسکن، خلاصهٔ سبد و پرداخت.
 */
@Composable
private fun TillBar(model: CartSummary.Model, pulse: Int, onOpen: () -> Unit, onPay: () -> Unit, onScan: () -> Unit, navBottom: Dp, modifier: Modifier) {
  val bump = remember { Animatable(1f) }
  val first = remember { pulse }
  LaunchedEffect(pulse) {
    if (pulse == first || !Motion.enabled) return@LaunchedEffect
    bump.snapTo(1.12f)
    bump.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 650f))
  }
  Row(
    modifier
      .widthIn(max = 640.dp)
      .fillMaxWidth()
      .padding(start = 12.dp, end = 12.dp, bottom = navBottom + 8.dp)
      .testTag(TillSpec.BAR_TAG)
      .padding(horizontal = 8.dp)
      .height(TillSpec.BAR_HEIGHT),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TillScanButton(onScan)
    Spacer(Modifier.width(4.dp))
    TillBarCart(model, bump.value, onOpen, onPay)
  }
}

/** خلاصهٔ سبد و پرداخت، فقط وقتی سبد پر است. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.TillBarCart(model: CartSummary.Model, bump: Float, onOpen: () -> Unit, onPay: () -> Unit) {
  CartDockPreview(
    model,
    modifier = Modifier.weight(1f)
      .fillMaxHeight()
      .padding(vertical = 6.dp)
      .clip(RoundedCornerShape(18.dp))
      .clickable(onClickLabel = StrSale.openCart, role = Role.Button, onClick = onOpen)
      .semantics(mergeDescendants = true) { contentDescription = StrSale.cartSpoken(plain(model.count), PhoneSaleText.amount(model.total)) }
      .padding(horizontal = 6.dp),
    bump = bump,
  )
  Spacer(Modifier.width(6.dp))
  V3Button(StrSale.pay, onPay, height = 48.dp, pill = true, haptic = true)
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
