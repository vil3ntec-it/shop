package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.v3.shell.HomeScreen

/**
 *  خانه — حالا `HomeScreen` ِ نسخهٔ ۳ (`ui/v3/shell`): گوشی r7/r6/r8 و تبلت دو ستون؛
 *  داشبوردِ پنلیِ پیشینِ تبلت برداشته شد. این نام فقط برای پیش‌نمایشِ امروزِ کامپیوتر
 *  (`PhonePreview`) مانده و در فاز ۳ برداشته می‌شود.
 */
@Composable
fun DashboardScreen(d: ShopData, bottomReserve: Dp = PhoneNav.RESERVE, onOpen: (String) -> Unit = {}) {
  HomeScreen(d, onOpen, bottomReserve)
}
