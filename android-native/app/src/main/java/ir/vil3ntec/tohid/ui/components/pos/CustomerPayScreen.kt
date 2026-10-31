package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.core.i18n.StrPay
import ir.vil3ntec.tohid.core.qr.QrCode
import ir.vil3ntec.tohid.pay.CustomerPay
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  صفحهٔ رو به مشتری در پرداختِ حساب‌پی (HW-10، POS-30): مبلغِ درشت، همان QR ِ برگهٔ صندوقدار و وضعیت.
 *  افقی (نمایشگرِ دومِ کارت‌خوان) ⇐ QR کنارِ مبلغ؛ عمودی ⇐ زیرِ هم.
 */
@Composable
fun CustomerPayScreen(storeName: String, v: CustomerPay.View) {
  val c = Shop.colors
  val rows = remember(v.qrPayload) {
    if (v.qrPayload.isBlank()) null else runCatching { QrCode.encode(v.qrPayload, QrCode.LARGE_MAX_VERSION) }.getOrNull()
  }
  BoxWithConstraints(Modifier.fillMaxSize().background(c.bg).padding(24.dp)) {
    val wide = maxWidth > maxHeight
    val side = (if (wide) minOf(maxHeight - 48.dp, maxWidth / 2) else minOf(maxWidth, maxHeight * 0.55f)).coerceAtMost(460.dp)
    val text: @Composable () -> Unit = {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (storeName.isNotBlank()) Text(storeName, style = MaterialTheme.typography.titleLarge, color = c.muted)
        Spacer(Modifier.height(8.dp))
        Text(StrPay.customerAmount, style = MaterialTheme.typography.titleMedium, color = c.text)
        Text("${amountText(v.amountMinor)} ؋", fontSize = 56.sp, color = c.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        when (v.stage) {
          CustomerPay.Stage.WAITING -> Text(StrPay.customerScan, style = MaterialTheme.typography.titleMedium, color = c.text, textAlign = TextAlign.Center)
          CustomerPay.Stage.PAID -> Text(StrPay.customerPaid, style = MaterialTheme.typography.headlineSmall, color = c.success, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
      }
    }
    val qr: @Composable () -> Unit = {
      Box(Modifier.size(side).clip(RoundedCornerShape(20.dp)).background(Color.White), contentAlignment = Alignment.Center) {
        if (rows != null && v.stage == CustomerPay.Stage.WAITING) QrImage(rows, Modifier.fillMaxSize().padding(10.dp))
        else Text("✓", fontSize = 96.sp, color = Color(0xFF15803D))
      }
    }
    if (wide) {
      Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        qr()
        text()
      }
    } else {
      Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
        text()
        qr()
      }
    }
  }
}
