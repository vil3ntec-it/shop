package ir.vil3ntec.tohid.ui.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.guide.GuideHelp
import ir.vil3ntec.tohid.core.guide.ScreenGuide
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.screens.v3Text

/**
 *  OPS-06 — دکمهٔ «راهنما» (تصمیمِ ۱۴۰۵/۰۸/۲۳، پرسشِ ۱۶: متنِ آماده حالا، ویدیو بعداً). متن همان راهنمای تأییدشدهٔ
 *  `core/guide/ScreenGuides` است که F1 ِ کامپیوتر نشان می‌دهد، به زبانِ برنامه (`GuideHelp.view`؛ دری از فارسی، گامِ پشتوی
 *  نانوشته ⇐ فارسی) و درونِ خودِ برنامه — بی اینترنت.
 *
 *  [LocalV3Guide] را میزبانِ مسیر (`V3RouteHost`) فقط برای صفحهٔ ریشهٔ هر بخش که راهنمای آماده دارد می‌دهد
 *  (`GuideHelp.forRoute`)؛ `V3Scaffold`، سرخطِ «خانه» و «…» ِ صندوق از آن دکمه می‌سازند. بی راهنما ⇒ هیچ دکمه‌ای.
 *  ویدیو: فقط وقتی راهنما ویدیو دارد ردیفش می‌آید (امروز هیچ‌کدام ندارد ⇒ هیچ دکمهٔ مرده‌ای نیست).
 */
val LocalV3Guide = compositionLocalOf<ScreenGuide?> { null }

@Composable
fun V3GuideSheet(guide: ScreenGuide, onDismiss: () -> Unit) {
  val c = PhoneV3.colors
  val lang = ir.vil3ntec.tohid.LocaleChoice.lang.code
  val view = remember(guide, lang) { GuideHelp.view(guide, lang) }
  val video = view.video?.let { rememberGuideVideoUrl(it) }
  val uri = LocalUriHandler.current
  V3Sheet(onDismiss = onDismiss, title = StrKit.guideTitle(view.title)) { closer ->
    V3Card(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
      view.steps.forEachIndexed { i, step ->
        if (i > 0) V3Divider(start = 52.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
          Box(Modifier.size(26.dp).clip(CircleShape).background(c.tile), contentAlignment = Alignment.Center) {
            Text(StrKit.guideStep(i + 1), style = v3Text(13, FontWeight.Bold, 18), color = PhoneV3Roles.brandText(c))
          }
          Spacer(Modifier.width(12.dp))
          Text(step, style = V3Type.body, color = c.ink, modifier = Modifier.weight(1f))
        }
      }
    }
    if (video != null) {
      Spacer(Modifier.height(12.dp))
      V3Button(StrKit.guideVideo(view.video!!.seconds), { runCatching { uri.openUri(video) } }, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.OndemandVideo)
    }
    Spacer(Modifier.height(12.dp))
    Text(StrKit.guideOffline, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(horizontal = 4.dp))
    Spacer(Modifier.height(12.dp))
    V3Button(StrKit.close, { closer.close {} }, Modifier.fillMaxWidth(), kind = V3ButtonKind.NEUTRAL)
  }
}
