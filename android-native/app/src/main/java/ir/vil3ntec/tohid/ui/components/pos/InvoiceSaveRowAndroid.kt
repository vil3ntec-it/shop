package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.print.InvoiceFile
import kotlinx.coroutines.launch

/**
 *  POS-32، کامپیوتر — زیرِ پنجرهٔ فاکتور (`InvoiceDialog`): «ذخیرهٔ تصویر» و، با فاکتورِ نسخهٔ ۲ (`ReceiptFlag`، همان شرطِ
 *  «فرستادنِ PDF» ِ گوشی)، «ذخیرهٔ PDF» — همان رسیدِ «فرستادن» ِ گوشی به فایل (`InvoiceFile.save`). گوشی ⇒ هیچ (همان
 *  «فرستادن» ِ خودش). فایلِ مرزیِ پلتفرم (`Context`).
 */
@Composable
fun InvoiceSaveRow(d: ShopData, sale: Sale, onMessage: (String) -> Unit) {
  if (!ir.vil3ntec.tohid.ui.Platform.DESKTOP) return
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val pdfOn = remember { ir.vil3ntec.tohid.data.ReceiptFlag.enabled(context) }
  Spacer(Modifier.height(8.dp))
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(onClick = { scope.launch { onMessage(InvoiceFile.save(context, d, sale, pdf = false)) } }, modifier = Modifier.weight(1f)) {
      Text(StrSale.saveImage)
    }
    if (pdfOn) OutlinedButton(onClick = { scope.launch { onMessage(InvoiceFile.save(context, d, sale, pdf = true)) } }, modifier = Modifier.weight(1f)) {
      Text(StrSale.savePdf)
    }
  }
}
