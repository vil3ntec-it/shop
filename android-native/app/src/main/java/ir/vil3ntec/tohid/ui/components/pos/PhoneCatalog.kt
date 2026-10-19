package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
 *  کاتالوگِ صندوقِ گوشی (نسخهٔ ۳) — درونِ سطحِ روشنِ قابِ سبد (`PhoneSaleFrame`).
 *
 *  هدفِ صاحب محصول: فروشنده هر کالا را تند و دقیق برساند؛ کالای زیاد در دید، هیچ چیزِ
 *  درشت. پس فشرده و ثابت در بالا:
 *    ۱) برگشت · جستجو (نام، بارکد، کد) · اسکن · کاشی/فهرست   ← می‌ماند
 *    ۲) چیپ‌های کوچکِ دسته                                       ← می‌ماند
 *    ۳) «پرفروش‌ها»: قرص‌های یک‌ضربه‌ای (نام + قیمت) — هنگامِ جستجو پنهان
 *    ۴) کاشیِ سه‌ستونه (~۱۰۰dp) یا فهرستِ ۵۲dp، پرفروش‌ترین اول
 *  زدن ⇐ +۱ (لرزش و پرشِ کوچک)؛ کالای وزنی ⇐ صفحه‌کلید؛ نگه داشتن ⇐ صفحه‌کلید.
 *  در سبد ⇐ دایرهٔ زردِ مقدار و «−». رنگ‌ها فقط از `PhoneV3`.
 */

/** یک کالا — فقط داده‌ای که کاشی و ردیف نشان می‌دهند */
data class PhoneCatalogItem(
  val id: String,
  val name: String,
  val price: Double,
  val unit: String,
  val stock: Double,
  val hasPhoto: Boolean,
  /** مقدارِ همین کالا در سبد (۰ ⇐ بی‌نشان) */
  val inCart: Double,
  /** کالای وزنی/پیمانه‌ای (کیلو، لیتر، …): زدن ⇐ صفحه‌کلید، نه +۱ */
  val weighed: Boolean,
)

@Composable
fun PhoneCatalog(
  items: List<PhoneCatalogItem>,
  bestsellers: List<PhoneCatalogItem>,
  categories: List<String>,
  category: String?,
  onCategory: (String?) -> Unit,
  onCategoryHold: (String) -> Unit,
  onAddCategory: () -> Unit,
  search: String,
  onSearch: (String) -> Unit,
  listMode: Boolean,
  onToggleMode: () -> Unit,
  onBack: () -> Unit,
  onScan: () -> Unit,
  scanning: Boolean,
  scanPanel: @Composable () -> Unit,
  emptyText: String,
  bottomGap: Dp,
  onTap: (PhoneCatalogItem) -> Unit,
  onHold: (PhoneCatalogItem) -> Unit,
  onMinus: (PhoneCatalogItem) -> Unit,
) {
  val v = PhoneV3.colors
  //  زیرِ نوارِ وضعیت؛ کمینهٔ ۲۴ (بلندیِ نوارِ وضعیتِ گوشی) تا در پنجرهٔ بی‌نوار هم همان‌جا بنشیند
  val top = maxOf(WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), 24.dp)
  val grid = rememberLazyGridState()
  val list = rememberLazyListState()
  LaunchedEffect(scanning) {
    if (scanning) runCatching { if (listMode) list.scrollToItem(0) else grid.scrollToItem(0) }
  }

  Column(Modifier.fillMaxSize()) {
    /* ---------- ۱) بالای ثابت: برگشت، جستجو، اسکن، کاشی/فهرست ---------- */
    Row(
      Modifier.fillMaxWidth().padding(top = top + 6.dp, start = 4.dp, end = 12.dp).height(44.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      RoundButton("برگشت", onBack, 40.dp, null) { BackChevron(v.icon) }
      Spacer(Modifier.width(2.dp))
      SearchField(search, onSearch, Modifier.weight(1f))
      Spacer(Modifier.width(8.dp))
      RoundButton(if (scanning) "بستنِ اسکن" else "اسکنِ بارکد", onScan, 44.dp, v.catalogCard) {
        Icon(if (scanning) Icons.Outlined.Close else Icons.Outlined.QrCodeScanner, null, tint = v.icon, modifier = Modifier.size(22.dp))
      }
      Spacer(Modifier.width(6.dp))
      RoundButton(if (listMode) "نمایِ کاشی" else "نمایِ فهرست", onToggleMode, 44.dp, v.catalogCard) {
        Icon(if (listMode) Icons.Outlined.GridView else Icons.Outlined.ViewList, null, tint = v.icon, modifier = Modifier.size(22.dp))
      }
    }

    /* ---------- ۲) چیپ‌های دسته ---------- */
    CategoryChips(categories, category, onCategory, onCategoryHold, onAddCategory)

    /* ---------- ۳) پرفروش‌ها (هنگامِ جستجو نه) ---------- */
    if (search.isBlank() && bestsellers.isNotEmpty()) BestsellerRow(bestsellers, onTap)

    /* ---------- ۴) کاشی یا فهرست ---------- */
    Box(Modifier.weight(1f).fillMaxWidth()) {
      val pad = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomGap)
      if (listMode) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = pad) {
          if (scanning) item(key = "scan") { Box(Modifier.padding(bottom = 8.dp)) { scanPanel() } }
          if (items.isEmpty()) item(key = "empty") { EmptyText(emptyText) }
          itemsIndexed(items, key = { _, it -> it.id }) { i, it ->
            PhoneListRow(it, first = i == 0, last = i == items.lastIndex, onTap = onTap, onHold = onHold, onMinus = onMinus)
          }
        }
      } else {
        LazyVerticalGrid(
          columns = GridCells.Fixed(3),
          state = grid,
          modifier = Modifier.fillMaxSize(),
          contentPadding = pad,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (scanning) item(key = "scan", span = { GridItemSpan(maxLineSpan) }) { scanPanel() }
          if (items.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyText(emptyText) }
          items(items, key = { it.id }) { PhoneTile(it, onTap, onHold, onMinus) }
        }
      }
    }
  }
}

