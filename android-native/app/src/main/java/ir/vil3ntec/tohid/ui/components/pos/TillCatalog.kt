package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.LocalV3NavSearch
import ir.vil3ntec.tohid.ui.v3.V3Search
import ir.vil3ntec.tohid.ui.v3.V3SearchBar
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberV3SearchToggle
import ir.vil3ntec.tohid.ui.screens.v3Text

/**
 *  صندوقِ نسخهٔ ۳ (۶۳w، حالتِ تمرکزِ ۱۴۰۵/۰۸/۲۴): سربرگ (برگشت، «فروش»، ذره‌بین، کاشی/فهرست، «…») یا — وقتی جستجو باز است —
 *  نوارِ جستجو با اسکن درونِ کادر؛ زیرش دسته‌ها و پرفروش‌ها، سپس کالاها. نوارِ شناورِ برنامه این‌جا نیست، پس ذره‌بینِ سربرگ همیشه
 *  هست (`LocalV3NavSearch` = false)؛ اسکن درونِ نوارِ سبدِ پایین است (`TillFrame`)، نه دکمهٔ شناورِ جدا. صفحه‌کلید فقط با زدنِ کادر.
 */
@Composable
internal fun TillCatalog(
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
  onBack: (() -> Unit)?,
  onScan: () -> Unit,
  emptyText: String,
  bottomGap: Dp,
  onTap: (PhoneCatalogItem) -> Unit,
  onHold: (PhoneCatalogItem) -> Unit,
  onMinus: (PhoneCatalogItem) -> Unit,
  onMenu: (() -> Unit)?,
  top: Dp,
  list: androidx.compose.foundation.lazy.LazyListState,
  grid: androidx.compose.foundation.lazy.grid.LazyGridState,
) {
  val v = PhoneV3.colors
  val spec = V3Search(search, onSearch, StrSale.catalogHint, onScan = onScan)
  val finder = rememberV3SearchToggle(spec)
  val navSearch = LocalV3NavSearch.current
  Column(Modifier.fillMaxSize()) {
    if (finder.shown) {
      //  `V3SearchBar` خودش زیرِ نوارِ وضعیت می‌نشیند؛ فقط کمینهٔ ۲۴ ِ همین قاب افزوده می‌شود
      val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
      Box(Modifier.padding(top = (top - inset).coerceAtLeast(0.dp))) { V3SearchBar(spec, finder.close) }
    } else {
      Row(
        Modifier.fillMaxWidth().padding(top = top + 6.dp, start = 4.dp, end = 12.dp).height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (onBack != null) {
          RoundButton(StrKit.back, onBack, 44.dp, null) { BackChevron(v.icon) }
          Spacer(Modifier.width(4.dp))
        } else Spacer(Modifier.width(12.dp))
        Text(Str.sale, color = v.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f).semantics { heading() })
        TillIcon(StrKit.scanBarcode, Icons.Outlined.QrCodeScanner, onScan)
        if (!navSearch) TillIcon(StrKit.search, Icons.Outlined.Search, finder.open)
        TillIcon(if (listMode) StrSale.gridView else StrSale.listView, if (listMode) Icons.Outlined.GridView else Icons.Outlined.ViewList, onToggleMode)
        if (onMenu != null) TillIcon(StrKit.more, Icons.Outlined.MoreHoriz, onMenu)
      }
    }
    Box(Modifier.padding(top = 4.dp)) { CategoryChips(categories, category, onCategory, onCategoryHold, onAddCategory) }
    if (search.isBlank() && bestsellers.isNotEmpty()) BestsellerRow(bestsellers, onTap)
    //  پایین: جای نوارِ سبد (`bottomGap`) تا آخرین ردیف پشتش نماند
    CatalogItems(
      items, listMode, false, {}, emptyText, PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomGap),
      list, grid, onTap, onHold, onMinus, Modifier.weight(1f).fillMaxSize(), till = true,
    )
  }
}

/** آیکنِ گردِ ۴۴dp ِ سربرگِ صندوق — همان آیکن و همان دایرهٔ کارتیِ مرزدارِ ردیفِ پیشین (آیکن‌ها عوض نمی‌شوند) */
@Composable
private fun TillIcon(label: String, icon: ImageVector, onClick: () -> Unit) {
  val v = PhoneV3.colors
  Spacer(Modifier.width(6.dp))
  RoundButton(label, onClick, 44.dp, v.card) { Icon(icon, null, tint = v.icon, modifier = Modifier.size(22.dp)) }
}

