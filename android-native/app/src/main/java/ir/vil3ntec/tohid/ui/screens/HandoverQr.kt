package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.pick.HandoverCode
import ir.vil3ntec.tohid.core.qr.QrCode

/**
 *  QRِ کدِ تحویل/دریافت (PCK-11، S43) — ماتریس از `core/pick/HandoverCode` (همان `core/qr/QrCode` ِ
 *  فاکتور). همیشه سیاه روی سفید با حاشیهٔ استاندارد، حتی در تمِ تیره، تا هر خواننده‌ای بخواند.
 *  ماتریس ساخته نشد ⇒ هیچ (عدد همیشه کنارش هست).
 */
@Composable
internal fun HandoverQr(orderNo: Long, code: String, modifier: Modifier = Modifier, side: Dp = 168.dp) {
  val rows = remember(orderNo, code) { HandoverCode.matrix(orderNo, code) } ?: return
  QrMatrix(rows, "QRِ کدِ سفارش #$orderNo", modifier, side)
}

/**
 *  QRِ هر متنِ کوتاه (برچسبِ کیسهٔ سفارش `VLNB-…`، PCK-09) با همان رمزگذارِ `core/qr/QrCode` (تا ۱۰۶
 *  بایت). ساخته نشد ⇒ هیچ (متن همیشه کنارش هست).
 */
@Composable
internal fun QrBox(text: String, modifier: Modifier = Modifier, side: Dp = 120.dp) {
  val rows = remember(text) { runCatching { QrCode.encode(text) }.getOrNull() } ?: return
  QrMatrix(rows, "QR: $text", modifier, side)
}

/** ماتریس ⇒ تصویر: سیاه روی سفید با حاشیهٔ استاندارد؛ بی آینه شدن در راست‌به‌چپ */
@Composable
private fun QrMatrix(rows: Array<BooleanArray>, label: String, modifier: Modifier, side: Dp) {
  val cells = rows.size + 2 * QrCode.QUIET_ZONE
  Canvas(
    modifier.size(side).semantics { contentDescription = label },
  ) {
    drawRect(Color.White)
    val cell = size.minDimension / cells
    for (y in rows.indices) {
      val row = rows[y]
      for (x in row.indices) {
        if (!row[x]) continue
        drawRect(
          Color.Black,
          topLeft = Offset((x + QrCode.QUIET_ZONE) * cell, (y + QrCode.QUIET_ZONE) * cell),
          //  یک دهمِ پیکسل روی‌هم تا میانِ خانه‌ها خطِ سفیدِ نازک نیفتد
          size = Size(cell + 0.1f, cell + 0.1f),
        )
      }
    }
  }
}
