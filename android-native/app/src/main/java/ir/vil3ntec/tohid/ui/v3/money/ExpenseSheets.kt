package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button

/**
 *  برگهٔ ماه (`expenses/month`): ماه‌هایی که مصرف دارند و ماهِ جاری، تازه‌ترین اول — همان کشوییِ
 *  امروز، که با گذشتِ ماه‌ها از صفحه بیرون نمی‌زند. انتخاب ⇐ برگه بسته و ماه به صفحه برمی‌گردد.
 */
@Composable
internal fun ExpenseMonthSheet(d: ShopData, current: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
  val months = remember(d) { ExpensesModel.months(d, todayIso()) }
  IdPickerSheet(
    title = StrMoney.pickMonth,
    options = months.map { PickOption(it, MoneyText.month(it)) },
    selected = current,
    onPick = { m -> if (m != null) onPick(m) },
    onDismiss = onDismiss,
  )
}

/**
 *  «به تفکیک دسته» (`expenses/breakdown`): همهٔ دسته‌های ماه با مبلغ و نوارِ سهم؛ زدنِ هر دسته
 *  همان را در فهرستِ پشتِ سر فیلتر می‌کند (دوباره = همه) و «نمایشِ همهٔ دسته‌ها» فیلتر را برمی‌دارد.
 */
@Composable
internal fun ExpenseBreakdownSheet(d: ShopData, month: String, selected: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
  val rows = remember(d, month) { ExpensesModel.byCategory(d, month) }
  val shares = ExpensesModel.shares(rows)
  ir.vil3ntec.tohid.ui.v3.V3Sheet(onDismiss = onDismiss, title = StrMoney.breakdownTitle(MoneyText.month(month))) { closer ->
    Text(
      StrMoney.breakdownHint,
      style = v3Text(12, FontWeight.Medium, 17),
      color = PhoneV3.colors.inkMuted,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      rows.forEachIndexed { i, (name, amount) ->
        BreakdownRow(name, amount, shares[i], categoryColor(i), selected == name) {
          onPick(if (selected == name) "" else name)
          closer.close()
        }
      }
    }
    if (selected != null) {
      Spacer(Modifier.height(12.dp))
      V3Button(StrMoney.allCategories, { onPick(""); closer.close() }, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY)
    }
  }
}
