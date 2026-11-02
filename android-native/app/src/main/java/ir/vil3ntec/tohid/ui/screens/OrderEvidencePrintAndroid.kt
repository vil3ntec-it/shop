package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrEvidence
import ir.vil3ntec.tohid.core.orders.OrderEvidence
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.print.ReportPrint

/**
 *  RSK-08 — چاپِ «پروندهٔ مدرک» از همان راهِ چاپِ جدولِ گزارش (`ReportPrint`: پنجرهٔ چاپِ اندروید، یا
 *  `ReportPrintDesk` روی کامپیوتر — چاپگر یا «ذخیره به PDF»). همان بخش‌های برگه و همهٔ اختلاف‌ها
 *  (`evidencePrintRows`)؛ صفحهٔ چاپیِ سرور (`GET …/:id/print`) توکن می‌خواهد و مرورگرِ بیرونی آن را ندارد.
 */
@Composable
internal fun rememberEvidencePrint(): (OrderEvidence.File, (Long) -> String) -> Unit {
  val context = LocalContext.current
  return remember(context) {
    { f, stamp ->
      val title = StrEvidence.title(f.order.orderNo.toString())
      ReportPrint.print(context, ReportCsv.Sheet(title, evidencePrintRows(f, stamp, ::evidenceMoney)), title)
    }
  }
}
