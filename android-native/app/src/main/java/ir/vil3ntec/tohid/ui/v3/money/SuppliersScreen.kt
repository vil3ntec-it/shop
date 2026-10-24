package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Debt
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Fab
import ir.vil3ntec.tohid.ui.v3.V3HeroCard
import ir.vil3ntec.tohid.ui.v3.V3HeroChip
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3SwipeAction
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3AnimateItem
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules

/** کارهای فهرستِ تأمین‌کننده‌ها — هر کدام یک مسیر، جز حذف که موتور همین‌جا رد یا قبول می‌کند */
internal class SuppliersActions(
  val onBack: (() -> Unit)?,
  val onOpen: (String) -> Unit,
  val onPay: (String) -> Unit,
  val onEdit: (String) -> Unit,
  /** `LedgerEngine.deleteSupplier` — تأمین‌کننده با خرید/پرداخت حذف نمی‌شود (پیامِ موتور) */
  val onDelete: (String) -> Unit,
  val onNewSupplier: () -> Unit,
  val onNewPurchase: () -> Unit,
)

/**
 *  خرید و تأمین‌کننده (`purchasing`): کادرِ بزرگِ «بدهیِ ما به تأمین‌کننده‌ها» (شمارنده)، و هر
 *  تأمین‌کننده یک ردیف — آواتارِ حرفِ اول، نام، شماره، و بدهی با برچسبِ وضعیت (بدهکاریم سرخ،
 *  پیش‌پرداخت آبی، صاف سبز). کشیدن: «پرداخت» از آغاز، «ویرایش/حذف» از پایان. «ثبت خرید» شناور.
 */
@Composable
internal fun SuppliersScreen(d: ShopData, actions: SuppliersActions) {
  val rows = remember(d) { SuppliersModel.rows(d) }
  val owed = rememberCountUp(remember(d) { SuppliersModel.owedTotal(d) })
  val stagger = rememberV3Stagger()
  var confirmDelete by remember { mutableStateOf<String?>(null) }

  V3Scaffold(
    title = Str.purchasing,
    onBack = actions.onBack,
    fab = { expanded -> V3Fab(Icons.Outlined.AddShoppingCart, StrMoney.newPurchase, expanded, actions.onNewPurchase) },
  ) { pad ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      LazyColumn(
        Modifier.widthIn(max = 720.dp).fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = pad.calculateBottomPadding() + 72.dp),
      ) {
        item(key = "hero") {
          V3HeroCard(
            title = StrMoney.supplierDebtTotal,
            value = MoneyText.afn(owed),
            caption = StrMoney.suppliersCaption(d.suppliers.size, d.purchases.size),
            chips = listOf(V3HeroChip(StrMoney.newSupplier, Icons.Outlined.PersonAddAlt, actions.onNewSupplier)),
          )
          Spacer(Modifier.height(16.dp))
        }
        if (rows.isEmpty()) {
          item(key = "empty") {
            V3Card {
              V3EmptyState(Icons.Outlined.LocalShipping, StrMoney.emptySuppliers, body = StrMoney.emptySuppliersBody, action = StrMoney.newSupplier, onAction = actions.onNewSupplier)
            }
          }
        } else {
          itemsIndexed(rows, key = { _, r -> r.supplier.id }) { i, r ->
            SupplierRow(
              row = r,
              first = i == 0,
              last = i == rows.lastIndex,
              actions = actions,
              onAskDelete = { confirmDelete = r.supplier.id },
              modifier = Modifier.v3AnimateItem(this).v3Stagger(stagger, r.supplier.id, i),
            )
          }
        }
      }
    }
  }

  confirmDelete?.let { id ->
    val name = d.suppliers.find { it.id == id }?.name.orEmpty()
    V3ConfirmSheet(
      title = StrMoney.deleteSupplierTitle,
      message = StrMoney.deleteSupplierBody(name),
      confirm = StrMoney.delete,
      icon = Icons.Outlined.DeleteOutline,
      onConfirm = { actions.onDelete(id) },
      onDismiss = { confirmDelete = null },
    )
  }
}

@Composable
private fun SupplierRow(
  row: SuppliersModel.Row,
  first: Boolean,
  last: Boolean,
  actions: SuppliersActions,
  onAskDelete: () -> Unit,
  modifier: Modifier,
) {
  val c = PhoneV3.colors
  val r = PhoneV3.groupRadius
  val shape = RoundedCornerShape(topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp, bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp)
  val s = row.supplier
  Column(modifier.clip(shape).background(c.card)) {
    V3ListRow(
      title = s.name,
      subtitle = s.phone.takeIf { it.isNotBlank() }?.let { ir.vil3ntec.tohid.LocaleText.phoneDisplay(it) },
      leading = { LetterAvatar(s.name, V3Modules.hueOf("purchasing")) },
      trailing = { DebtLabel(row.debt, row.state) },
      startActions = listOf(V3SwipeAction(Icons.Outlined.Payments, StrMoney.pay, V3ButtonKind.PRIMARY) { actions.onPay(s.id) }),
      endActions = listOf(
        V3SwipeAction(Icons.Outlined.Edit, StrMoney.edit) { actions.onEdit(s.id) },
        V3SwipeAction(Icons.Outlined.DeleteOutline, StrMoney.delete, V3ButtonKind.DANGER, onAskDelete),
      ),
      onClick = { actions.onOpen(s.id) },
    )
    if (!last) V3Divider(start = 64.dp)
  }
}

/** مبلغ و برچسبِ وضعیتِ حسابِ تأمین‌کننده — رنگ هرگز تنها نیست */
@Composable
internal fun DebtLabel(debt: Double, state: SuppliersModel.State) {
  val tint = supplierTint(state)
  Column(horizontalAlignment = Alignment.End) {
    Text("${money(kotlin.math.abs(debt))} ؋", style = v3Text(15, FontWeight.Bold, 20), color = tint)
    Text(
      when (state) {
        SuppliersModel.State.OWE -> StrMoney.weOwe
        SuppliersModel.State.PREPAID -> StrMoney.prepaidToSupplier
        SuppliersModel.State.SETTLED -> StrMoney.accountSettled
      },
      style = v3Text(11, FontWeight.Medium, 15),
      color = PhoneV3.colors.inkMuted,
    )
  }
}

@Composable
internal fun supplierTint(state: SuppliersModel.State): Color = when (state) {
  SuppliersModel.State.OWE -> PhoneV3Debt.colors.owe
  SuppliersModel.State.PREPAID -> PhoneV3Debt.colors.info
  SuppliersModel.State.SETTLED -> PhoneV3Debt.colors.settled
}

/** آواتارِ حرفِ اول روی زمینهٔ نرمِ رنگِ همراهِ بخش (پیشنهادِ کیت: `Draft`) */
@Composable
internal fun LetterAvatar(name: String, hue: V3Hue?) {
  val c = PhoneV3.colors
  val tint = hue?.let { PhoneV3Palette.glyph(c, it) } ?: c.icon
  val soft = hue?.let { PhoneV3Palette.soft(c, it) } ?: c.tile
  Box(Modifier.size(40.dp).clip(CircleShape).background(soft), contentAlignment = Alignment.Center) {
    Text(name.trim().take(1).ifBlank { "؟" }, style = v3Text(17, FontWeight.Bold, 22), color = tint)
  }
}
