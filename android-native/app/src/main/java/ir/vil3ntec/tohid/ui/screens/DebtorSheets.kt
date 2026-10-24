package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Debt

/*
 *  برگه‌های پایینِ قرض‌داران (V3): ثبتِ تراکنش، افزودن/ویرایشِ قرض‌دار، و پرسشِ
 *  حذف. هر سه روی یک برگهٔ مشترک (`DebtSheet`) با رنگ‌های VILL3N؛ کادرهای متن و
 *  دکمه‌های متنیِ مشترکِ برنامه (`AmountField`، `DateField`، `DueDateField`) زیرِ
 *  `V3MaterialColors` همان رنگ‌ها را می‌گیرند — بنفشِ قدیم هیچ‌جا دیده نمی‌شود.
 */

/** رنگ‌های متریالِ زیرِ برگه از توکن‌های V3 — کادرِ متن، دکمهٔ متنی، نشانگر */
@Composable
internal fun V3MaterialColors(content: @Composable () -> Unit) {
  val c = PhoneV3.colors
  val dark = c.isDark
  val primary = if (dark) c.cart else c.accentDeep
  val scheme = MaterialTheme.colorScheme.copy(
    primary = primary,
    onPrimary = if (dark) c.onCart else c.onAccent,
    primaryContainer = c.tile,
    surface = c.card,
    onSurface = c.ink,
    surfaceVariant = c.tile,
    onSurfaceVariant = c.inkMuted,
    background = c.bg,
    onBackground = c.ink,
    outline = PhoneV3Debt.colors.fieldEdge,
    outlineVariant = c.hairline,
    error = c.negative,
    surfaceContainerHighest = c.tile,
  )
  MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
}

/**
 *  برگهٔ پایینِ V3: از پایین با فنر بالا می‌آید، دستگیرهٔ کوچک، گوشهٔ ۲۸، سطحِ
 *  کارت. محتوا اسکرول می‌شود و دکمهٔ اصلی (`footer`) همیشه پیداست.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebtSheet(
  onDismiss: () -> Unit,
  footer: (@Composable ColumnScope.() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val c = PhoneV3.colors
  val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = state,
    containerColor = c.card,
    contentColor = c.ink,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    dragHandle = {
      Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 40.dp, height = 5.dp).clip(CircleShape).background(c.hairline))
    },
  ) {
    V3MaterialColors {
      Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
        Column(
          Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
          content = content,
        )
        if (footer != null) {
          Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp), content = footer)
        }
      }
    }
  }
}

@Composable
internal fun SheetTitle(text: String, subtitle: String? = null) {
  val c = PhoneV3.colors
  Text(text, style = v3Text(20, FontWeight.Bold, 28), color = c.ink)
  if (subtitle != null) {
    Spacer(Modifier.height(2.dp))
    Text(subtitle, style = v3Text(14, FontWeight.Normal, 20), color = c.inkMuted)
  }
  Spacer(Modifier.height(16.dp))
}

/** دکمهٔ اصلیِ تمام‌عرض (VILL3N: برند، یا قرمزِ «خطر») */
@Composable
internal fun V3PrimaryButton(text: String, modifier: Modifier = Modifier, danger: Boolean = false, onClick: () -> Unit) {
  val c = PhoneV3.colors
  val dc = PhoneV3Debt.colors
  //  خطر: قرمزِ وضعیت (شب قرمزِ روشن با متنِ تیره)؛ وگرنه برندِ «فعال»
  val fill = if (danger) dc.owe else dc.accentActive
  val ink = if (danger) (if (c.isDark) c.black else c.onAccent) else dc.onAccent
  Box(
    modifier
      .fillMaxWidth()
      .height(52.dp)
      .v3PressScale(onClick, text)
      .clip(RoundedCornerShape(16.dp))
      .background(fill),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, style = v3Text(16, FontWeight.Bold, 22), color = ink)
  }
}

/** دکمهٔ دوم (انصراف): سطحِ درونی با مرز */
@Composable
internal fun V3SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(16.dp)
  Box(
    modifier
      .fillMaxWidth()
      .height(52.dp)
      .v3PressScale(onClick, text)
      .clip(shape)
      .background(c.tile)
      .border(1.dp, c.hairline, shape),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, style = v3Text(16, FontWeight.SemiBold, 22), color = c.ink)
  }
}

/* ------------------------------ ثبتِ تراکنش ------------------------------ */

/**
 *  برگهٔ «ثبتِ تراکنش» — همان کارِ کادرِ قدیمی (برد/رسید، مبلغ، «تسویهٔ کامل»، تاریخ،
 *  یادداشت، سررسیدِ برد)، با همان `onSave`؛ قاعده‌ها (سقفِ اعتبار، …) بیرون می‌مانند.
 */
