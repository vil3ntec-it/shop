package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.components.pos.CartSummary
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.components.pos.TillMenu
import ir.vil3ntec.tohid.ui.components.pos.TillQtyStepper
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Chevron
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3IconButton
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3ProductThumb
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.screens.v3Text

/*
 *  تکه‌های سبدِ بازِ گوشی (نمایشِ تدریجی، ۱۴۰۵/۰۸/۲۴). پیش‌فرض فقط این‌ها دیده می‌شوند:
 *    سرِ سبد («سبد · ۳ قلم» و «…») ← فاکتورهای باز فقط وقتی بیش از یکی است ← قلم‌ها با −/+ ← مشتری (یک خط) ← جمع ← «پرداخت».
 *  هر کارِ دیگر (پیش‌فاکتور، سفارشِ ویژه، لیستِ قیمت، یادداشت، تحویلِ بعدی، …) یک ردیفِ «…» است با توضیحِ یک‌خطی (`TillMenu`).
 *  رنگ فقط از تم (`PhoneV3`)؛ رنگِ برند فقط روی «پرداخت».
 */

/** عنوان و مبلغِ سبدِ جمع‌شده، با پیش‌نمایشِ قلم‌های تازه در فضای موجود. */
@Composable
internal fun CartDockPreview(model: CartSummary.Model, modifier: Modifier = Modifier, bump: Float = 1f) {
  val c = PhoneV3.colors
  val total = rememberCountUp(model.total)
  val amount = PhoneSaleText.amount(total)
  BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
    val photoSpace = maxWidth - if (PhoneSaleText.amount(model.total).length > 10) 24.dp else 0.dp
    val photos = when {
      photoSpace >= 155.dp -> 3
      photoSpace >= 134.dp -> 2
      photoSpace >= 113.dp -> 1
      else -> 0
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Column(Modifier.weight(1f)) {
        Text(StrSale.cartWord, style = V3Type.footnote, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
          amount, style = V3Type.subhead.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
          color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
          modifier = Modifier.graphicsLayer { scaleX = bump; scaleY = bump },
        )
      }
      if (photos > 0) {
        Row(horizontalArrangement = Arrangement.spacedBy((-5).dp)) {
          model.thumbs.take(photos).forEachIndexed { index, thumb ->
            key(index, thumb.id) { V3ProductThumb(thumb.id, thumb.initials, size = 27.dp, shape = CircleShape) }
          }
        }
      }
      CartCountBadge(model.count)
    }
  }
}

@Composable
private fun CartCountBadge(count: Int) {
  val c = PhoneV3.colors
  val brand = PhoneV3Roles.button(c, V3ButtonKind.PRIMARY)
  Box(
    Modifier.defaultMinSize(minWidth = 32.dp, minHeight = 32.dp)
      .clip(CircleShape).background(brand.bg).padding(horizontal = 7.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(plain(count), style = V3Type.subhead.copy(fontWeight = FontWeight.Bold), color = brand.fg, maxLines = 1)
  }
}

@Composable
internal fun TillCartHeader(count: Int, menu: TillMenu, onMore: () -> Unit, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  Row(
    modifier.fillMaxWidth().padding(top = 3.dp, bottom = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(c.tile), contentAlignment = Alignment.Center) {
      Icon(Icons.Outlined.ShoppingBasket, null, tint = c.ink, modifier = Modifier.size(21.dp))
    }
    Column(Modifier.weight(1f)) {
      Text(StrSale.cart, style = V3Type.title, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
      Text(StrSale.linesCount(plain(count)), style = V3Type.footnote, color = c.inkMuted, maxLines = 1)
    }
    TillMoreButton(menu, onMore)
  }
}

/** «…» ِ سبد — جدا تا خواندنِ فهرستِ زنده فقط همین دکمه را از نو بکشد، نه ابزارها را */
@Composable
private fun TillMoreButton(menu: TillMenu, onMore: () -> Unit) {
  if (menu.actions.isEmpty()) {
    Spacer(Modifier.size(44.dp))
    return
  }
  Box(Modifier.testTag(TillSpec.MORE_TAG)) { V3IconButton(Icons.Outlined.MoreHoriz, StrSale.cartMore, onClick = onMore) }
}

/**
 *  یک قلمِ سبد: عکس، نام و قیمت واحد در بالا، جمع ردیف و کنترل تعداد در پایین. زدن یا نگه
 *  داشتنِ ردیف و زدنِ مقدار ⇐ «تعداد و قیمتِ این ردیف» (`sale/line/{i}`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TillCartRow(row: CartSummary.Row, onMinus: () -> Unit, onPlus: () -> Unit, onDetails: () -> Unit) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(18.dp)
  Column(
    Modifier.fillMaxWidth()
      .clip(shape)
      .background(c.tile)
      .combinedClickable(onClickLabel = StrSale.lineDetails, onLongClickLabel = StrSale.lineDetails, onLongClick = onDetails, onClick = onDetails)
      .testTag(TillSpec.ROW_TAG)
      .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 6.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3ProductThumb(row.id, row.name, size = 40.dp, shape = CircleShape)
      Column(Modifier.weight(1f)) {
        Text(row.name, style = V3Type.body.copy(fontWeight = FontWeight.SemiBold), color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val unit = if (row.unit.isNotBlank()) " / ${row.unit}" else ""
        val custom = if (row.custom && !row.free) " · ${StrSale.manualPriceTag}" else ""
        Text(PhoneSaleText.amount(row.unitPrice) + unit + custom, style = V3Type.footnote, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
    Row(
      Modifier.fillMaxWidth().padding(start = 50.dp, top = 3.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        PhoneSaleText.amount(row.lineTotal), style = V3Type.subhead.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
        color = c.ink, modifier = Modifier.weight(1f),
      )
      TillStepper(row, onMinus, onPlus, onDetails)
    }
  }
}

/** −/+ همان قرصِ فهرستِ کالا (`TillQtyStepper`)؛ پلهٔ آخر ⇒ سطل؛ مقدار ⇐ صفحه‌کلیدِ مقدار و قیمت */
@Composable
internal fun TillStepper(row: CartSummary.Row, onMinus: () -> Unit, onPlus: () -> Unit, onEdit: () -> Unit) {
  val c = PhoneV3.colors
  TillQtyStepper(
    qty = qty(row.quantity),
    minusIcon = if (row.lastStep) Icons.Outlined.DeleteOutline else Icons.Outlined.Remove,
    minusLabel = if (row.lastStep) StrSale.removeNamed(row.name) else StrSale.oneLess(row.name),
    minusTint = if (row.lastStep) PhoneV3Roles.errorText(c) else c.ink,
    plusLabel = StrSale.oneMore(row.name),
    qtyLabel = StrSale.typeQtyPrice,
    onMinus = onMinus,
    onPlus = onPlus,
    onQty = onEdit,
  )
}

/**
 *  مشتریِ فاکتور در **یک خط**: «مشتری گذری ›» یا «نام · بدهی … ×». زدن ⇐ انتخابِ قرض‌دار (برای نسیه)؛ × ⇐ برداشتن.
 */
@Composable
internal fun TillCustomerRow(name: String?, balance: String?, onPick: () -> Unit, onClear: () -> Unit) {
  val c = PhoneV3.colors
  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 52.dp)
      .clip(RoundedCornerShape(12.dp))
      .clickable(role = Role.Button, onClickLabel = StrSale.pickDebtor, onClick = onPick)
      .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(c.tile), contentAlignment = Alignment.Center) {
      Icon(Icons.Outlined.Person, contentDescription = null, tint = c.icon, modifier = Modifier.size(22.dp))
    }
    Spacer(Modifier.width(12.dp))
    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
      Text(name ?: StrSale.walkIn, style = V3Type.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
      if (balance != null) Text("  ·  $balance", style = V3Type.footnote, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (name != null) {
      Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClear).semantics { contentDescription = StrSale.removeDebtor },
        contentAlignment = Alignment.Center,
      ) { Icon(Icons.Outlined.Close, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(18.dp)) }
    } else {
      V3Chevron()
    }
  }
}