/**
 *  ردیفِ آرامِ فهرستِ صندوق (همان کالبدِ ردیفِ داده‌های ۶۸c، `V3DataRow`): کاشیِ ۴۰ (عکس یا حرفِ اول)، نام (یک خط، ۱۶) و
 *  «قیمت · موجودی» به کم‌رنگ (یک خط، ۱۳؛ «تمام شد» به رنگِ خطرِ آرام). −/+ **فقط** وقتی کالا در سبد است. زدنِ ردیف ⇐ +۱
 *  (وزنی ⇐ صفحه‌کلید)؛ نگه داشتن یا زدنِ مقدار ⇐ صفحه‌کلیدِ مقدار. ردیف‌ها یک کارتِ پیوسته با خطِ مویی از زیرِ متن‌اند.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TillListRow(
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
  val r = PhoneV3.groupRadius
  val shape = RoundedCornerShape(
    topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp,
    bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp,
  )
  val out = item.soldOut
  val dim = if (out) SOLD_OUT_ALPHA else 1f
  val hairline = v.hairline
  val priceText = PhoneSaleText.amount(item.price)
  val stockText = PhoneSaleText.stock(item)
  val danger = PhoneV3Roles.errorText(v)
  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 64.dp)
      .clip(shape)
      .background(v.card)
      .drawBehind {
        //  مرزِ کلِ گروه (لبه‌های همین ردیف) و خطِ مویی از زیرِ متن تا لبه — بی خطِ دوتایی میانِ ردیف‌ها
        val sw = 1.dp.toPx()
        val rad = r.toPx()
        val top = if (first) sw / 2 else -rad * 2
        val bottom = if (last) size.height - sw / 2 else size.height + rad * 2
        drawRoundRect(hairline, topLeft = Offset(sw / 2, top), size = Size(size.width - sw, bottom - top), cornerRadius = CornerRadius(rad, rad), style = Stroke(width = sw))
        if (!last) {
          val y = size.height - sw / 2
          val inset = (12 + 40 + 12).dp.toPx()
          val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
          drawLine(hairline, Offset(if (rtl) 0f else inset, y), Offset(if (rtl) size.width - inset else size.width, y), strokeWidth = sw)
        }
      }
      .combinedClickable(
        role = Role.Button,
        onClickLabel = if (item.weighed) StrSale.weigh else StrSale.addOne,
        onLongClickLabel = StrSale.typeQty,
        onLongClick = { onHold(item) },
        onClick = {
          if (!out) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onTap(item)
        },
      )
      .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.alpha(dim).graphicsLayer { scaleX = pop.value; scaleY = pop.value }) { TillThumb(item) }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f).alpha(dim)) {
      Text(item.name, style = V3Type.rowTitle, color = v.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(
        buildAnnotatedString {
          withStyle(SpanStyle(color = v.ink, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")) { append(priceText) }
          withStyle(SpanStyle(color = v.inkMuted)) { append("  ·  ") }
          withStyle(SpanStyle(color = if (out) danger else v.inkMuted)) { append(stockText) }
        },
        style = V3Type.rowHelp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    if (item.inCart > 0) {
      Spacer(Modifier.width(8.dp))
      TillQtyStepper(
        qty = qty(item.inCart),
        minusIcon = Icons.Outlined.Remove,
        minusLabel = StrSale.oneLess(item.name),
        minusTint = v.ink,
        plusLabel = if (item.weighed) StrSale.weigh else StrSale.oneMore(item.name),
        qtyLabel = StrSale.typeQty,
        onMinus = { onMinus(item) },
        onPlus = { onTap(item) },
        onQty = { onHold(item) },
      )
    }
  }
}

/** کاشیِ ۴۰ ِ کالا: عکس (فقط کالای عکس‌دار، بی خواندنِ دیسک برای بقیه) یا حرفِ اول به رنگِ پایدارِ پالت — مثلِ `V3ProductThumb` */
@Composable
private fun TillThumb(item: PhoneCatalogItem) {
  val v = PhoneV3.colors
  val image = rememberProductImage(item.id, item.hasPhoto)
  val shape = RoundedCornerShape(11.dp)
  Box(Modifier.size(40.dp).clip(shape).background(v.tile).border(1.dp, v.hairline, shape), contentAlignment = Alignment.Center) {
    if (image != null) {
      Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp))
    } else {
      val hue = ir.vil3ntec.tohid.ui.v3.v3ThumbHue(item.name)   // e-phone-p2: همان شش خانوادهٔ آرامِ برند، بی صورتی و سرخ
      Text(CartSummary.initials(item.name), style = v3Text(17, FontWeight.Bold, 22), color = PhoneV3Palette.glyph(v, hue), maxLines = 1)
    }
  }
}

/** −/+ ِ قرصیِ مرزدارِ صندوق (فهرستِ کالا و سبدِ باز) — مقدارِ میانه زدنی */
@Composable
internal fun TillQtyStepper(
  qty: String,
  minusIcon: ImageVector,
  minusLabel: String,
  minusTint: Color,
  plusLabel: String,
  qtyLabel: String,
  onMinus: () -> Unit,
  onPlus: () -> Unit,
  onQty: () -> Unit,
) {
  val v = PhoneV3.colors
  val shape = RoundedCornerShape(percent = 50)
  Row(Modifier.height(40.dp).clip(shape).border(1.dp, PhoneV3Roles.inputBorder(v), shape), verticalAlignment = Alignment.CenterVertically) {
    TillStepButton(minusIcon, minusLabel, minusTint, onMinus)
    Text(
      qty,
      style = v3Text(15, FontWeight.SemiBold, 20).copy(fontFeatureSettings = "tnum"),
      color = v.ink,
      maxLines = 1,
      modifier = Modifier
        .widthIn(min = 24.dp)
        .clip(RoundedCornerShape(8.dp))
        .clickable(role = Role.Button, onClickLabel = qtyLabel, onClick = onQty)
        .padding(horizontal = 4.dp, vertical = 8.dp),
    )
    TillStepButton(Icons.Outlined.Add, plusLabel, v.ink, onPlus)
  }
}

@Composable
private fun TillStepButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
  Box(
    Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
  ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) }
}
