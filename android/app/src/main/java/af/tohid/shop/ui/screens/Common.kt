package af.tohid.shop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import af.tohid.shop.ui.components.PageToolbar
import af.tohid.shop.ui.components.TCard
import af.tohid.shop.ui.theme.Radius
import af.tohid.shop.ui.theme.T

/*
 * این فایل فقط پوسته‌ی سازگاری است: امضای توابع قدیمی حفظ شده تا صفحه‌های
 * موجود نشکنند، ولی ظاهرشان همان طراحی مشترک نسخه‌ی وب است.
 * صفحه‌های تازه مستقیم از `ui.components` استفاده می‌کنند.
 */

@Composable
fun ScreenScaffold(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(T.surface)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageToolbar(title, subtitle)
        content()
    }
}

@Composable
fun SimpleStat(label: String, value: String, modifier: Modifier = Modifier) {
    TCard(modifier, padding = 16.dp) {
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = T.muted)
        Spacer(Modifier.height(8.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = T.text, maxLines = 1)
    }
}

enum class PanelTone { Neutral, Warning, Danger }

@Composable
fun InfoPanel(title: String, body: String, tone: PanelTone = PanelTone.Neutral) {
    val bg = when (tone) {
        PanelTone.Danger -> T.dangerTint
        PanelTone.Warning -> T.warningTint
        PanelTone.Neutral -> T.surface2
    }
    val fg = when (tone) {
        PanelTone.Danger -> T.danger
        PanelTone.Warning -> T.warning
        PanelTone.Neutral -> T.muted
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.25f), RoundedCornerShape(Radius.md))
            .padding(14.dp),
    ) {
        Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = fg)
        Spacer(Modifier.height(5.dp))
        Text(body, fontSize = 12.5.sp, color = T.text, lineHeight = 23.sp)
    }
}

@Composable
fun SimpleEmpty(text: String) {
    af.tohid.shop.ui.components.EmptyState(
        icon = Icons.Outlined.Inbox,
        title = "هنوز اطلاعاتی ثبت نشده",
        subtitle = text,
    )
}

/** اسکرول عمودی با حالت به‌خاطر سپرده‌شده — برای کوتاه شدن کد صفحه‌ها. */
@Composable
fun Modifier.verticalScrollCompat(): Modifier = this.verticalScroll(rememberScrollState())
