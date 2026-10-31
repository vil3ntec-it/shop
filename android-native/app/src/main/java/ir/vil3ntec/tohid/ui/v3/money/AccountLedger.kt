package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Debt
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/*
 *  «حساب» (money.md · AccountLedger): تکه‌های مشترکِ حسابِ قرض‌دار و حسابِ تأمین‌کننده — یک بار
 *  ساخته، دو جا به کار. شکل همان حسابِ قرض‌دارِ تأییدشده (#354): کارتِ سه‌عددی با شمارشِ نرم،
 *  و نوارِ چسبانِ پایین با کارِ اصلی و دو قرصِ تند. رنگِ بدهی/رسید معنایی است (THEME-V2، بی تم).
 */

/** رنگِ معناییِ یک عدد یا ردیفِ حساب */
internal enum class LedgerTone { NEUTRAL, OWE, SETTLED }

@Composable
internal fun LedgerTone.ink(): Color = when (this) {
  LedgerTone.NEUTRAL -> PhoneV3.colors.inkMuted
  LedgerTone.OWE -> PhoneV3Debt.colors.owe
  LedgerTone.SETTLED -> PhoneV3Debt.colors.settled
}

@Composable
internal fun LedgerTone.wash(): Color = when (this) {
  LedgerTone.NEUTRAL -> PhoneV3.colors.tile
  LedgerTone.OWE -> PhoneV3Debt.colors.oweTint
  LedgerTone.SETTLED -> PhoneV3Debt.colors.settledTint
}

/** یک خانهٔ کارتِ سه‌عددی; `text` به‌جای عدد (مثلاً «تسویه» وقتی صفر است) */
internal data class LedgerTotal(val label: String, val value: Double, val tone: LedgerTone, val text: String? = null)

/** «کل برد · کل رسید · الباقی» — سه ستونِ هم‌وزن در یک کارت، عددها با شمارشِ نرم */
@Composable
internal fun LedgerTotals(cells: List<LedgerTotal>, modifier: Modifier = Modifier) {
  V3Card(modifier, padding = PaddingValues(vertical = 9.dp)) {
    Row(Modifier.fillMaxWidth()) {
      cells.forEach { cell -> TotalCell(cell, Modifier.weight(1f)) }
    }
  }
}

@Composable
private fun TotalCell(cell: LedgerTotal, modifier: Modifier) {
  val c = PhoneV3.colors
  val shown = rememberCountUp(cell.value)
  val final = cell.text ?: money(cell.value)
  Column(
    modifier.semantics(mergeDescendants = true) { contentDescription = "${cell.label}: $final" },
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(cell.label, style = v3Text(11, FontWeight.Medium, 15), color = c.inkMuted, maxLines = 1)
    Spacer(Modifier.height(1.dp))
    Text(
      cell.text ?: money(shown),
      style = v3Text(17, FontWeight.Bold, 23),
      color = cell.tone.ink(),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/** یک قرصِ تندِ نوارِ پایین («برد»، «رسید») */
internal data class LedgerQuick(val label: String, val tone: LedgerTone, val onClick: () -> Unit)

/**
 *  نوارِ چسبانِ پایینِ حساب: دایرهٔ «+» با عنوان و راهنما (زدنِ نوار ⇐ کارِ اصلی) و تا دو قرصِ
 *  تند که همان کار را با نوعِ انتخاب‌شده باز می‌کنند. در جای `bottomBar` ِ `V3Scaffold` می‌نشیند.
 */
@Composable
internal fun LedgerActionBar(
  title: String,
  hint: String,
  onMain: () -> Unit,
  quick: List<LedgerQuick>,
  modifier: Modifier = Modifier,
  icon: ImageVector = Icons.Outlined.Add,
) {
  val c = PhoneV3.colors
  val primary = PhoneV3Roles.button(c, V3ButtonKind.PRIMARY)
  val shape = RoundedCornerShape(22.dp)
  Row(
    modifier
      .padding(horizontal = 16.dp, vertical = 12.dp)
      .fillMaxWidth()
      .v3Pressable(label = title, haptic = true, onClick = onMain)
      .shadow(10.dp, shape, clip = false, ambientColor = c.ink.copy(alpha = 0.10f), spotColor = c.ink.copy(alpha = 0.10f))
      .clip(shape)
      .background(c.card)
      .border(1.dp, c.hairline, shape)
      .padding(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(primary.bg), contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = null, tint = primary.fg, modifier = Modifier.size(24.dp))
    }
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = v3Text(15, FontWeight.Bold, 20), color = c.ink, maxLines = 1)
      Text(hint, style = v3Text(11, FontWeight.Normal, 15), color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    quick.forEachIndexed { i, q ->
      if (i > 0) Spacer(Modifier.width(6.dp))
      QuickPill(q)
    }
  }
}

@Composable
private fun QuickPill(q: LedgerQuick) {
  val ink = q.tone.ink()
  Row(
    Modifier
      .v3Pressable(label = q.label, haptic = true, onClick = q.onClick)
      .clip(RoundedCornerShape(50))
      .background(q.tone.wash())
      .padding(horizontal = 12.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(ink))
    Spacer(Modifier.width(6.dp))
    Text(q.label, style = v3Text(13, FontWeight.Bold, 18), color = ink)
  }
}
