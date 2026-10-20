package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.SegmentInnerShape
import ir.vil3ntec.tohid.ui.theme.segmentSelected
import ir.vil3ntec.tohid.ui.theme.segmentTrack
import ir.vil3ntec.tohid.ui.theme.softCard
import ir.vil3ntec.tohid.ui.theme.tabular
import ir.vil3ntec.tohid.ui.theme.zone
import ir.vil3ntec.tohid.ui.theme.Shape as ShopShape

/*
 *  ناحیهٔ «سبد خرید» (VISUAL-DIRECTION «نسخهٔ ۲»، سمتِ چپ در RTL).
 *
 *  نقدِ صاحب محصول (۱۴۰۵/۰۷/۲۷): «جعبهٔ قرمز برای چیست؟ چرا این‌قدر شلوغ؟». پس
 *  سبد یک ناحیهٔ شناختنی با رنگِ خودش است (`zoneCart`): سربرگ با شمار و دو کارِ
 *  سبد، تراشهٔ مشتری، ردیف‌های فشرده (عکس، نام، شمارنده، جمعِ ردیف) روی کارتِ
 *  سفید، خلاصهٔ پرداخت، روشِ پرداخت به‌شکلِ کنترلِ قطعه‌ای (فقط روش‌های واقعیِ
 *  موتور)، دکمهٔ بزرگِ «ثبتِ فروش» و «چاپِ آخر» کوچک. همه فقط نمایش‌اند؛ هر کار
 *  از صفحه (`DeskSaleScreen`) می‌آید و کلیدهایش همان است.
 */

/** قابِ ناحیهٔ سبد — هر بخش یک slot است تا صفحه کار و کلیدش را خودش نگه دارد */
@Composable
fun OrderPanel(
  itemCount: Int,
  customer: @Composable () -> Unit,
  items: @Composable ColumnScope.() -> Unit,
  summary: @Composable () -> Unit,
  methods: @Composable () -> Unit,
  actions: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  headerActions: @Composable RowScope.() -> Unit = {},
) {
  val c = Shop.colors
  //  VILL3N: سبد پنلِ مرزدار است (سطحِ درونی + خطِ دورِ کارت) — جدا از کاتالوگ روی زمینه
  Column(modifier.zone(c.zoneCart, border = c.borderCard).padding(14.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Filled.ShoppingBasket, contentDescription = null, tint = c.iconActive, modifier = Modifier.size(22.dp))
      Spacer(Modifier.width(8.dp))
      Text("سبد خرید", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.text)
      Spacer(Modifier.width(8.dp))
      Text(
        "$itemCount قلم",
        Modifier.clip(ShopShape.chip).background(c.surface).padding(horizontal = 10.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium.tabular(), color = c.primaryDark, fontWeight = FontWeight.SemiBold,
      )
      Spacer(Modifier.weight(1f))
      headerActions()
    }
    Spacer(Modifier.height(10.dp))
    customer()
    Spacer(Modifier.height(10.dp))
    Column(Modifier.weight(1f).fillMaxWidth().softCard(c, ShopShape.card)) { items() }
    Spacer(Modifier.height(10.dp))
    summary()
    Spacer(Modifier.height(10.dp))
    methods()
    Spacer(Modifier.height(12.dp))
    actions()
  }
}

/** کارِ کوچکِ سربرگِ سبد («قلمِ آزاد»، «خالی کردن») — آیکن + نوشته، رنگِ خطر فقط برای پاک‌کردن */
@Composable
fun CartHeaderAction(label: String, icon: ImageVector, hint: String?, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
  val c = Shop.colors
  val fg = when { !enabled -> c.muted2; danger -> c.danger; else -> c.primaryDark }
  TextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.heightIn(min = 32.dp)) {
    Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
    Spacer(Modifier.width(4.dp))
    Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    hint?.let { Spacer(Modifier.width(4.dp)); PosKeyHint(it) }
  }
}

/** تراشهٔ مشتری: مشتریِ گذری یا قرض‌دارِ انتخاب‌شده، با F2 و × برای برداشتن */
@Composable
fun CustomerRow(
  name: String?,
  detail: String?,
  pickHint: String,
  target: Dp,
  onPick: () -> Unit,
  onRemove: () -> Unit,
) {
  val c = Shop.colors
  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = target + 4.dp)
      .clip(ShopShape.chip)
      .background(c.surface)
      .clickable(onClickLabel = "انتخابِ مشتری", onClick = onPick)
      .padding(start = 6.dp, end = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(28.dp).clip(CircleShape).background(if (name != null) c.primaryTint else c.surface2), contentAlignment = Alignment.Center) {
      Icon(Icons.Filled.Person, contentDescription = null, tint = if (name != null) c.primaryDark else c.muted, modifier = Modifier.size(17.dp))
    }
    Spacer(Modifier.width(8.dp))
    Text(
      name ?: "مشتریِ گذری",
      Modifier.weight(1f),
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = if (name != null) FontWeight.SemiBold else FontWeight.Medium,
      color = if (name != null) c.text else c.muted,
      maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
    PosKeyHint(pickHint)
    if (name != null) {
      Spacer(Modifier.width(4.dp))
      Icon(
        Icons.Filled.Close, contentDescription = "برداشتنِ مشتری", tint = c.muted,
        modifier = Modifier.size(28.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onRemove).padding(5.dp),
      )
    } else Spacer(Modifier.width(6.dp))
  }
  detail?.let { Text(it, Modifier.padding(start = 12.dp, top = 4.dp), style = MaterialTheme.typography.bodySmall, color = c.muted) }
}

