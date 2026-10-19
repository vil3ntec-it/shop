package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.PhoneV3

/*
 *  سطحِ کالاهای صندوقِ گوشی (نسخهٔ ۳، تصویرِ مرجعِ r3) — آینه‌شده برای راست‌به‌چپ.
 *
 *  سربرگِ ثابت (برگشت، عنوانِ وسط، اسکن) روی همان رنگِ سطح، با محوشدنی که کارت‌ها زیرش
 *  می‌روند؛ زیرش ردیفِ چیپ‌های دسته (و ذره‌بینِ جستجو)، بعد شبکهٔ دوستونهٔ پلکانی: ستونِ
 *  دوم کمی پایین‌تر شروع می‌شود (r3). کارت: عکس یا حرفِ اولِ درشت، قیمتِ درشت، نام (دو خط)
 *  و موجودی به خاکستری. زدنِ کارت ⇐ یک پله به سبد؛ −/+ فقط در سبد است.
 */

/** یک کارتِ کالا — فقط داده‌ای که کارت نشان می‌دهد */
data class PhoneCatalogItem(
  val id: String,
  val name: String,
  val price: Double,
  val unit: String,
  val stock: Double,
  val hasPhoto: Boolean,
  /** مقدارِ همین کالا در سبد (۰ ⇐ بی‌نشان) */
  val inCart: Double,
)

/** بلندیِ سربرگ زیرِ نوارِ وضعیت */
private val HEADER = 64.dp

/** پایین‌تر شروع شدنِ ستونِ دوم (r3 ≈ ۵۱dp = این + فاصلهٔ ردیف‌ها) */
private val STAGGER = 36.dp

@Composable
fun PhoneCatalog(
  title: String,
  items: List<PhoneCatalogItem>,
  categories: List<String>,
  category: String?,
  onCategory: (String?) -> Unit,
  onCategoryHold: (String) -> Unit,
  onAddCategory: () -> Unit,
  search: String,
  onSearch: (String) -> Unit,
  onBack: () -> Unit,
  onScan: () -> Unit,
  scanning: Boolean,
  scanPanel: @Composable () -> Unit,
  emptyText: String,
  bottomGap: Dp,
  onAdd: (PhoneCatalogItem) -> Unit,
) {
  val v = PhoneV3.colors
  //  زیرِ نوارِ وضعیت؛ کمینهٔ ۲۴ (بلندیِ نوارِ وضعیتِ گوشی) تا در پنجرهٔ بی‌نوار هم سربرگ جای r3 بنشیند
  val top = maxOf(WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), 24.dp)
  val grid = rememberLazyStaggeredGridState()
  LaunchedEffect(scanning) { if (scanning) grid.animateScrollToItem(0) }

  Box(Modifier.fillMaxSize()) {
    LazyVerticalStaggeredGrid(
      columns = StaggeredGridCells.Fixed(2),
      state = grid,
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = top + HEADER, bottom = bottomGap),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalItemSpacing = 15.dp,
    ) {
      item(key = "chips", span = StaggeredGridItemSpan.FullLine) {
        CategoryChips(categories, category, onCategory, onCategoryHold, onAddCategory, search, onSearch)
      }
      if (scanning) {
        item(key = "scan", span = StaggeredGridItemSpan.FullLine) { scanPanel() }
      }
      if (items.isEmpty()) {
        item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
          Text(
            emptyText,
            color = v.inkMuted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 24.dp),
          )
        }
      } else {
        val first = items.first()
        item(key = first.id) { PhoneProductCard(first) { onAdd(first) } }
        //  ستونِ دوم از این‌جا شروع می‌شود ⇐ همان پلکانِ r3
        item(key = "stagger") { Spacer(Modifier.fillMaxWidth().height(STAGGER)) }
        items(items.drop(1), key = { it.id }) { p -> PhoneProductCard(p) { onAdd(p) } }
      }
    }

    /* ---------------- سربرگِ ثابت (r3) ---------------- */
    Column(Modifier.fillMaxWidth()) {
      Box(
        Modifier
          .fillMaxWidth()
          .background(v.catalogPage)
          .padding(top = top + 16.dp, start = 16.dp, end = 16.dp)
          .height(48.dp),
      ) {
        HeaderButton("برگشت", onBack, Modifier.align(Alignment.CenterStart)) { BackChevron(v.icon) }
        Text(
          title,
          color = v.ink,
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
          modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
        )
        HeaderButton(if (scanning) "بستنِ اسکن" else "اسکنِ بارکد", onScan, Modifier.align(Alignment.CenterEnd)) {
          Icon(
            if (scanning) Icons.Outlined.Close else Icons.Outlined.QrCodeScanner,
            contentDescription = null,
            tint = v.icon,
            modifier = Modifier.size(24.dp),
          )
        }
      }
      //  کارت‌ها زیرِ سربرگ محو می‌شوند (r3: «Les Framboise»)
      Box(
        Modifier
          .fillMaxWidth()
          .height(18.dp)
          .background(Brush.verticalGradient(listOf(v.catalogPage, v.catalogPage.copy(alpha = 0f)))),
      )
    }
  }
}

