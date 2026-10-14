package ir.vil3ntec.tohid.ui.screens

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed

/**
 *  F0-19 — کلیدِ اصلیِ میان‌برها: Ctrl در ویندوز و لینوکس، Cmd در macOS.
 *
 *  میان‌برهای هر صفحه (Ctrl+S، Ctrl+F، Ctrl+Enter …) فقط `isCtrlPressed` را
 *  می‌دیدند و روی مک Cmd هیچ کاری نمی‌کرد؛ پنجرهٔ اصلی (`Main.kt`) از قبل
 *  هر دو را می‌پذیرفت. صفحه‌ها `isCtrlPressed` را مستقیم نمی‌خوانند
 *  (`PrimaryModifierTest` این را می‌پاید). در فاز ۱ همهٔ کلیدها به ثبتِ
 *  مرکزیِ کلیدها می‌روند.
 */
val KeyEvent.isPrimaryModifier: Boolean get() = isCtrlPressed || isMetaPressed
