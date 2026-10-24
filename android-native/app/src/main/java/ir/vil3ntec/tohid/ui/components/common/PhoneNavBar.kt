package ir.vil3ntec.tohid.ui.components.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Colors
import ir.vil3ntec.tohid.ui.theme.PhoneV3Dark

/** یک تبِ نوارِ پایینِ گوشی — فقط برای صدازننده‌های پیشین (`PhonePreview`) */
data class NavTab(val id: String, val label: String, val icon: ImageVector)

/**
 *  نوارِ پایینِ گوشی، phone v3 §F/§F2/§G — شکلِ r10.
 *
 *  سه تکهٔ سفیدِ شناور در یک ردیف: دایرهٔ شروع، قرصِ ۳ تا ۵ آیکنی، دایرهٔ پایان
 *  (راست‌به‌چپ: شروع = راست). آیکن‌ها خطی و تک‌رنگ، بی برچسب؛ موردِ فعال آیکنِ
 *  پُر روی دایرهٔ خاکستریِ روشن است و همان دایره با فنر میانِ موردها می‌لغزد.
 *  چه چیزی کجا بنشیند انتخابِ کاربر است (`NavLayouts`، ویرایشگر: `PhoneNav.EDITOR`)
 *  و نگه‌داشتنِ انگشت روی نوار همان ویرایشگر را باز می‌کند.
 *
 *  «حذفِ انیمیشن» (`Motion.enabled`) همه را بی‌حرکت می‌کند؛ هیچ انیمیشنی لمس را
 *  نگه نمی‌دارد.
 */
object PhoneNav {
  /** ویرایشگرِ نوار — از نگه‌داشتنِ نوار و از تنظیمات ← عمومی ← «نوارِ پایین» */
  const val EDITOR = "nav-editor"
  /** صفحهٔ جست‌وجوی کامل (کالا، مشتری، فاکتور، بخش) */
  const val SEARCH = "search"

  val CIRCLE: Dp = 56.dp
  /** جایی که نوار پایینِ صفحه می‌گیرد (بی نوارِ سیستم): ۵۶ + فاصله‌ها */
  val RESERVE: Dp = 80.dp
  /** و وقتی قرصِ «تمدیدِ اشتراک» هم بالایش است */
  val RENEW_RESERVE: Dp = 64.dp

  /** «اسکنِ بارکد»: فروش با دوربینِ روشن باز می‌شود (`SaleScreen` مصرفش می‌کند) */
  val scanRequest = mutableStateOf(false)

  /** چیدمانِ همین کاربر — نوار و ویرایشگر هر دو همین را می‌خوانند */
  val layout = mutableStateOf(NavLayouts.DEFAULT)