/** یک ردیفِ سبد برای پنل */
class OrderLineView(
  val id: String,
  val name: String,
  val unit: String,
  val quantity: Double,
  val price: Double,
  /** قیمتِ دستی (F3) — به رنگِ اصلی */
  val customPrice: Boolean,
  /** دستهٔ کالا — رنگِ حرفِ اول همان رنگِ کارتِ کاتالوگ */
  val category: String = "",
)

/** اقلامِ سبد (ردیفِ فشرده: عکس، نام و قیمتِ واحد، شمارنده، جمعِ ردیف) یا حالتِ خالی با راهنما */
@Composable
fun OrderLines(
  lines: List<OrderLineView>,
  selected: String?,
  target: Dp,
  state: LazyListState,
  showPhotos: Boolean,
  onSelect: (String) -> Unit,
  onEdit: (String) -> Unit,
  onMinus: (String) -> Unit,
  onPlus: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = Shop.colors
  if (lines.isEmpty()) {
    Column(
      modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(Modifier.size(56.dp).clip(CircleShape).background(c.primaryTint), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.ShoppingBasket, contentDescription = null, tint = c.primaryDark, modifier = Modifier.size(26.dp))
      }
      Spacer(Modifier.height(10.dp))
      Text("سبد خالی است", style = MaterialTheme.typography.titleSmall, color = c.text)
      Spacer(Modifier.height(4.dp))
      Text(
        "بارکد را بخوانید، نام را بنویسید و Enter بزنید، یا روی کارتِ کالا بزنید.",
        style = MaterialTheme.typography.bodySmall, color = c.muted, textAlign = TextAlign.Center,
      )
    }
    return
  }
  LazyColumn(state = state, modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(6.dp)) {
    itemsIndexed(lines, key = { _, l -> l.id }) { i, line ->
      val on = line.id == selected
      Row(
        Modifier
          .fillMaxWidth()
          .clip(ShopShape.button)
          .background(if (on) c.primaryTint else Color.Transparent)
          .semantics { this.selected = on }
          .clickable(onClickLabel = "انتخابِ ردیف") { onSelect(line.id) }
          .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ProductThumb(line.id, line.name, 36.dp, showPhotos, tintKey = line.category)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
          Text(line.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Row(
            Modifier
              .clip(ShopShape.chip)
              .clickable(onClickLabel = "ویرایشِ تعداد و قیمت") { onEdit(line.id) }
              .semantics { role = Role.Button },
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              "${money(line.price)} ؋",
              style = MaterialTheme.typography.labelSmall.tabular(),
              color = if (line.customPrice) c.primary else c.muted,
            )
            if (on) {
              Spacer(Modifier.width(4.dp))
              Icon(Icons.Filled.Edit, contentDescription = null, tint = c.primaryDark, modifier = Modifier.size(12.dp))
            }
          }
        }
        QtyStepper(line.quantity, line.name, onMinus = { onMinus(line.id) }, onPlus = { onPlus(line.id) }, target = target, unit = line.unit)
        Text(
          money(SalesEngine.lineTotal(line.price, line.quantity)),
          Modifier.width(72.dp),
          style = MaterialTheme.typography.bodyMedium.tabular(), fontWeight = FontWeight.Bold, color = c.text,
          textAlign = TextAlign.End, maxLines = 1,
        )
      }
      if (i < lines.size - 1 && !on && lines.getOrNull(i + 1)?.id != selected) {
        //  جداکنندهٔ مویی و تورفته — نه خطِ تمام‌عرضِ تیره
        Box(Modifier.fillMaxWidth().padding(start = 50.dp, end = 6.dp).height(1.dp).background(c.border))
      } else Spacer(Modifier.height(1.dp))
    }
  }
}

/**
 *  خلاصهٔ پرداخت روی کارتِ سفید: ردیف‌های `PosSummary.rows` (جمع و تخفیف)، جای
 *  کادرِ تخفیف برای کسی که مجوز دارد، و «مبلغِ قابلِ پرداخت» درشت.
 */