@Composable
internal fun TxComposerSheet(
  debtor: Debtor?,
  balance: Double,
  kind: DebtorEngine.Kind,
  onDismiss: () -> Unit,
  onSave: (DebtorEngine.Kind, Double, String, String, String?) -> Unit,
) {
  val c = PhoneV3.colors
  val dc = PhoneV3Debt.colors
  var amount by remember { mutableStateOf("") }
  var date by remember { mutableStateOf(todayIso()) }
  var notes by remember { mutableStateOf("") }
  var due by remember { mutableStateOf("") }
  var chosen by remember { mutableStateOf(kind) }
  val receiving = chosen == DebtorEngine.Kind.RECEIVE

  DebtSheet(
    onDismiss = onDismiss,
    footer = {
      V3PrimaryButton(if (receiving) StrDebtor.newReceive else StrDebtor.newGive) {
        onSave(chosen, amount.toDoubleOrNull() ?: 0.0, date, notes, due.ifBlank { null }.takeIf { !receiving })
      }
    },
  ) {
    SheetTitle(StrDebtor.newTx, debtor?.let { "${it.name} — ${DebtorEngine.stateText(balance)}" })

    //  برد/رسید: دو قرص در یک قاب — انتخاب‌شده رنگِ وضعیتِ خودش و نقطه
    Row(
      Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.tile).border(1.dp, c.hairline, RoundedCornerShape(16.dp)).padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      KindSegment(StrDebtor.giveKind, !receiving, dc.owe, dc.oweTint, Modifier.weight(1f)) { chosen = DebtorEngine.Kind.GIVE }
      KindSegment(StrDebtor.receiveKind, receiving, dc.settled, dc.settledTint, Modifier.weight(1f)) { chosen = DebtorEngine.Kind.RECEIVE }
    }
    Spacer(Modifier.height(16.dp))

    AmountField(amount, { amount = it }, StrDebtor.amount)
    // میان‌بُرِ «تسویهٔ کامل» فقط وقتی معنی دارد که بدهکار باشد
    if (receiving && balance > 0) {
      Spacer(Modifier.height(8.dp))
      Box(
        Modifier
          .v3PressScale({ amount = Math.round(balance).toString() }, StrDebtor.settleAll)
          .clip(CircleShape)
          .background(dc.settledTint)
          .padding(horizontal = 14.dp, vertical = 8.dp),
      ) {
        Text(StrDebtor.settleAll, style = v3Text(13, FontWeight.SemiBold, 18), color = dc.settled)
      }
    }
    Spacer(Modifier.height(12.dp))
    DateField(date) { date = it }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(notes, { notes = it }, label = { Text(StrDebtor.notesOptional) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
    //  سررسید فقط برای برد (اختیاری) — رسید سررسید ندارد
    if (!receiving) {
      Spacer(Modifier.height(12.dp))
      DueDateField(due) { due = it }
    }
    Spacer(Modifier.height(4.dp))
  }
}

@Composable
private fun KindSegment(label: String, selected: Boolean, tint: Color, wash: Color, modifier: Modifier, onClick: () -> Unit) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(12.dp)
  Row(
    modifier
      .v3PressScale(onClick, label)
      .clip(shape)
      .background(if (selected) wash else Color.Transparent)
      .then(if (selected) Modifier.border(1.dp, tint, shape) else Modifier)
      .padding(vertical = 12.dp, horizontal = 8.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(if (selected) tint else c.chevron))
    Spacer(Modifier.width(6.dp))
    Text(
      label,
      style = v3Text(13, if (selected) FontWeight.Bold else FontWeight.Medium, 18),
      color = if (selected) tint else c.inkMuted,
      textAlign = TextAlign.Center,
      maxLines = 1,
    )
  }
}

/* ------------------------------ افزودن / ویرایش ------------------------------ */

@Composable
internal fun DebtorFormSheet(
  state: DebtorFormState,
  onDismiss: () -> Unit,
  onSave: (DebtorEngine.DebtorDraft) -> Unit,
) {
  var form by remember(state.editingId) { mutableStateOf(state) }
  val shape = RoundedCornerShape(14.dp)
  DebtSheet(
    onDismiss = onDismiss,
    footer = {
      V3PrimaryButton(StrDebtor.save) {
        onSave(DebtorEngine.DebtorDraft(form.name, form.phone, form.notes, parseLimit(form.creditLimit)))
      }
    },
  ) {
    SheetTitle(if (form.editingId == null) StrDebtor.newDebtor else StrDebtor.editDebtor)
    OutlinedTextField(form.name, { form = form.copy(name = it) }, label = { Text(StrDebtor.name) },
      singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(form.phone, { form = form.copy(phone = it) }, label = { Text(StrDebtor.phoneOptional) },
      singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(form.notes, { form = form.copy(notes = it) }, label = { Text(StrDebtor.notesOptional) },
      shape = shape, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    CreditLimitField(form.creditLimit) { form = form.copy(creditLimit = it) }
    Spacer(Modifier.height(4.dp))
  }
}

/* ------------------------------ حذف ------------------------------ */

/** پرسشِ حذفِ قرض‌دار — متنِ هشدار همان `DebtorEngine.deleteWarning` (مبلغ و شمارِ تراکنش‌ها) */
@Composable
internal fun DeleteDebtorSheet(warning: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
  DebtSheet(
    onDismiss = onDismiss,
    footer = {
      V3PrimaryButton(StrDebtor.delete, danger = true, onClick = onConfirm)
      Spacer(Modifier.height(8.dp))
      V3SecondaryButton(StrDebtor.cancel, onClick = onDismiss)
    },
  ) {
    SheetTitle(StrDebtor.deleteDebtorTitle)
    Text(warning, style = v3Text(15, FontWeight.Normal, 23), color = PhoneV3.colors.inkMuted)
    Spacer(Modifier.height(4.dp))
  }
}