@Composable
private fun EmptyText(text: String) {
  Text(
    text,
    color = PhoneV3.colors.inkMuted,
    fontSize = 14.sp,
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
  )
}

@Composable
private fun RoundButton(label: String, onClick: () -> Unit, size: Dp, bg: Color?, content: @Composable () -> Unit) {
  Box(
    Modifier
      .size(size)
      .clip(CircleShape)
      .then(if (bg != null) Modifier.background(bg) else Modifier)
      .clickable(role = Role.Button, onClick = onClick)
      .semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
  ) { content() }
}

/** پیکانِ نازکِ برگشت — در راست‌به‌چپ رو به راست */
@Composable
private fun BackChevron(color: Color) {
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Canvas(Modifier.size(20.dp)) {
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
 *  کادرِ جستجو — نام، بارکد یا کد؛ فارسی/عربی و رقم‌ها یکی (`CatalogSearch`). با باز
 *  شدنِ صفحه فوکوس نمی‌گیرد تا صفحه‌کلید روی کاتالوگ نپرد.
 */
@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
  val v = PhoneV3.colors
  Row(
    modifier
      .height(44.dp)
      .clip(RoundedCornerShape(percent = 50))
      .background(v.catalogCard)
      .padding(start = 14.dp, end = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Outlined.Search, contentDescription = null, tint = v.inkMuted, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(8.dp))
    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
      if (value.isEmpty()) Text("نام، بارکد یا کدِ کالا", color = v.inkMuted, fontSize = 14.sp, maxLines = 1)
      BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = v.ink, fontSize = 14.sp),
        cursorBrush = SolidColor(v.ink),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "جستجوی کالا" },
      )
    }
    if (value.isNotEmpty()) {
      RoundButton("پاک کردنِ جستجو", { onValueChange("") }, 36.dp, null) {
        Icon(Icons.Outlined.Close, contentDescription = null, tint = v.icon, modifier = Modifier.size(18.dp))
      }
    } else {
      Spacer(Modifier.width(10.dp))
    }
  }
}

