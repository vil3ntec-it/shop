package ir.vil3ntec.tohid.ui.v3.products

import ir.vil3ntec.tohid.ui.v3.V3Collapse
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Space
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3ProductThumb
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3SwipeAction
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.V3Card
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight

/** برچسب و رنگِ §۸ ِ نشانِ ردیف — رنگ هرگز تنها نیست */
internal fun badgeStatus(b: ProductsListModel.Badge): Pair<V3Status, String>? = when (b) {
  ProductsListModel.Badge.LOW -> V3Status.AWAITING to StrProducts.filterLow
  ProductsListModel.Badge.OUT -> V3Status.CANCELLED to StrProducts.filterOut
  ProductsListModel.Badge.INACTIVE -> V3Status.INACTIVE to StrProducts.filterInactive
  ProductsListModel.Badge.NONE -> null
}

/** «دسته · ۱۲ عدد» */
internal fun stockLine(p: Product, stock: Double): String {
  val amount = qty(stock) + if (p.unit.isNotBlank()) " ${p.unit}" else ""
  return if (p.category.isBlank()) amount else "${p.category} · $amount"
}

/** کارهای کشیدنیِ ردیفِ کالا: آغاز «ورود» (برند)، پایان «ویرایش» و «حذف» */
internal class RowSwipes(val onEntry: () -> Unit, val onEdit: () -> Unit, val onDelete: () -> Unit) {
  val start get() = listOf(V3SwipeAction(Icons.Outlined.MoveToInbox, StrProducts.swipeEntry, V3ButtonKind.PRIMARY, onEntry))
  val end get() = listOf(
    V3SwipeAction(Icons.Outlined.Edit, StrProducts.edit, V3ButtonKind.NEUTRAL, onEdit),
    V3SwipeAction(Icons.Outlined.DeleteOutline, StrProducts.delete, V3ButtonKind.DANGER, onDelete),
  )
}

/**
 *  ردیفِ فهرست (۶۴dp): عکسِ ۴۸ یا کاشیِ حرف، نام، «دسته · موجودی»، قیمتِ فروشِ
 *  پررنگ و نشانِ وضعیت فقط وقتی «کم»، «تمام‌شده» یا «غیرفعال» است. در حالتِ انتخاب
 *  دایرهٔ تیک می‌آید و کشیدن خاموش است. `flash` = کالای تازه‌ثبت (۱٫۲ ثانیه درخشش).
 */
@Composable
internal fun ProductRow(
  p: Product,
  stock: Double,
  badge: ProductsListModel.Badge,
  selected: Boolean?,
  flash: Boolean,
  swipes: RowSwipes?,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)?,
) {
  val c = PhoneV3.colors
  Box(Modifier.draftFlash(flash)) {
    V3ListRow(
      title = p.name,
      subtitle = stockLine(p, stock),
      subtitleLines = 1,
      minHeight = 64.dp,
      leading = {
        Box(Modifier.graphicsLayer { alpha = if (p.active) 1f else 0.55f }) {
          V3ProductThumb(p.id, p.name, size = 48.dp, version = ProductPhotos.version.intValue)
        }
      },
      trailing = {
        Column(horizontalAlignment = Alignment.End) {
          Text("${money(p.salePrice)} ؋", style = V3Type.callout.copy(fontWeight = FontWeight.Bold), color = if (p.active) c.ink else c.inkMuted, maxLines = 1)
          badgeStatus(badge)?.let { (s, label) -> Spacer(Modifier.height(3.dp)); V3StatusPill(s, label) }
        }
      },
      chevron = false,
      startActions = swipes?.start.orEmpty(),
      endActions = swipes?.end.orEmpty(),
      selected = selected,
      onLongClick = onLongClick,
      onClick = onClick,
    )
  }
}