@Composable
private fun HeaderButton(label: String, onClick: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
  Box(
    modifier
      .size(48.dp)
      .clip(CircleShape)
      .clickable(role = Role.Button, onClick = onClick)
      .semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
  ) { content() }
}

/** پیکانِ نازکِ برگشت (r3 «‹») — در راست‌به‌چپ رو به راست */
@Composable
private fun BackChevron(color: Color) {
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Canvas(Modifier.size(22.dp)) {
    val w = size.width
    val h = size.height
    fun x(f: Float) = if (rtl) w - w * f else w * f
    val path = Path().apply {
      moveTo(x(0.64f), h * 0.16f)
      lineTo(x(0.31f), h * 0.5f)
      lineTo(x(0.64f), h * 0.84f)
    }
    drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
  }
}

/**
 *  ردیفِ چیپ‌های دسته (کوچک و روشن): ذره‌بین، «همه»، دسته‌ها و «+» برای دستهٔ تازه.
 *  نگه داشتنِ یک دسته ⇐ حذفش (با تأیید، کارِ صدازننده). زدنِ ذره‌بین ⇐ همین ردیف
 *  کادرِ جستجو می‌شود (نام یا بارکد)؛ × پاکش می‌کند و چیپ‌ها برمی‌گردند.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryChips(
  categories: List<String>,
  category: String?,
  onCategory: (String?) -> Unit,
  onHold: (String) -> Unit,
  onAdd: () -> Unit,
  search: String,
  onSearch: (String) -> Unit,
) {
  val v = PhoneV3.colors
  var searching by rememberSaveable { mutableStateOf(search.isNotEmpty()) }
  if (searching || search.isNotEmpty()) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
      Modifier
        .fillMaxWidth()
        .padding(vertical = 2.dp)
        .height(44.dp)
        .clip(RoundedCornerShape(percent = 50))
        .background(v.catalogCard)
        .padding(start = 14.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Outlined.Search, contentDescription = null, tint = v.inkMuted, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(10.dp))
      Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
        if (search.isEmpty()) Text("جستجوی نام کالا یا بارکد", color = v.inkMuted, fontSize = 14.sp, maxLines = 1)
        BasicTextField(
          value = search,
          onValueChange = onSearch,
          singleLine = true,
          textStyle = TextStyle(color = v.ink, fontSize = 14.sp),
          cursorBrush = SolidColor(v.ink),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
      }
      HeaderButton("بستنِ جستجو", { onSearch(""); searching = false }, Modifier) {
        Icon(Icons.Outlined.Close, contentDescription = null, tint = v.icon, modifier = Modifier.size(20.dp))
      }
    }
    return
  }
  Row(
    Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RoundChip("جستجو", Icons.Outlined.Search) { searching = true }
    Chip("همه", selected = category == null, onClick = { onCategory(null) }, onHold = null)
    categories.forEach { c ->
      Chip(c, selected = category == c, onClick = { onCategory(if (category == c) null else c) }, onHold = { onHold(c) })
    }
    RoundChip("دستهٔ تازه", Icons.Outlined.Add, onAdd)
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit, onHold: (() -> Unit)?) {
  val v = PhoneV3.colors
  Box(
    Modifier
      .height(36.dp)
      .clip(RoundedCornerShape(percent = 50))
      .background(if (selected) v.ink else v.catalogCard)
      .combinedClickable(
        role = Role.Tab,
        onLongClickLabel = if (onHold != null) "حذفِ این دسته" else null,
        onLongClick = onHold,
        onClick = onClick,
      )
      .padding(horizontal = 16.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text,
      color = if (selected) v.catalogCard else v.ink,
      fontSize = 13.sp,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
      maxLines = 1,
    )
  }
}

@Composable
private fun RoundChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
  val v = PhoneV3.colors
  Box(
    Modifier
      .size(36.dp)
      .clip(CircleShape)
      .background(v.catalogCard)
      .clickable(role = Role.Button, onClick = onClick)
      .semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = null, tint = v.icon, modifier = Modifier.size(18.dp))
  }
}

/**
 *  کارتِ کالا (r3): سفید، گوشهٔ ۲۰، بی خط؛ عکس وسط (یا کاشیِ نرمِ حرفِ اول)، قیمتِ
 *  درشت، نامِ دوخطی، موجودی به خاکستری. فشردن ⇐ کمی کوچک (۰٫۹۶)؛ زدن ⇐ یک پله به سبد
 *  با لرزشِ کوتاه. اگر در سبد است، دایرهٔ زردِ کوچکِ مقدار گوشه‌اش می‌نشیند.
 */
