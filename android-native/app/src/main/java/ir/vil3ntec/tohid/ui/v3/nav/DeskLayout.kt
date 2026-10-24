package ir.vil3ntec.tohid.ui.v3.nav

import androidx.compose.runtime.staticCompositionLocalOf
import ir.vil3ntec.tohid.ui.Platform

/**
 *  چیدمانِ کامپیوتری (Desk*) یا گوشی؟ پیش‌فرض همان `Platform.DESKTOP`؛ پیش‌نمایشِ گوشی در
 *  برنامهٔ کامپیوتر `false` می‌دهد تا عکس‌ها نسخهٔ گوشیِ صفحه را نشان دهند (AUDIT-NAV N11).
 *  در مسیرها `desk` ِ `RouteScope` را بخوانید، نه `Platform.DESKTOP`.
 */
val LocalDeskLayout = staticCompositionLocalOf { Platform.DESKTOP }
