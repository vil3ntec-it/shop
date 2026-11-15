package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrA11y
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.pressed
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.InvoicePiece
import ir.vil3ntec.tohid.ui.v3.InvoiceRefText
import ir.vil3ntec.tohid.ui.v3.V3AmountStatus
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3RowStatus
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  ردیفِ یک تراکنشِ حساب — سادهٔ `claude/e-rows-63q` (خواستِ صاحب‌کار): بی قاب و بی زمینهٔ رنگیِ هر ردیف، درونِ یک
 *  کارتِ پیوسته با جداکنندهٔ مویی. خطِ اول «۴۰۰ ؋ • برد» (جهت به متنِ آرام: برد سرخِ بدهی، رسید سبزِ رسید، `V3AmountStatus`)
 *  و ماندهٔ پس از ردیف کوچک در پایان؛ خطِ دوم تاریخِ کوتاه (`RowWhen`) و یادداشت اگر هست — «فاکتور #۱۲» ِ یادداشت
 *  زدنی است (`InvoiceRef`). زدن ⇐ برگهٔ جزئیات؛ نگه‌داشتن ⇐ کارها؛ کشیدن به سمتِ آغاز ⇐ حذف (با برگرداندن).
 *
 *  کشیدن این‌جا `SwipeToDismissBox` ِ خودِ Compose است (برگرداندنِ `MoneyUndo` پس از رها کردن).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LedgerRow(
  /** مبلغ بی علامت («۴۰۰ ؋») — جهت را [direction] می‌گوید */
  amount: String,
  /** «برد» (`V3RowTone.DEBT`) یا «رسید» (`V3RowTone.RECEIPT`) */
  direction: V3RowStatus,
  /** برد ↗ / رسید ↙ */
  up: Boolean,
  /** تاریخِ کوتاه («امروز ۱۴:۰۵»، «دیروز»، «۲۸ میزان») */
  date: String,
  /** یادداشتِ ردیف، تکه‌تکه (`InvoiceRefs.split`)؛ خالی ⇒ فقط تاریخ */
  note: List<InvoicePiece>,
  /** «مانده ۲۵۰» یا برچسبِ «معکوس»/«معکوس‌شده» (S89) */
  after: String?,
  /** مشتریِ همین حساب — برای شمارهٔ فاکتورِ دوپهلو در یادداشت */
  debtorId: String,
  first: Boolean,
  last: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = PhoneV3.colors
  val shape = moneyGroupShape(first, last)
  val haptic = LocalHapticFeedback.current
  //  پُرشدنِ نرمِ هنگامِ فشار، مثلِ ردیف‌های کیت (`V3ListRow`) — کوچک شدنِ یک تکه از کارتِ پیوسته بی‌جاست
  val source = remember { MutableInteractionSource() }
  val down by source.collectIsPressedAsState()
  val bg by animateColorAsState(if (down) c.pressed else c.card, MotionSpec.tweenOf(120), label = "ledgerPress")
  @Suppress("DEPRECATION")
  val swipe = rememberSwipeToDismissBoxState(
    confirmValueChange = { value ->
      if (value == SwipeToDismissBoxValue.EndToStart) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onDelete()
        true
      } else false
    },
  )
  Column(modifier.clip(shape)) {
    if (!first) Box(Modifier.background(c.card)) { V3Divider(start = 16.dp) }
    SwipeToDismissBox(
      state = swipe,
      enableDismissFromStartToEnd = false,
      backgroundContent = {
        Box(Modifier.fillMaxSize().background(c.tile).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
          //  فقط نما: صفحه‌خوان «حذف» را کارِ سفارشیِ خودِ ردیف می‌شنود، نه نوشتهٔ جدای زیرِ آن
          Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = LedgerTone.OWE.ink(), modifier = Modifier.size(20.dp))
        }
      },
    ) {
      Row(
        Modifier
          .fillMaxWidth()
          .heightIn(min = 56.dp)
          .background(bg)
          .combinedClickable(
            interactionSource = source,
            indication = null,
            role = Role.Button,
            onClickLabel = StrMoney.details,
            onLongClickLabel = StrA11y.moreActions,
            onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() },
            onClick = onClick,
          )
          //  کشیدن برای حذف جایگزینِ صفحه‌خوان می‌خواهد (همان `onDelete` ِ کشیدن، با همان «برگرداندن»)
          .semantics { customActions = listOf(CustomAccessibilityAction(StrMoney.delete) { onDelete(); true }) }
          .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        //  همان پیکانِ برد ↗ / رسید ↙ (آیکن‌ها دست نمی‌خورند)؛ نامِ نوع را نوشتهٔ «• برد/رسید» می‌گوید
        Icon(if (up) Icons.Outlined.NorthEast else Icons.Outlined.SouthWest, contentDescription = null, tint = (if (up) LedgerTone.OWE else LedgerTone.SETTLED).ink(), modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
          V3AmountStatus(amount, direction, align = TextAlign.Start)
          InvoiceRefText(
            listOf(InvoicePiece(date)) + if (note.isEmpty()) emptyList() else listOf(InvoicePiece(" · ")) + note,
            style = V3Type.footnote,
            color = c.inkMuted,
            debtorId = debtorId,
            maxLines = 2,
          )
        }
        if (after != null) {
          Spacer(Modifier.width(8.dp))
          Text(after, style = V3Type.footnote, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}
