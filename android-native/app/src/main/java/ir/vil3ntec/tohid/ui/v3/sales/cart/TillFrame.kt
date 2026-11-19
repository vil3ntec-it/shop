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
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
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
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/*
 *  نوارِ سبد پایین صندوق می‌ماند. برگه در همان صفحه باز می‌شود تا بارکدخوان و میان‌برها همچنان کار کنند.
 *  کشیدنِ دستگیره به پایین، زدنِ پرده یا برگشت برگه را می‌بندد. کاهش حرکت، انیمیشن را کنار می‌گذارد.
 */

/** اندازه‌ها و نشان‌های آزمونِ صندوقِ گوشی */
object TillSpec {
  val BAR_HEIGHT: Dp = 72.dp
  /** جای لازم زیرِ آخرین کالا تا پشتِ نوار نماند (فاصلهٔ نوار سیستم جدا افزوده می‌شود) */
  val BAR_SPACE: Dp = BAR_HEIGHT + 16.dp
  /** بالای برگهٔ سبد همیشه کمی از کالاها دیده بماند */
  val SHEET_TOP_GAP: Dp = 64.dp
  const val CATALOG_TAG = "till-catalog-pane"
  const val SHEET_HANDLE_TAG = "till-sheet-handle"
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

  BoxWithConstraints(Modifier.fillMaxSize().background(c.card)) {
    val bottomGap = TillSpec.BAR_SPACE + navBottom
    val sheetHeight = (maxHeight - TillSpec.SHEET_TOP_GAP).coerceAtLeast(0.dp)
    val travel = with(density) { (sheetHeight - bottomGap).toPx() }.coerceAtLeast(1f)
    val catalogPeek = with(density) { 72.dp.toPx() }.coerceAtMost(travel * 0.22f)
    val drag = rememberDraggableState { delta ->
      scope.launch(start = CoroutineStart.UNDISPATCHED) {
        progress.snapTo((progress.value - delta / travel).coerceIn(0f, 1f))
      }
    }
    val grip = Modifier.draggable(
      drag, Orientation.Vertical,
      enabled = model.visible,
      onDragStarted = { progress.stop() },
      onDragStopped = { velocity ->
        val keep = model.visible && when {
          velocity < -900f -> true
          velocity > 900f -> false
          showing -> progress.value >= 0.62f
          else -> progress.value >= 0.28f
        }
        when {
          keep && !showing -> onOpen()
          !keep && showing -> onClose()
          else -> animate(keep)
        }
      },
    )

    val p = progress.value
    val paneShape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    Box(
      Modifier.fillMaxSize().padding(bottom = bottomGap)
        .graphicsLayer { translationY = -p * travel }
        .clip(paneShape).background(c.bg)
        .testTag(TillSpec.CATALOG_TAG),
    ) {
      //  چند ردیف پایینِ فهرست از پشتِ لبهٔ بالاییِ سبد پیدا می‌مانند.
      Box(Modifier.fillMaxSize().graphicsLayer { translationY = p * catalogPeek }) { catalog(8.dp) }
    }
    TillBar(
      model, pulse, onOpen, onPay, onScan, navBottom,
      Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = (1f - 4f * p).coerceIn(0f, 1f) }.then(grip),
    )

    if (p > 0.001f && sheetHeight > 0.dp) {
      //  با ضربه روی بخش باقی‌ماندهٔ صفحه، سبد بسته می‌شود.
      Box(
        Modifier.fillMaxSize()
          .background(c.black.copy(alpha = 0.1f * p))
          .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = StrSale.closeCart, onClick = onClose)
          .semantics { contentDescription = StrSale.closeCart },
      )
      val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
      Column(
        Modifier
          .align(Alignment.BottomCenter)
          .widthIn(max = 640.dp)
          .fillMaxWidth()
          .height(sheetHeight)
          .graphicsLayer { translationY = (1f - p) * travel }
          .shadow(16.dp, shape, clip = false, ambientColor = c.source.shadow, spotColor = c.source.shadow)
          .clip(shape)
          .background(c.card)
          .border(1.dp, c.hairline, shape)
          .testTag(TillSpec.SHEET_TAG)
          .padding(start = 20.dp, end = 20.dp, bottom = 16.dp + navBottom),
      ) {
        //  دستگیره: کشیدنی؛ صفحه‌خوان آن را دکمهٔ «بستنِ سبد» می‌شنود
        Box(Modifier.fillMaxWidth().then(grip).testTag(TillSpec.SHEET_HANDLE_TAG).padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
          Box(
            Modifier.size(width = 38.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(c.hairline)
              .semantics { role = Role.Button; contentDescription = StrSale.closeCart; onClick(StrSale.closeCart) { onClose(); true } },
          )
        }
        sheet(grip)
      }
    }
  }
}

/**
 *  نوارِ سبد: اسکن، خلاصهٔ سبد و پرداخت. در سبد خالی، اسکن و پیام سبد خالی دیده می‌شوند.
 */
@Composable
private fun TillBar(model: CartSummary.Model, pulse: Int, onOpen: () -> Unit, onPay: () -> Unit, onScan: () -> Unit, navBottom: Dp, modifier: Modifier) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(28.dp)
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
      .shadow(12.dp, shape, clip = false, ambientColor = c.source.shadow, spotColor = c.source.shadow)
      .clip(shape)
      .background(c.card)
      .border(1.dp, c.hairline, shape)
      .testTag(TillSpec.BAR_TAG)
      .padding(horizontal = 8.dp)
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
