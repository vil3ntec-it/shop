package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.screens.plusDays
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Space
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/*
 *  تکه‌های برگهٔ کوچک‌شدهٔ «ثبتِ تراکنش» (صاحب‌کار ۱۴۰۵/۰۸/۲۳، `TxFormSheet`): قوطیِ «برد/رسید» کنارِ مبلغ،
 *  تاریخِ نوشته‌ایِ کوچکِ سربرگ و تراشه‌های سررسید. هیچ‌کدام چیزِ ذخیره‌شده را عوض نمی‌کنند — همان `TxForm`.
 */

/** نشانِ آزمون: تاریخِ سربرگ و قوطیِ نوع */
internal const val TX_DATE_TAG = "txDate"
internal const val TX_KIND_TAG = "txKind"

/** نوعِ دیگر — برد ⇄ رسید */
internal fun DebtorEngine.Kind.flipped(): DebtorEngine.Kind =
  if (this == DebtorEngine.Kind.GIVE) DebtorEngine.Kind.RECEIVE else DebtorEngine.Kind.GIVE

/**
 *  قوطیِ کوچکِ نوع کنارِ کادرِ مبلغ: همان که نشان می‌دهد ثبت می‌شود؛ زدن ⇒ نوعِ دیگر. رنگ همان رنگِ معناییِ
 *  همین صفحه (`LedgerTone`): برد رنگِ بدهی، رسید رنگِ رسید — و نوشته همیشه هست، رنگ تنها نیست.
 *  بالایش جای برچسبِ کادر خالی است تا قوطی با خودِ کادر هم‌تراز بماند (با درشتیِ نوشته هم).
 */
@Composable
internal fun TxKindToggle(kind: DebtorEngine.Kind, onToggle: () -> Unit, modifier: Modifier = Modifier) {
  val tone = if (kind == DebtorEngine.Kind.GIVE) LedgerTone.OWE else LedgerTone.SETTLED
  val label = if (kind == DebtorEngine.Kind.GIVE) StrMoney.giveTag else StrMoney.receiveTag
  val ink by animateColorAsState(tone.ink(), MotionSpec.tweenOf(MotionTokens.SHORT), label = "txKindInk")
  val wash by animateColorAsState(tone.wash(), MotionSpec.tweenOf(MotionTokens.SHORT), label = "txKindWash")
  val shape = RoundedCornerShape(V3Space.controlRadius)
  Column(modifier) {
    //  جای برچسبِ کادر (همان سبک) — فقط فاصله، صفحه‌خوان آن را نمی‌خواند
    Text(" ", style = v3Text(13, FontWeight.SemiBold, 18), modifier = Modifier.padding(bottom = 6.dp).clearAndSetSemantics { })
    Row(
      Modifier
        .heightIn(min = V3Space.control)
        .widthIn(min = 76.dp)
        .v3Pressable(label = StrMoney.txSwitchKind, haptic = true, onClick = onToggle)
        .clip(shape)
        .background(wash)
        .border(1.dp, ink.copy(alpha = 0.45f), shape)
        .semantics { stateDescription = label }
        .testTag(TX_KIND_TAG)
        .padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(Icons.Outlined.SwapHoriz, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
      Spacer(Modifier.width(5.dp))
      Text(label, style = v3Text(15, FontWeight.Bold, 20), color = ink, maxLines = 1)
    }
  }
}

/**
 *  تاریخِ تراکنش در سربرگ، روبه‌روی عنوان: نوشتهٔ سادهٔ کوچک (نه کادر و خانه) به تقویمِ برنامه (`MoneyText.date`،
 *  خورشیدی در فارسی). زدن ⇒ کادرِ تاریخِ همیشگی (`V3DateField`) زیرِ سربرگ باز/بسته می‌شود. نوشتهٔ ناخوانا همان‌طور
 *  که هست و به رنگِ خطا — همان را موتور هنگامِ ثبت رد می‌کند (`DebtorEngine.addTransaction`).
 */
@Composable
internal fun TxDateLink(iso: String, open: Boolean, onClick: () -> Unit) {
  val c = PhoneV3.colors
  val valid = DebtBook.isIsoDate(iso)
  val shown = if (valid) MoneyText.date(iso) else iso.ifBlank { "—" }
  Text(
    shown,
    style = v3Text(13, if (open) FontWeight.SemiBold else FontWeight.Medium, 18),
    color = if (valid) PhoneV3Roles.brandText(c) else PhoneV3Roles.errorText(c),
    maxLines = 1,
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .v3Pressable(label = StrMoney.txChangeDate, onClick = onClick)
      .semantics { stateDescription = "${StrMoney.date}: $shown" }
      .testTag(TX_DATE_TAG)
      .padding(horizontal = 6.dp, vertical = 6.dp),
  )
}

/**
 *  سررسیدِ برد فقط با تراشه: «یک هفته · دو هفته · بی سررسید» (`DebtorTxModel.TX_DUE_PRESETS`؛ «یک ماه» را صاحب‌کار
 *  خط زد). مقدار همان `yyyy-MM-dd` ِ امروز + روزها؛ خالی یعنی سررسید ندارد.
 */
@Composable
internal fun TxDueChips(value: String, onValueChange: (String) -> Unit) {
  val c = PhoneV3.colors
  val today = todayIso()
  V3ChipRow(contentPadding = PaddingValues(0.dp)) {
    Text(StrMoney.dueTitle, style = v3Text(13, FontWeight.SemiBold, 18), color = c.inkMuted, maxLines = 1)
    DebtorTxModel.TX_DUE_PRESETS.zip(listOf(StrMoney.dueWeek, StrMoney.dueTwoWeeks)).forEach { (days, label) ->
      val iso = plusDays(today, days)
      V3Chip(label, selected = value == iso, onClick = { onValueChange(iso) })
    }
    V3Chip(StrMoney.dueNone, selected = value.isBlank(), onClick = { onValueChange("") })
  }
}
