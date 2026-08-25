package af.tohid.shop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import af.tohid.shop.ui.components.PageToolbar
import af.tohid.shop.ui.components.TCard
import af.tohid.shop.ui.theme.Radius
import af.tohid.shop.ui.theme.T

@Composable
fun MoreScreen(onOpen: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(T.surface)
            .verticalScrollCompat()
            .padding(16.dp),
    ) {
        PageToolbar("بیشتر", "بخش‌های دیگر برنامه")

        MoreRow("تاریخچه فروش", "فاکتورها، مرجوعی و لغو فروش", Icons.Outlined.ReceiptLong) { onOpen("sales") }
        Spacer(Modifier.height(10.dp))
        MoreRow("خریداری", "تأمین‌کننده‌ها و بدهی به آن‌ها", Icons.Outlined.LocalShipping) { onOpen("purchasing") }
        Spacer(Modifier.height(10.dp))
        MoreRow("گزارش‌ها", "سود، فروش و مصارف در بازه‌ی دلخواه", Icons.Outlined.BarChart) { onOpen("reports") }
        Spacer(Modifier.height(10.dp))
        MoreRow("دفتر رویدادها", "هر تغییری که در دفتر ثبت شده", Icons.Outlined.History) { onOpen("audit") }
        Spacer(Modifier.height(10.dp))
        MoreRow("دکان و همگام‌سازی", "اتصال به سرور و اعضای دکان", Icons.Outlined.Sync) { onOpen("shop") }
        Spacer(Modifier.height(10.dp))
        MoreRow("تنظیمات", "نسخه‌ی برنامه و به‌روزرسانی", Icons.Outlined.Settings) { onOpen("settings") }

        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun MoreRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    TCard(Modifier.fillMaxWidth(), padding = 14.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(T.primaryTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = T.primary, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = T.text)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 11.5.sp, color = T.muted)
            }
            Icon(
                Icons.Outlined.KeyboardArrowLeft,
                contentDescription = null,
                tint = T.muted2,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
