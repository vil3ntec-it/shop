package af.tohid.shop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import af.tohid.shop.ui.theme.Dims
import af.tohid.shop.ui.theme.Radius
import af.tohid.shop.ui.theme.T

/* ================================================================== */
/*  هدر بالای صفحه — معادل .header                                     */
/* ================================================================== */

@Composable
fun AppHeader(
    title: String,
    userInitial: String = "ک",
    notificationCount: Int = 0,
    onNotifications: () -> Unit = {},
    onSettings: () -> Unit = {},
    onAccount: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(Dims.header)
                .background(T.bg)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (onBack != null) {
                    IconSquare(Icons.Outlined.ArrowForward, "بازگشت", onBack)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = T.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    IconSquare(Icons.Outlined.NotificationsNone, "اعلان‌ها", onNotifications)
                    if (notificationCount > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(T.danger),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (notificationCount > 9) "۹+" else faDigits(notificationCount.toString()),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }
                IconSquare(Icons.Outlined.Settings, "تنظیمات", onSettings)
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(T.surface2)
                        .pressable { onAccount() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(userInitial, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = T.text)
                }
            }
        }
        Divider()
    }
}

@Composable
private fun IconSquare(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(T.bg)
            .border(1.dp, T.border, RoundedCornerShape(Radius.sm))
            .pressable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = T.muted, modifier = Modifier.size(18.dp))
    }
}

/* ================================================================== */
/*  نوار پایین — معادل .bottom-nav                                     */
/* ================================================================== */

data class NavEntry(val route: String, val label: String, val icon: ImageVector)

@Composable
fun BottomNav(
    entries: List<NavEntry>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
) {
    Column {
        Divider()
        Row(
            Modifier
                .fillMaxWidth()
                .height(Dims.bottomNav)
                .background(T.bg),
        ) {
            entries.forEach { e ->
                val active = currentRoute == e.route
                val tint = if (active) T.primary else T.muted2
                // آیکون تب فعال یک جهش کوچک می‌زند تا تعویض صفحه دیده شود
                val pop by animateFloatAsState(
                    targetValue = if (active) 1.14f else 1f,
                    animationSpec = spring(dampingRatio = 0.42f, stiffness = 700f),
                    label = "navPop",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressable(scaleDown = 0.9f) { onSelect(e.route) },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        e.icon, contentDescription = e.label, tint = tint,
                        modifier = Modifier.size(21.dp).scale(pop),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        e.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tint,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/* ================================================================== */
/*  دکمه‌ی شناور — معادل .fab                                          */
/* ================================================================== */

@Composable
fun Fab(onClick: () -> Unit, icon: ImageVector = Icons.Outlined.Add, label: String = "افزودن") {
    Box(
        Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(T.primary, T.primaryDark)))
            .pressable(scaleDown = 0.9f) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

/* ================================================================== */
/*  ارقام فارسی                                                        */
/* ================================================================== */

private val faDigitChars = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

fun faDigits(s: String): String = buildString {
    for (ch in s) append(if (ch in '0'..'9') faDigitChars[ch - '0'] else ch)
}
