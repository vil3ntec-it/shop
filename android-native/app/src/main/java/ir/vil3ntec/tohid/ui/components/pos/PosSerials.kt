package ir.vil3ntec.tohid.ui.components.pos

// i18n: سه‌زبانه (`StrProduct`)

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ir.vil3ntec.tohid.core.i18n.StrProduct
import ir.vil3ntec.tohid.core.pos.SerialNumbers
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.SerialSale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.theme.Shop

/** POS-28 — سریال‌های ردیفِ کالای سریال‌دار در کادرِ ردیف: متنِ آغازین و ذخیره (متنِ خام ⇒ `SerialNumbers.parse`) */
class PosLineSerials(val text: String, private val write: (List<String>) -> Unit) {
  fun save(text: String) = write(SerialNumbers.parse(text))
}

/** فقط برای ردیفِ کالای سریال‌دار؛ کالای دیگر یا قلمِ آزاد ⇒ `null` (همان کادرِ پیشین) */
fun posLineSerials(d: ShopData, line: SalesEngine.CartLine, write: (List<String>) -> Unit): PosLineSerials? {
  if (line.free || !SerialSale.tracked(d.products.firstOrNull { it.id == line.productId })) return null
  return PosLineSerials(SerialNumbers.format(line.serials.orEmpty()), write)
}

/** کادرِ سریال‌ها با شمارِ «N از M» و ایرادِ تکرار همان‌جا (فروخته‌شدن را ثبت می‌سنجد) */
@Composable
fun PosSerialField(text: String, onText: (String) -> Unit, qty: Double?) {
  val list = SerialNumbers.parse(text)
  val need = qty?.let { Math.round(it * 1000.0) } ?: 0L
  val problem = SerialNumbers.problem(list, need, emptySet())
  OutlinedTextField(
    value = text,
    onValueChange = { onText(it.take(SerialNumbers.MAX_PER_LINE * (SerialNumbers.MAX_LEN + 1))) },
    label = { Text(StrProduct.serials) },
    supportingText = {
      val count = StrProduct.serialCount(ir.vil3ntec.tohid.plain(list.size), ir.vil3ntec.tohid.plain((need / 1000).toInt()))
      when (problem) {
        null -> Text(count, color = Shop.colors.success)
        is SerialNumbers.Problem.Duplicate -> Text(StrProduct.serialDuplicate(problem.serial), color = Shop.colors.danger)
        else -> Text(count, color = Shop.colors.warning)
      }
    },
    singleLine = false,
    modifier = Modifier.fillMaxWidth(),
  )
}
