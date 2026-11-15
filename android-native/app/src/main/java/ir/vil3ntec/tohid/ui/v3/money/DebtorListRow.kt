package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrRows
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.DebtRows
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.DebtorListModel
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.RowWhen
import ir.vil3ntec.tohid.ui.v3.V3AmountStatus
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3RowStatus
import ir.vil3ntec.tohid.ui.v3.V3RowTone
import ir.vil3ntec.tohid.ui.v3.V3Sheet

/*
 *  ردیفِ قرض‌داران (`debtors`) — سادهٔ `claude/e-rows-63q` به همان قاعدهٔ ۶۳d («سادگی: یک کارِ اصلی در هر ردیف»):
 *  نام؛ «آخرین حرکت · شماره» (کوچک)؛ و در پایان «۳۰۰ ؋ • قرض‌دار» به متنِ آرام (بدهی سرخ، سررسیدِ گذشته کهربایی،
 *  موجودی و تسویه آرام). زدن ⇐ حساب؛ نگه‌داشتن ⇐ برگهٔ کارها (باز کردن، ویرایش، حذف — همان سه کارِ کارتِ پیشین).
 *  قرصِ «ثبت» روی ردیف نیست: «ثبتِ تراکنش» نوارِ چسبانِ همان حساب است (یک زدن دورتر) و قرصِ تکراری روی هر ردیف شلوغی است.
 */

/** متن‌های آمادهٔ یک ردیف — یک بار در `remember` ساخته می‌شود، نه در هر کشیدن */
internal data class DebtorRowText(
  val id: String,
  val title: String,
  /** «دیروز ۱۴:۰۵ · ‎0700 000 001» — آخرین حرکتِ حساب و شماره (پوشیده بی مجوز)؛ هیچ ⇒ null */
  val subtitle: String?,
  /** مانده بی علامت؛ تسویه ⇒ null (فقط «تسویه») */
  val amount: String?,
  val status: V3RowStatus,
)

/** منطقِ خالصِ ردیفِ قرض‌دار — بی Compose، زیرِ آزمون (`DebtorRowsUiTest`، `DebtorRowModelTest`) */
internal object DebtorRowModel {

  private const val EPSILON = 0.001

  /** ترتیبِ زمانِ دفتر — همان `DebtBook` (روز، سپس لحظهٔ ثبت) */
  private val ORDER = compareBy<DebtTransaction> { it.date }.thenBy { it.createdAt }

  /**
   *  @param today امروزِ دکان (`todayIso()`) برای «امروز/دیروز» و سررسید
   *  @param offsetMs فاصلهٔ منطقهٔ زمانی در یک لحظه (`SaleTime.zoneOffset`) برای ساعتِ آخرین حرکت
   *  @param contacts CRM-15 — بی مجوزِ `customers.contact` شماره پوشیده است
   */
  fun of(d: ShopData, row: DebtorListModel.Row, today: String, offsetMs: (Long) -> Int, contacts: Boolean): DebtorRowText {
    val own = DebtRows.of(d)[row.debtor.id].orEmpty()
    val last = own.maxWithOrNull(ORDER)
    val whenText = last?.let { RowWhen.dayAt(it.date, it.createdAt, today, offsetMs(it.createdAt)) }?.takeIf { it.isNotBlank() }
    val phone = ir.vil3ntec.tohid.ui.components.customerPhone(row.debtor.phone, contacts).trim().takeIf { it.isNotEmpty() }?.let { "⁦$it⁩" }
    return DebtorRowText(
      id = row.debtor.id,
      title = row.debtor.name.ifBlank { StrMoney.noName },
      subtitle = listOfNotNull(whenText, phone).joinToString(" · ").ifBlank { null },
      amount = if (kotlin.math.abs(row.balance) < EPSILON) null else MoneyText.afn(kotlin.math.abs(row.balance)),
      status = status(row.balance, overdue(d, row.debtor.id, row.balance, today, own)),
    )
  }

  /** بدهی سرخ، بدهیِ سررسیدگذشته کهربایی، موجودی آرام، صاف «تسویه» ِ آرام */
  fun status(balance: Double, overdue: Boolean): V3RowStatus = when {
    balance > EPSILON && overdue -> V3RowStatus(StrRows.overdue, V3RowTone.ATTENTION)
    balance > EPSILON -> V3RowStatus(StrRows.owes, V3RowTone.DEBT)
    balance < -EPSILON -> V3RowStatus(StrRows.inCredit)
    else -> V3RowStatus(StrMoney.settled)
  }

  /**
   *  بردی با سررسیدِ گذشته که هنوز باز است — همان «عقب‌افتاده» ِ کشوی حساب (`DebtBook.overdue`). دفترِ کامل فقط برای
   *  کسی ساخته می‌شود که بدهکار است و دست‌کم یک برد با سررسیدِ پیش از امروز دارد (بیشترِ دفترها سررسید ندارند).
   */
  fun overdue(d: ShopData, debtorId: String, balance: Double, today: String, own: List<DebtTransaction> = DebtRows.of(d)[debtorId].orEmpty()): Boolean {
    if (balance <= EPSILON || today.isBlank()) return false
    if (own.none { it.type == "give" && it.dueDate?.trim().orEmpty().let { due -> due.isNotEmpty() && due < today } }) return false
    return DebtBook.book(d, debtorId, today)?.overdue?.isNotEmpty() == true
  }
}

/** گوشه‌های گروهِ پیوسته: فقط سرِ اول و آخر گرد (همان کارتِ پیوستهٔ رسیدها و تاریخچه) */
internal fun moneyGroupShape(first: Boolean, last: Boolean): RoundedCornerShape {
  val r = PhoneV3.groupRadius
  return RoundedCornerShape(
    topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp,
    bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp,
  )
}

/** یک ردیف درونِ کارتِ پیوستهٔ قرض‌داران — بی قاب و بی قرص؛ گروه با فاصله و جداکنندهٔ مویی */
@Composable
internal fun DebtorListRow(text: DebtorRowText, first: Boolean, last: Boolean, onOpen: () -> Unit, onMenu: () -> Unit, modifier: Modifier = Modifier) {
  Column(modifier.clip(moneyGroupShape(first, last)).background(PhoneV3.colors.card)) {
    if (!first) V3Divider(start = 22.dp)
    V3ListRow(
      title = text.title,
      subtitle = text.subtitle,
      subtitleLines = 1,
      tile = false,
      trailing = { V3AmountStatus(text.amount, text.status, Modifier.widthIn(max = 168.dp)) },
      chevron = false,
      onLongClick = onMenu,
      onClick = onOpen,
    )
  }
}

/** نگه‌داشتنِ ردیف ⇐ همان سه کارِ کارتِ پیشین (باز کردن، ویرایش، حذف) */
@Composable
internal fun DebtorRowMenu(title: String, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
  V3Sheet(onDismiss = onDismiss, title = title, detents = false) { closer ->
    V3Card(padding = PaddingValues(vertical = 4.dp)) {
      V3ListRow(StrMoney.openAccount, icon = Icons.AutoMirrored.Outlined.ReceiptLong, onClick = { closer.close(onOpen) })
      V3Divider(start = 64.dp)
      V3ListRow(StrMoney.edit, icon = Icons.Outlined.Edit, onClick = { closer.close(onEdit) })
      V3Divider(start = 64.dp)
      V3ListRow(StrMoney.delete, icon = Icons.Outlined.DeleteOutline, danger = true, chevron = false, onClick = { closer.close(onDelete) })
    }
  }
}
