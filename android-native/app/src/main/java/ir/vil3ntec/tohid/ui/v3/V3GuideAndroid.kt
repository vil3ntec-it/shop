package ir.vil3ntec.tohid.ui.v3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.config.ApiConfig
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.guide.GuideVideo

/**
 *  OPS-06 — مرزِ سکوی «ویدیوی راهنما»: نشانیِ کاملِ فایلِ MP4 روی سرورِ همین برنامه (`/guides/<نام>.mp4`، بی پیشوندِ API).
 *  برنامهٔ بی سرور ⇒ `null` (ردیفِ ویدیو نمی‌آید). امروز هیچ راهنمایی ویدیو ندارد، پس این فقط جای «بعداً» است.
 */
@Composable
internal fun rememberGuideVideoUrl(v: GuideVideo): String? {
  val context = LocalContext.current
  return remember(v, context) {
    val base = runCatching { AppConfig.baseUrl(context) }.getOrDefault("")
    if (base.isBlank()) null else ApiConfig.urlOf(base, v.path, prefix = "")
  }
}