  /** ترجیحِ نمایشیِ محلی، کنارِ `view_mode` (همگام نمی‌شود) */
  fun load(context: android.content.Context, allowed: Set<String>): NavLayout {
    val store = ViewModePref.Store.of(context.getSharedPreferences(ViewModePref.PREFS, android.content.Context.MODE_PRIVATE))
    val user = runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountId }.getOrDefault("")
    return NavLayouts.load(runCatching { store.get(NavLayouts.key(user)) }.getOrNull(), allowed)
  }

  fun save(context: android.content.Context, l: NavLayout) {
    layout.value = l
    val store = ViewModePref.Store.of(context.getSharedPreferences(ViewModePref.PREFS, android.content.Context.MODE_PRIVATE))
    val user = runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountId }.getOrDefault("")
    runCatching { store.put(NavLayouts.key(user), NavLayouts.encode(l)) }
  }

  /** آیکنِ هر مورد: خطی، و پُر برای حالتِ فعال */
  fun icon(id: String, filled: Boolean): ImageVector = when (id) {
    NavCatalog.SEARCH -> if (filled) Icons.Filled.Search else Icons.Outlined.Search
    NavCatalog.HOME -> if (filled) Icons.Filled.Home else Icons.Outlined.Home
    NavCatalog.SALE -> if (filled) Icons.Filled.Inbox else Icons.Outlined.Inbox
    NavCatalog.PRODUCTS -> if (filled) Icons.Filled.LocalOffer else Icons.Outlined.LocalOffer
    NavCatalog.WAREHOUSE -> if (filled) Icons.Filled.Warehouse else Icons.Outlined.Warehouse
    NavCatalog.DEBTORS -> if (filled) Icons.Filled.Group else Icons.Outlined.Group
    NavCatalog.ORDERS -> if (filled) Icons.Filled.ShoppingBag else Icons.Outlined.ShoppingBag
    NavCatalog.REPORTS -> if (filled) Icons.Filled.Assessment else Icons.Outlined.Assessment
    NavCatalog.EXPENSES -> if (filled) Icons.Filled.Payments else Icons.Outlined.Payments
    NavCatalog.PURCHASING -> if (filled) Icons.Filled.LocalShipping else Icons.Outlined.LocalShipping
    NavCatalog.HISTORY -> if (filled) Icons.Filled.History else Icons.Outlined.History
    NavCatalog.TEAM -> if (filled) Icons.Filled.Badge else Icons.Outlined.Badge
    NavCatalog.ACCOUNT -> if (filled) Icons.Filled.Face else Icons.Outlined.Face
    NavCatalog.SCAN -> if (filled) Icons.Filled.QrCodeScanner else Icons.Outlined.QrCodeScanner
    NavCatalog.SETTINGS -> if (filled) Icons.Filled.Settings else Icons.Outlined.Settings
    else -> ir.vil3ntec.tohid.ui.v3.nav.V3Modules.navIcon(id) ?: Icons.Filled.Menu
  }

  /** دایرهٔ موردِ فعال — surfaceSelectedِ VILL3N (شکلِ r10: دایرهٔ روشن زیرِ آیکنِ پُر) */
  fun highlight(p: PhoneV3Colors) = ir.vil3ntec.tohid.ui.theme.PhoneV3Palette.selected(p)

  /** لبهٔ هر تکه = borderCard؛ بی آن، سفید روی صفحهٔ سفید گم می‌شد */
  fun edge(p: PhoneV3Colors) = p.hairline
}

/**
 *  @param current مسیرِ صفحهٔ باز (`dashboard`، `profile`، …) — موردِ فعال از روی آن
 *  @param onEdit نگه‌داشتنِ انگشت روی نوار؛ `null` = بی ویرایشگر (پیش‌نمایش)
 *  @param renewDays روزهای ماندهٔ اشتراک وقتی ۳ یا کمتر است ⇒ قرصِ سیاهِ «تمدیدِ اشتراک» بالای نوار
 */
@Composable
fun PhoneNavBar(
  layout: NavLayout,
  current: String?,
  onPick: (NavItemDef) -> Unit,
  onEdit: (() -> Unit)?,
  modifier: Modifier = Modifier,
  renewDays: Int? = null,
  onRenew: () -> Unit = {},
  systemInsets: Boolean = true,
  /** محوِ ملایمِ زمینه زیرِ نوار تا متنِ صفحه از لای تکه‌ها پیدا نباشد; پیش‌نمایش‌ها خاموش */
  scrim: Boolean = true,
) {
  val p = PhoneV3.colors
  val active = NavCatalog.activeFor(layout, current)
  Column(
    modifier
      .fillMaxWidth()
      .then(
        if (scrim) Modifier.background(
          Brush.verticalGradient(0f to p.card.copy(alpha = 0f), 0.32f to p.card.copy(alpha = 0.92f), 1f to p.card),
        ) else Modifier,
      )
      .then(if (systemInsets) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier)
      .padding(top = 8.dp, bottom = 12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    AnimatedVisibility(
      visible = renewDays != null,
      enter = if (Motion.enabled) fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 } else EnterTransition.None,
      exit = if (Motion.enabled) fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 } else ExitTransition.None,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RenewPill(renewDays ?: 0, onRenew)
        Spacer(Modifier.height(12.dp))
      }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
      val gap = 10.dp
      val sides = listOfNotNull(layout.start, layout.end).size
      val room = maxWidth - PhoneNav.CIRCLE * sides - gap * sides - 8.dp
      val itemW = (room / layout.pill.size.coerceAtLeast(1)).coerceIn(44.dp, 56.dp)
      Row(
        Modifier.selectableGroup().animateContentSize(if (Motion.enabled) spring(stiffness = Spring.StiffnessMediumLow) else snap()),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        layout.start?.let { id -> SideCircle(id, id == active, p, onPick, onEdit) }
        NavPill(layout.pill, active, itemW, p, onPick, onEdit)
        layout.end?.let { id -> SideCircle(id, id == active, p, onPick, onEdit) }
      }
    }
  }
}