/** ردیفِ کم‌رنگِ کم‌شدن از جمع (تخفیفِ دستی، تخفیفِ خودکار) — فقط وقتی هست */
@Composable
internal fun TillAdjustRow(title: String, amount: Double) {
  val c = PhoneV3.colors
  Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(title, style = V3Type.footnote, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    Text(PhoneSaleText.amount(-amount), style = V3Type.footnote, color = c.inkMuted, maxLines = 1)
  }
}

/** «جمع» و مبلغِ درشت؛ نشان‌های کم‌رنگِ غیرِ پیش‌فرض (مثلاً «لیستِ قیمت: عمده») فقط وقتی هستند */
@Composable
internal fun TillTotal(total: Double, menu: TillMenu) {
  val c = PhoneV3.colors
  val notices = menu.notices
  Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(StrSale.totalColon, style = V3Type.callout, color = c.inkMuted, maxLines = 1)
      Spacer(Modifier.width(8.dp))
      Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        notices.take(1).forEach { TillMutedChip(it) }
      }
      val text = PhoneSaleText.amount(total)
      Text(text, style = v3Text(if (text.length <= 12) 26 else 22, FontWeight.Bold, 32).copy(fontFeatureSettings = "tnum"), color = c.ink, maxLines = 1, softWrap = false)
    }
    notices.drop(1).forEach { Text(it, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 4.dp)) }
  }
}

@Composable
private fun TillMutedChip(text: String) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(percent = 50)
  Text(
    text,
    style = V3Type.caption,
    color = c.inkMuted,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.clip(shape).border(1.dp, c.hairline, shape).padding(horizontal = 10.dp, vertical = 4.dp),
  )
}

/**
 *  «…» ِ سبد: هر کار یک ردیف با توضیحِ یک‌خطی، در دو گروه («همین فاکتور»، «کارهای دیگرِ صندوق»). زدن ⇒ برگه با حرکت پایین
 *  می‌رود و بعد پنجرهٔ همان ابزار باز می‌شود. تنظیمِ خاموش و فهرستِ خالی ردیفی ندارند (خودِ ابزار نمی‌دهد).
 */
@Composable
internal fun TillMoreSheet(menu: TillMenu, onClose: () -> Unit) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(PhoneV3.groupRadius)
  val chosen = remember { booleanArrayOf(false) }
  val groups = menu.actions.groupBy { it.group }.toSortedMap()
  V3Sheet(onDismiss = { if (!chosen[0]) onClose() }, title = StrSale.moreTitle, detents = false) { closer ->
    groups.entries.forEachIndexed { gi, (group, actions) ->
      if (groups.size > 1) {
        Text(
          if (group == 0) StrSale.groupThisInvoice else StrSale.groupOtherTasks,
          style = V3Type.footnote,
          color = c.inkMuted,
          modifier = Modifier.padding(start = 4.dp, bottom = 6.dp, top = if (gi > 0) 14.dp else 0.dp),
        )
      }
      Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, c.hairline, shape)) {
        actions.forEachIndexed { i, a ->
          if (i > 0) V3Divider(start = 64.dp)
          V3ListRow(a.title, subtitle = a.hint, icon = a.icon, hue = a.hue, onClick = {
            chosen[0] = true
            closer.close { onClose(); a.onClick() }
          })
        }
      }
    }
  }
}