/** کاشیِ شبکهٔ دوستونه: کارتِ r16، عکسِ مربع، قیمتِ پررنگ، نامِ دوخطی و موجودیِ خاکستری */
@Composable
internal fun ProductCell(
  p: Product,
  stock: Double,
  badge: ProductsListModel.Badge,
  selected: Boolean?,
  flash: Boolean,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(V3Space.featuredRadius)
  val edge = if (selected == true) PhoneV3Roles.focus(c) else c.hairline
  Column(
    modifier
      .draftPressable(p.name, onClick, onLongClick)
      .draftFlash(flash, shape)
      .clip(shape)
      .background(if (selected == true) PhoneV3Palette.selected(c) else c.card)
      .border(if (selected == true) 2.dp else 1.dp, edge, shape)
      .semantics { if (selected != null) this.selected = selected },
  ) {
    Box {
      DraftProductPhoto(
        p.id, p.name,
        Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer { alpha = if (p.active) 1f else 0.55f },
        radius = 0.dp,
      )
      Box(Modifier.align(Alignment.TopStart).padding(8.dp)) { V3Collapse(selected != null) { CheckDot(selected == true) } }
      badgeStatus(badge)?.let { (s, label) -> Box(Modifier.align(Alignment.BottomStart).padding(8.dp)) { V3StatusPill(s, label) } }
    }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
      Text("${money(p.salePrice)} ؋", style = V3Type.headline, color = c.ink, maxLines = 1)
      Text(p.name, style = V3Type.subhead, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(36.dp))
      Text(stockLine(p, stock), style = V3Type.footnote, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

@Composable
private fun CheckDot(on: Boolean) {
  val c = PhoneV3.colors
  val pair = PhoneV3Roles.button(c, V3ButtonKind.PRIMARY)
  Box(
    Modifier.size(26.dp).clip(CircleShape).background(if (on) pair.bg else c.card).border(1.5.dp, if (on) pair.bg else PhoneV3Roles.inputBorder(c), CircleShape),
    contentAlignment = Alignment.Center,
  ) { if (on) Icon(Icons.Outlined.Check, contentDescription = null, tint = pair.fg, modifier = Modifier.size(16.dp)) }
}

/** کارتِ آمارِ بالای فهرست: دو خانه با شمارنده — «قلم کالا» و «سود بالقوه» */
@Composable
internal fun ProductsStatsCard(stats: ProductsListModel.Stats, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  val items = rememberCountUp(stats.items.toDouble())
  val profit = rememberCountUp(stats.potentialProfit)
  V3Card(modifier, radius = V3Space.featuredRadius, padding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp)) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
      StatCell(StrProducts.statItems, items.toLong().toString(), null, Modifier.weight(0.8f))
      Box(Modifier.width(1.dp).fillMaxHeight().background(c.hairline))
      StatCell(StrProducts.statPotential, "${money(profit)} ؋", StrProducts.statPotentialCaption, Modifier.weight(1.2f))
    }
  }
}

@Composable
private fun StatCell(label: String, value: String, caption: String?, modifier: Modifier) {
  val c = PhoneV3.colors
  Column(modifier.padding(horizontal = 16.dp)) {
    Text(label, style = V3Type.footnote, color = c.inkMuted, maxLines = 1)
    Spacer(Modifier.height(4.dp))
    Text(value, style = V3Type.figure, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (caption != null) Text(caption, style = V3Type.caption, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}


/**
 *  یک تکه از کارتِ گروهیِ فهرستِ تنبل: ردیفِ اول گوشه‌های بالا و ردیفِ آخر گوشه‌های
 *  پایین را دارد و مرزِ ۱dp فقط بیرونِ کارت کشیده می‌شود (نه میانِ ردیف‌ها) — همان
 *  `V3Card` با `V3Divider` در فهرستی که ردیف‌هایش جدا جدا ساخته می‌شوند.
 *  پیش‌نویس برای کیت (`V3Card` در `LazyColumn`).
 */
internal fun Modifier.draftGroupItem(first: Boolean, last: Boolean, radius: Dp = PhoneV3.groupRadius): Modifier = composed {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(
    topStart = if (first) radius else 0.dp, topEnd = if (first) radius else 0.dp,
    bottomStart = if (last) radius else 0.dp, bottomEnd = if (last) radius else 0.dp,
  )
  this
    .clip(shape)
    .background(c.card)
    .drawWithContent {
      drawContent()
      val sw = 1.dp.toPx()
      val top = if (first) 0f else sw * 4
      val bottom = if (last) 0f else sw * 4
      val outline = shape.createOutline(Size(size.width - sw, size.height - sw + top + bottom), layoutDirection, this)
      clipRect { translate(sw / 2, sw / 2 - top) { drawOutline(outline, c.hairline, style = Stroke(sw)) } }
    }
}

/** خطِ میانِ ردیف‌های همان تکه (زیرِ متن، نه زیرِ عکس) */
@Composable
internal fun RowDivider() = V3Divider(start = 72.dp)
