package ir.vil3ntec.tohid.ui.v3.products

import ir.vil3ntec.tohid.ui.v3.V3Collapse
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
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
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ProductThumb
import ir.vil3ntec.tohid.ui.v3.V3RowStatus
import ir.vil3ntec.tohid.ui.v3.V3RowTone
import ir.vil3ntec.tohid.ui.v3.V3DataRow
import ir.vil3ntec.tohid.ui.v3.V3DataRowSpec
import ir.vil3ntec.tohid.ui.v3.v3MetaText
import ir.vil3ntec.tohid.ui.v3.V3RowMeta
import ir.vil3ntec.tohid.ui.v3.V3SwipeAction
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  وضعیتِ آرامِ ردیف (سادهٔ e-rows-63d، به‌جای قرص): «کم» کهربایی، «تمام‌شده» قرمز، «غیرفعال» خاکستری؛ عادی ⇒ هیچ.
 *  رنگ هرگز تنها نیست — نوشته همیشه هست.
 */
internal fun badgeStatus(b: ProductsListModel.Badge): V3RowStatus? = when (b) {
  ProductsListModel.Badge.LOW -> V3RowStatus(StrProducts.filterLow, V3RowTone.ATTENTION)
  ProductsListModel.Badge.OUT -> V3RowStatus(StrProducts.filterOut, V3RowTone.DANGER)
  ProductsListModel.Badge.INACTIVE -> V3RowStatus(StrProducts.filterInactive)
  ProductsListModel.Badge.NONE -> null
}

/** «۱۲ عدد» — موجودی به واحدِ کالا */
internal fun stockAmount(p: Product, stock: Double): String = qty(stock) + if (p.unit.isNotBlank()) " ${p.unit}" else ""

/**
 *  خطِ دومِ ردیفِ فشرده: «۱۲۰ ؋ • ۱۵ عدد • کم» — قیمتِ فروش به رنگِ نوشته (رقمِ هم‌پهنا)، موجودی کم‌رنگ، و وضعیت فقط وقتی عادی
 *  نیست (فقط «تمام‌شده» رنگِ خطر). همان `V3RowMeta` ِ کالبدِ ۶۸c.
 */
internal fun productMeta(p: Product, stock: Double, badge: ProductsListModel.Badge): V3RowMeta =
  V3RowMeta("${money(p.salePrice)} ؋", V3RowStatus(stockAmount(p, stock)), badgeStatus(badge))

/** کارهای کشیدنیِ ردیفِ کالا: آغاز «ورود» (برند)، پایان «ویرایش» و «حذف» */
internal class RowSwipes(val onEntry: () -> Unit, val onEdit: () -> Unit, val onDelete: () -> Unit) {
  val start get() = listOf(V3SwipeAction(Icons.Outlined.MoveToInbox, StrProducts.swipeEntry, V3ButtonKind.PRIMARY, onEntry))
  val end get() = listOf(
    V3SwipeAction(Icons.Outlined.Edit, StrProducts.edit, V3ButtonKind.NEUTRAL, onEdit),
    V3SwipeAction(Icons.Outlined.DeleteOutline, StrProducts.delete, V3ButtonKind.DANGER, onDelete),
  )
}

/**
 *  ردیفِ فشردهٔ فهرست (کالبدِ `V3DataRow`، ۶۸c؛ ۱۴۰۵/۰۸/۲۴ دوخطی و ۶۴dp — `dense` — تا دست‌کم هشت کالا در یک صفحهٔ گوشی دیده شود):
 *  عکسِ ۴۰ یا کاشیِ حرف (`V3DataRowSpec.TILE`)، نام، و یک خطِ «۱۲۰ ؋ • ۱۵ عدد • کم» ([productMeta]) — وضعیت فقط وقتی «کم»،
 *  «تمام‌شده» یا «غیرفعال» است. دسته در تراشه‌ها و صفحهٔ کالا. در حالتِ انتخاب دایرهٔ تیک می‌آید و کشیدن خاموش است. `flash` =
 *  کالای تازه‌ثبت (۱٫۲ ثانیه درخشش).
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
  Box(Modifier.draftFlash(flash)) {
    V3DataRow(
      title = p.name,
      meta = productMeta(p, stock, badge),
      leading = {
        Box(Modifier.graphicsLayer { alpha = if (p.active) 1f else 0.55f }) {
          V3ProductThumb(p.id, p.name, size = V3DataRowSpec.TILE, version = ProductPhotos.version.intValue)
        }
      },
      startActions = swipes?.start.orEmpty(),
      endActions = swipes?.end.orEmpty(),
      selected = selected,
      onLongClick = onLongClick,
      dense = true,
      onClick = onClick,
    )
  }
}

/**
 *  کاشیِ شبکهٔ سه‌ستونه (۱۴۰۵/۰۸/۲۴ — هرگز کارتِ نیم‌صفحه‌ای): عکسِ مربعِ کوچک (r12)، نامِ دوخطیِ ۱۳، قیمت، و موجودیِ کم‌رنگ با
 *  وضعیتِ غیرعادی پیشش («کم • ۲ کیلو»، فقط «تمام‌شده» رنگِ خطر). همهٔ کاشی‌ها هم‌بلندی‌اند. انتخابِ گروهی با دایرهٔ تیک روی عکس.
 */
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
  val shape = RoundedCornerShape(V3Space.cardRadius)
  val edge = if (selected == true) PhoneV3Roles.focus(c) else c.hairline
  Column(
    modifier
      .testTag(ProductsListSpec.CELL_TAG)
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
        glyphScale = 0.34f,
      )
      Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { V3Collapse(selected != null) { CheckDot(selected == true) } }
    }
    Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
      Text(p.name, style = V3Type.subhead, color = c.ink, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
      Text(
        "${money(p.salePrice)} ؋", style = V3Type.subhead.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
      )
      //  موجودی کم‌رنگ؛ وضعیتِ غیرعادی پیش از آن («تمام‌شده • ۰ عدد» — فقط «تمام‌شده» رنگِ خطر)
      Text(
        v3MetaText(c, V3RowMeta(status = badgeStatus(badge), second = V3RowStatus(stockAmount(p, stock)))),
        style = V3Type.footnote, maxLines = 1, overflow = TextOverflow.Ellipsis,
      )
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

/**
 *  پایانِ فهرست (۱۴۰۵/۰۸/۲۴، جای کارتِ خلاصهٔ بالا — صاحب‌کار سربرگِ این فهرست‌ها را بی‌کار می‌داند): **یک** خطِ کم‌رنگِ وسط،
 *  «۴۰ قلم کالا · سودِ بالقوه ۱۲٬۴۰۰ ؋ اگر همه فروخته شود» — همان دو عدد (`ProductsListModel.stats`)، هیچ عددی گم نمی‌شود.
 */
@Composable
internal fun ProductsListFooter(stats: ProductsListModel.Stats, modifier: Modifier = Modifier) {
  Text(
    StrProducts.listFooter(money(stats.items.toDouble()), money(stats.potentialProfit)),
    style = V3Type.footnote,
    color = PhoneV3.colors.inkMuted,
    textAlign = TextAlign.Center,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier.fillMaxWidth().testTag(ProductsListSpec.FOOTER_TAG).padding(horizontal = 8.dp, vertical = 14.dp),
  )
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

/** خطِ میانِ ردیف‌های همان تکه (زیرِ متن، نه زیرِ عکس — همان `V3DataRowSpec.DIVIDER_START`) */
@Composable
internal fun RowDivider() = V3Divider(start = V3DataRowSpec.DIVIDER_START)