@Composable
fun PhoneProductCard(item: PhoneCatalogItem, onAdd: () -> Unit) {
  val v = PhoneV3.colors
  val haptic = LocalHapticFeedback.current
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val motion = Motion.enabled
  val scale by animateFloatAsState(
    targetValue = if (pressed && motion) 0.96f else 1f,
    animationSpec = if (motion) spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium) else snap(),
    label = "cardPress",
  )
  val priceText = "${money(item.price)} ؋"
  val stockText = PhoneSaleText.stock(item.stock, item.unit)
  Box(
    Modifier
      .fillMaxWidth()
      .graphicsLayer { scaleX = scale; scaleY = scale },
  ) {
    Column(
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(PhoneV3.catalogCardRadius))
        .background(v.catalogCard)
        .clickable(
          interactionSource = interaction,
          indication = null,
          role = Role.Button,
          onClickLabel = "افزودن به سبد",
        ) {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onAdd()
        }
        .semantics(mergeDescendants = true) { contentDescription = "${item.name}، $priceText، $stockText" }
        .padding(start = 22.dp, end = 22.dp, top = 36.dp, bottom = 18.dp),
    ) {
      Box(Modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.Center) {
        val image = rememberProductImage(item.id, item.hasPhoto)
        if (image != null) {
          Image(
            image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(16.dp)),
          )
        } else {
          Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(26.dp)).background(v.bg),
            contentAlignment = Alignment.Center,
          ) {
            Text(CartSummary.initials(item.name), color = v.inkMuted, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 1)
          }
        }
      }
      Spacer(Modifier.height(30.dp))
      Text(
        priceText,
        color = v.ink,
        fontSize = PhoneSaleText.priceSp(priceText).sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
      )
      Spacer(Modifier.height(12.dp))
      Text(
        item.name,
        color = v.ink,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        minLines = 2,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(6.dp))
      Text(
        stockText,
        color = if (item.stock <= 0) v.negative else v.inkMuted,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    //  مقدارِ همین کالا در سبد — زردِ سبد، کوچک، گوشهٔ آغاز
    AnimatedVisibility(
      visible = item.inCart > 0,
      enter = if (motion) scaleIn(spring(dampingRatio = 0.5f, stiffness = 600f), initialScale = 0.6f) + fadeIn(tween(120)) else fadeIn(snap()),
      exit = if (motion) scaleOut(tween(120)) + fadeOut(tween(120)) else fadeOut(snap()),
      modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
    ) {
      Box(
        Modifier.size(24.dp).clip(CircleShape).background(v.cart),
        contentAlignment = Alignment.Center,
      ) {
        Text(qty(item.inCart), color = v.onCart, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
      }
    }
  }
}