/** چیپ‌های کوچکِ دسته: «همه»، دسته‌ها و «+» (دستهٔ تازه)؛ نگه داشتنِ دسته ⇐ حذف (با تأیید) */
@Composable
private fun CategoryChips(
  categories: List<String>,
  category: String?,
  onCategory: (String?) -> Unit,
  onHold: (String) -> Unit,
  onAdd: () -> Unit,
) {
  Row(
    Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(start = 12.dp, end = 12.dp, top = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Chip("همه", selected = category == null, onClick = { onCategory(null) }, onHold = null)
    categories.forEach { c ->
      Chip(c, selected = category == c, onClick = { onCategory(if (category == c) null else c) }, onHold = { onHold(c) })
    }
    RoundButton("دستهٔ تازه", onAdd, 32.dp, PhoneV3.colors.catalogCard) {
      Icon(Icons.Outlined.Add, contentDescription = null, tint = PhoneV3.colors.icon, modifier = Modifier.size(16.dp))
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit, onHold: (() -> Unit)?) {
  val v = PhoneV3.colors
  Box(
    Modifier
      .height(32.dp)
      .clip(RoundedCornerShape(percent = 50))
      .background(if (selected) v.ink else v.catalogCard)
      .combinedClickable(
        role = Role.Tab,
        onLongClickLabel = if (onHold != null) "حذفِ این دسته" else null,
        onLongClick = onHold,
        onClick = onClick,
      )
      .padding(horizontal = 14.dp),
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

/** «پرفروش‌ها»: قرص‌های یک‌ضربه‌ای (نام + قیمت) — سریع‌ترین راه به کالاهای همیشگی */
@Composable
private fun BestsellerRow(items: List<PhoneCatalogItem>, onTap: (PhoneCatalogItem) -> Unit) {
  val v = PhoneV3.colors
  val haptic = LocalHapticFeedback.current
  Row(
    Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(start = 12.dp, end = 12.dp, top = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("پرفروش‌ها", color = v.inkMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    items.forEach { b -> key(b.id) {
      val pop = rememberAddPop(b.inCart)
      Row(
        Modifier
          .height(32.dp)
          .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
          .alpha(if (b.stock <= 0) SOLD_OUT_ALPHA else 1f)
          .clip(RoundedCornerShape(percent = 50))
          .background(if (b.inCart > 0) v.cart else v.catalogCard)
          .clickable(role = Role.Button, onClickLabel = if (b.weighed) "وزن کردن" else "یکی به سبد") {
            if (b.stock > 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onTap(b)
          }
          .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val on = if (b.inCart > 0) v.onCart else v.ink
        Text(b.name, color = on, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 116.dp))
        Spacer(Modifier.width(6.dp))
        Text("${money(b.price)} ؋", color = on, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
      }
    } }
  }
}

/** کالای «تمام شد»: محتوا کم‌رنگ (۴۵٪)، بی لرزش و بی پرش؛ زدنش پیامِ موجودی می‌دهد */
private const val SOLD_OUT_ALPHA = 0.45f

/** پرشِ کوچکِ کاشی وقتی مقدارش در سبد بیشتر شد (بندِ G) */
@Composable
private fun rememberAddPop(inCart: Double): Animatable<Float, AnimationVector1D> {
  val pop = remember { Animatable(1f) }
  val last = remember { mutableStateOf(inCart) }
  LaunchedEffect(inCart) {
    val grew = inCart > last.value
    last.value = inCart
    if (grew && Motion.enabled) {
      pop.snapTo(1.06f)
      pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 700f))
    }
  }
  return pop
}

/**
 *  دایرهٔ ۳۲dpِ آغازِ کاشی/ردیف: عکس، یا حرفِ اول؛ در سبد ⇐ زرد با مقدار (نشانِ مقدار).
 *  عکس‌دار و در سبد ⇐ نشانِ زردِ کوچک روی گوشهٔ عکس.
 */
@Composable
private fun ItemThumb(item: PhoneCatalogItem) {
  val v = PhoneV3.colors
  val image = rememberProductImage(item.id, item.hasPhoto)
  val inCart = item.inCart > 0
  val qtyText = qty(item.inCart)
  Box(Modifier.size(32.dp)) {
    Box(
      Modifier.size(32.dp).clip(CircleShape).background(if (inCart && image == null) v.cart else v.bg),
      contentAlignment = Alignment.Center,
    ) {
      when {
        image != null -> Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(32.dp))
        inCart -> Text(qtyText, color = v.onCart, fontSize = if (qtyText.length <= 3) 12.sp else 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        else -> Text(CartSummary.initials(item.name), color = v.inkMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
      }
    }
    if (image != null && inCart) {
      Box(
        Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(18.dp).clip(CircleShape).background(v.cart),
        contentAlignment = Alignment.Center,
      ) {
        Text(qtyText, color = v.onCart, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
      }
    }
  }
}

/** «−» کوچکِ کالای درونِ سبد */
@Composable
private fun MinusButton(item: PhoneCatalogItem, size: Dp, onMinus: (PhoneCatalogItem) -> Unit) {
  RoundButton("یکی کمتر ${item.name}", { onMinus(item) }, size, PhoneV3.colors.bg) {
    Icon(Icons.Outlined.Remove, contentDescription = null, tint = PhoneV3.colors.icon, modifier = Modifier.size(size * 0.6f))
  }
}

/**
 *  کاشیِ کوچکِ کالا (~۱۰۰dp): دایرهٔ ۳۲ و نامِ دوخطیِ ۱۳sp، قیمتِ پررنگِ ۱۴sp، موجودیِ
 *  کوچکِ خاکستری (سرخ «تمام شد»). فشردن ⇐ ۰٫۹۶؛ زدن ⇐ +۱ با لرزش و پرش (وزنی ⇐
 *  صفحه‌کلید)؛ نگه داشتن ⇐ صفحه‌کلید. در سبد ⇐ مقدارِ زرد و «−».
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhoneTile(
  item: PhoneCatalogItem,
  onTap: (PhoneCatalogItem) -> Unit,
  onHold: (PhoneCatalogItem) -> Unit,
  onMinus: (PhoneCatalogItem) -> Unit,
) {
  val v = PhoneV3.colors
  val haptic = LocalHapticFeedback.current
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val motion = Motion.enabled
  val press by animateFloatAsState(
    targetValue = if (pressed && motion) 0.96f else 1f,
    animationSpec = if (motion) spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium) else snap(),
    label = "tilePress",
  )
  val pop = rememberAddPop(item.inCart)
  val priceText = "${money(item.price)} ؋"
  val stockText = PhoneSaleText.stock(item.stock, item.unit)
  val out = item.stock <= 0
  Column(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 100.dp)
      .graphicsLayer { val s = press * pop.value; scaleX = s; scaleY = s }
      .clip(RoundedCornerShape(PhoneV3.tileRadius))
      .background(v.catalogCard)
      .combinedClickable(
        interactionSource = interaction,
        indication = null,
        role = Role.Button,
        onClickLabel = if (item.weighed) "وزن کردن" else "یکی به سبد",
        onLongClickLabel = "نوشتنِ مقدار",
        onLongClick = { onHold(item) },
        onClick = {
          if (!out) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onTap(item)
        },
      )
      .semantics(mergeDescendants = true) {
        contentDescription = "${item.name}، $priceText، $stockText" +
          if (item.inCart > 0) "، ${qty(item.inCart)} در سبد" else ""
      }
      .padding(8.dp),
  ) {
    Column(Modifier.alpha(if (out) SOLD_OUT_ALPHA else 1f)) {
      Row(verticalAlignment = Alignment.Top) {
        ItemThumb(item)
        Spacer(Modifier.width(6.dp))
        Text(
          item.name,
          color = v.ink,
          fontSize = 13.sp,
          lineHeight = 18.sp,
          fontWeight = FontWeight.SemiBold,
          minLines = 2,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
      }
      Spacer(Modifier.height(6.dp))
      Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
          priceText,
          color = v.ink,
          fontSize = PhoneSaleText.priceSp(priceText).sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          softWrap = false,
          modifier = Modifier.weight(1f),
        )
        if (item.inCart > 0) MinusButton(item, 24.dp, onMinus)
      }
      Text(
        stockText,
        color = if (out) v.negative else v.inkMuted,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 *  ردیفِ فهرست (۵۲dp): دایره، نام، قیمت · موجودی، و در انتها «+» (در سبد ⇐ «−» مقدار «+»).
 *  ردیف‌ها یک کارتِ سفیدِ پیوسته با خطِ مویی‌اند (مثلِ گروه‌های r5).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhoneListRow(
  item: PhoneCatalogItem,
  first: Boolean,
  last: Boolean,
  onTap: (PhoneCatalogItem) -> Unit,
  onHold: (PhoneCatalogItem) -> Unit,
  onMinus: (PhoneCatalogItem) -> Unit,
) {
  val v = PhoneV3.colors
  val haptic = LocalHapticFeedback.current
  val pop = rememberAddPop(item.inCart)
  val r = PhoneV3.tileRadius
  val shape = RoundedCornerShape(
    topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp,
    bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp,
  )
  val priceText = "${money(item.price)} ؋"
  val stockText = PhoneSaleText.stock(item.stock, item.unit)
  val hairline = v.hairline
  val out = item.stock <= 0
  val dim = if (out) SOLD_OUT_ALPHA else 1f
  Row(
    Modifier
      .fillMaxWidth()
      .height(52.dp)
      .clip(shape)
      .background(v.catalogCard)
      .drawBehind {
        if (!last) {
          val y = size.height - 0.5.dp.toPx()
          drawLine(hairline, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
      }
      .combinedClickable(
        role = Role.Button,
        onClickLabel = if (item.weighed) "وزن کردن" else "یکی به سبد",
        onLongClickLabel = "نوشتنِ مقدار",
        onLongClick = { onHold(item) },
        onClick = {
          if (!out) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onTap(item)
        },
      )
      .padding(start = 10.dp, end = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.alpha(dim).graphicsLayer { scaleX = pop.value; scaleY = pop.value }) { ItemThumb(item) }
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f).alpha(dim)) {
      Text(item.name, color = v.ink, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(priceText, color = v.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(
          "  ·  $stockText",
          color = if (item.stock <= 0) v.negative else v.inkMuted,
          fontSize = 12.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    Row(Modifier.alpha(dim), verticalAlignment = Alignment.CenterVertically) {
      if (item.inCart > 0) {
        MinusButton(item, 34.dp, onMinus)
        Text(
          qty(item.inCart),
          color = v.ink,
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          textAlign = TextAlign.Center,
          modifier = Modifier.widthIn(min = 28.dp),
        )
      }
      RoundButton("یکی بیشتر ${item.name}", { if (!out) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onTap(item) }, 34.dp, v.cart) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = v.onCart, modifier = Modifier.size(20.dp))
      }
    }
  }
}