@Composable
fun PaymentSummary(
  rows: List<PosSummaryRow>,
  payable: Double,
  extra: @Composable () -> Unit = {},
) {
  val c = Shop.colors
  Column(Modifier.fillMaxWidth().softCard(c, ShopShape.card).padding(horizontal = 14.dp, vertical = 10.dp)) {
    rows.forEach { r ->
      Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(r.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = if (r.negative) c.danger else c.muted)
        Text(
          "${if (r.negative) "−" else ""}${money(r.amount)} ؋",
          style = MaterialTheme.typography.bodySmall.tabular(), color = if (r.negative) c.danger else c.text,
        )
      }
    }
    extra()
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text("مبلغِ قابلِ پرداخت", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = c.text)
      Text("${money(payable)} ؋", style = MaterialTheme.typography.headlineMedium.tabular(), fontWeight = FontWeight.Bold, color = c.text, maxLines = 1)
    }
  }
}

/**
 *  کادرِ عددیِ کوچک در یک ردیف («تخفیف ٪»، «دریافتیِ همین حالا ؋») — به‌جای
 *  کادرِ متریالِ ۵۶ پیکسلی. خطا: لبهٔ سرخ و «عدد درست نیست» (متن + رنگ).
 */
@Composable
fun MiniNumberField(label: String, value: String, onValue: (String) -> Unit, isError: Boolean, suffix: String) {
  val c = Shop.colors
  Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1)
    Row(
      Modifier
        .width(132.dp)
        .heightIn(min = 34.dp)
        .clip(ShopShape.field)
        .background(c.fieldBg)
        .border(1.dp, if (isError) c.danger else c.fieldBorder, ShopShape.field)
        .padding(horizontal = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.tabular().copy(color = c.text, textAlign = TextAlign.End),
        cursorBrush = SolidColor(c.primary),
        modifier = Modifier.weight(1f).semantics { contentDescription = label },
      )
      Spacer(Modifier.width(6.dp))
      Text(suffix, style = MaterialTheme.typography.labelMedium, color = c.muted)
    }
  }
  if (isError) Text("عدد درست نیست", Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall, color = c.danger, textAlign = TextAlign.End)
}

/** روشِ پرداخت — کنترلِ قطعه‌ای، فقط `PosPayments.configured()` (روش‌هایی که موتور ثبت می‌کند) */
@Composable
fun PaymentMethodGrid(
  methods: List<PosPayMethod>,
  selected: SalesEngine.Payment,
  target: Dp,
  onSelect: (PosPayMethod) -> Unit,
) {
  val c = Shop.colors
  Row(Modifier.fillMaxWidth().segmentTrack(c).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
    methods.forEach { m ->
      val on = m.payment == selected
      Box(
        Modifier
          .weight(1f)
          .heightIn(min = target)
          .then(if (on) Modifier.segmentSelected(c) else Modifier.clip(SegmentInnerShape))
          .selectable(selected = on, role = Role.RadioButton) { onSelect(m) },
        contentAlignment = Alignment.Center,
      ) {
        Text(
          m.label,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
          color = if (on) c.primaryDark else c.muted,
        )
      }
    }
  }
}

/** «ثبتِ فروش» (اصلی، بزرگ، تمام‌عرض) و «چاپِ آخر» (ثانویه، کوچک) — دکمهٔ ثبت در حینِ ثبت خاموش است (POS-14) */
@Composable
fun OrderActions(
  postHint: String,
  printHint: String,
  canPost: Boolean,
  posting: Boolean,
  canPrint: Boolean,
  onPost: () -> Unit,
  onPrint: () -> Unit,
) {
  Button(
    onClick = onPost,
    enabled = canPost && !posting,
    shape = ShopShape.button,
    //  دکمهٔ اصلی و حالتِ غیرفعالش از توکن‌های دکمه (VILL3N §۷)
    colors = ButtonDefaults.buttonColors(
      containerColor = Shop.colors.button.primary.bg,
      contentColor = Shop.colors.button.primary.text,
      disabledContainerColor = Shop.colors.button.disabled.bg,
      disabledContentColor = Shop.colors.button.disabled.text,
    ),
    modifier = Modifier.fillMaxWidth().height(54.dp),
  ) {
    Text(if (posting) "در حالِ ثبت…" else "ثبتِ فروش", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
    if (!posting) {
      Spacer(Modifier.width(10.dp))
      Text(postHint, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
  }
  Spacer(Modifier.height(4.dp))
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
    TextButton(onClick = onPrint, enabled = canPrint, contentPadding = PaddingValues(horizontal = 10.dp)) {
      val fg = if (canPrint) Shop.colors.primaryDark else Shop.colors.muted2
      Icon(Icons.Filled.Print, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
      Spacer(Modifier.width(6.dp))
      Text("چاپِ فاکتورِ آخر", style = MaterialTheme.typography.labelLarge, color = fg)
      Spacer(Modifier.width(6.dp))
      PosKeyHint(printHint)
    }
  }
}