/** همان امضای پیشین — پیش‌نمایش‌ها چیدمانِ پیش‌فرض را می‌کشند */
@Composable
fun PhoneNavBar(tabs: List<NavTab>, current: String?, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
  val route = tabs.firstOrNull { it.id == current }?.id ?: current
  PhoneNavBar(NavLayouts.DEFAULT, route, onPick = { onPick(it.route) }, onEdit = null, modifier = modifier)
}

private val floatShadow = 16.dp
private const val SHADOW_ALPHA = 0.16f

@Composable
private fun NavPill(
  items: List<String>,
  active: String?,
  itemW: Dp,
  p: PhoneV3Colors,
  onPick: (NavItemDef) -> Unit,
  onEdit: (() -> Unit)?,
) {
  val shape = RoundedCornerShape(percent = 50)
  val index = items.indexOf(active)
  val dot = 48.dp.coerceAtMost(itemW - 2.dp)
  //  دایرهٔ خاکستری با فنر از یک مورد به موردِ دیگر می‌لغزد (≈۲۵۰ms)
  val x by animateDpAsState(
    targetValue = itemW * index.coerceAtLeast(0) + (itemW - dot) / 2,
    animationSpec = if (Motion.enabled) spring(dampingRatio = 0.78f, stiffness = 520f) else snap(),
    label = "navHighlightX",
  )
  val shown by animateFloatAsState(if (index >= 0) 1f else 0f, if (Motion.enabled) tween(180) else snap(), label = "navHighlight")
  Box(
    Modifier
      .height(PhoneNav.CIRCLE)
      .shadow(floatShadow, shape, clip = false, ambientColor = p.black.copy(alpha = SHADOW_ALPHA), spotColor = p.black.copy(alpha = SHADOW_ALPHA))
      .clip(shape)
      .background(p.card)
      .border(1.dp, PhoneNav.edge(p), shape)
      .padding(horizontal = 4.dp),
    contentAlignment = Alignment.CenterStart,
  ) {
    Box(
      Modifier
        .offset { androidx.compose.ui.unit.IntOffset(x.roundToPx(), 0) }
        .size(dot)
        .graphicsLayer { alpha = shown; scaleX = 0.8f + 0.2f * shown; scaleY = 0.8f + 0.2f * shown }
        .clip(CircleShape)
        .background(PhoneNav.highlight(p)),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      items.forEach { id ->
        NavIcon(id, id == active, Modifier.width(itemW).height(PhoneNav.CIRCLE), p, onPick, onEdit)
      }
    }
  }
}

