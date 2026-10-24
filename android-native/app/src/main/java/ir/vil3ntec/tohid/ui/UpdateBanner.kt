package ir.vil3ntec.tohid.ui

import androidx.compose.runtime.Composable
import ir.vil3ntec.tohid.ui.v3.shell.UpdateNotice

/**
 *  نوارِ «نسخهٔ تازه» — حالا `UpdateNotice` ِ نسخهٔ ۳ (`ui/v3/shell/ShellNotices.kt`): همان
 *  بررسیِ خودکار، گرفتن و نصب، «بعداً» و پیامِ «زیرِ حداقلِ پشتیبانی»؛ زدنِ نوار ⇐
 *  `settings/about`. این نام برای صدازننده‌های امروزِ پوسته مانده است.
 */
@Composable
fun UpdateBanner() = UpdateNotice()