@Composable
private fun SideCircle(id: String, active: Boolean, p: PhoneV3Colors, onPick: (NavItemDef) -> Unit, onEdit: (() -> Unit)?) {
  Box(
    Modifier
      .size(PhoneNav.CIRCLE)
      .shadow(floatShadow, CircleShape, clip = false, ambientColor = p.black.copy(alpha = SHADOW_ALPHA), spotColor = p.black.copy(alpha = SHADOW_ALPHA))
      .clip(CircleShape)
      .background(p.card)
      .border(1.dp, PhoneNav.edge(p), CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    val shown by animateFloatAsState(if (active) 1f else 0f, if (Motion.enabled) tween(200) else snap(), label = "sideHi")
    Box(
      Modifier.size(46.dp).graphicsLayer { alpha = shown; scaleX = 0.8f + 0.2f * shown; scaleY = 0.8f + 0.2f * shown }
        .clip(CircleShape).background(PhoneNav.highlight(p)),
    )
    NavIcon(id, active, Modifier.size(PhoneNav.CIRCLE), p, onPick, onEdit)
  }
}

/** آیکنِ یک مورد: خطی ⇄ پُر با محوشدن و جهشِ کوچک؛ فشار = ۰٫۹۲ */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NavIcon(
  id: String,
  active: Boolean,
  modifier: Modifier,
  p: PhoneV3Colors,
  onPick: (NavItemDef) -> Unit,
  onEdit: (() -> Unit)?,
) {
  val def = NavCatalog.of(id) ?: return
  val source = remember { MutableInteractionSource() }
  val pressed by source.collectIsPressedAsState()
  val press by animateFloatAsState(if (pressed) 0.92f else 1f, if (Motion.enabled) spring(stiffness = 900f) else snap(), label = "navPress")
  val filled by animateFloatAsState(if (active) 1f else 0f, if (Motion.enabled) tween(180) else snap(), label = "navFill")
  val bounce = remember { Animatable(1f) }
  LaunchedEffect(active) {
    if (active && Motion.enabled) {
      bounce.snapTo(0.82f)
      bounce.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 520f))
    } else bounce.snapTo(1f)
  }
  Box(
    modifier
      .semantics {
        contentDescription = def.label
        role = Role.Tab
        selected = active
      }
      .combinedClickable(
        interactionSource = source,
        indication = null,
        onClick = { onPick(def) },
        onLongClick = onEdit,
      )
      .graphicsLayer { scaleX = press * bounce.value; scaleY = press * bounce.value },
    contentAlignment = Alignment.Center,
  ) {
    val tint = if (active) ir.vil3ntec.tohid.ui.theme.PhoneV3Palette.iconActive(p) else p.icon
    Icon(PhoneNav.icon(id, false), null, tint = tint, modifier = Modifier.size(24.dp).graphicsLayer { alpha = 1f - filled })
    Icon(PhoneNav.icon(id, true), null, tint = tint, modifier = Modifier.size(24.dp).graphicsLayer { alpha = filled })
  }
}

/** r10 «Subscribe for $1 · ● 1 day left» — فقط وقتی اشتراک ≤ ۳ روزِ دیگر تمام می‌شود */
@Composable
private fun RenewPill(days: Int, onRenew: () -> Unit) {
  val p = PhoneV3.colors
  val source = remember { MutableInteractionSource() }
  val pressed by source.collectIsPressedAsState()
  val press by animateFloatAsState(if (pressed) 0.96f else 1f, if (Motion.enabled) spring(stiffness = 900f) else snap(), label = "renewPress")
  Row(
    Modifier
      .widthIn(max = 344.dp)
      .fillMaxWidth(0.9f)
      .height(52.dp)
      .graphicsLayer { scaleX = press; scaleY = press }
      .shadow(10.dp, RoundedCornerShape(percent = 50), clip = false, ambientColor = p.black.copy(alpha = 0.12f), spotColor = p.black.copy(alpha = 0.12f))
      .clip(RoundedCornerShape(percent = 50))
      .background(p.black)
      .combinedClickable(interactionSource = source, indication = null, role = Role.Button, onClick = onRenew)
      .padding(start = 20.dp, end = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      "تمدیدِ اشتراک",
      color = p.onBlack,
      style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
      modifier = Modifier.weight(1f),
      maxLines = 1,
    )
    Row(
      Modifier.height(36.dp).clip(RoundedCornerShape(percent = 50)).background(p.blackRaised).padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(Modifier.size(8.dp).clip(CircleShape).background(p.negative))
      Spacer(Modifier.width(8.dp))
      Text(
        if (days <= 0) "امروز تمام می‌شود" else "${days} روز مانده",
        color = p.onBlack,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
        maxLines = 1,
      )
    }
  }
}
