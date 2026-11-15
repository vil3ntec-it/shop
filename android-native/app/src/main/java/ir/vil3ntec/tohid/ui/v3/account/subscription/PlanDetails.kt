package ir.vil3ntec.tohid.ui.v3.account.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  کشوی جزئیاتِ کارتِ پلن (درونِ `PlanCard`): سه خانهٔ کوتاه (مدت، هر روز، صرفه‌جویی در برابرِ کوتاه‌ترین پلن)، مقایسهٔ
 *  فشردهٔ «با اشتراک باز می‌شود» و «همیشه رایگان» (همان فهرستِ واقعیِ `PlansModel`، تراشه به‌جای دو فهرستِ بلند)، و سه
 *  سطرِ قاعدهٔ تمدید و پایان — همان که سرور می‌کند: تمدیدِ زودهنگام روزها را به آخرِ اشتراکِ فعلی می‌افزاید
 *  (`subscriptions.grant`)، تمدیدِ خودکار و برداشتِ خودکار نیست (پرداخت از راه واتساپ)، و پس از پایان بخش‌های
 *  رایگان باز می‌ماند و داده پاک نمی‌شود.
 */
@Composable
internal fun PlanDetails(option: PlansModel.Option, saving: PlansModel.Saving?, currency: String) {
  val c = PhoneV3.colors
  Column(Modifier.fillMaxWidth().testTag(PlansTags.details(option.code)).padding(start = 14.dp, end = 14.dp, bottom = 16.dp)) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Fact(StrAccount.factLength, Str.days(option.days), Modifier.weight(1f))
      Fact(StrAccount.factPerDay, money(option.perDay) + " " + currency, Modifier.weight(1f))
      if (saving != null) Fact(StrAccount.factSaving, PlansModel.price(saving.amount, currency), Modifier.weight(1f), accent = true)
    }
    if (saving != null) {
      Text(StrAccount.saveVs(saving.base.title), style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp))
    }
    Spacer(Modifier.height(16.dp))
    Features(StrAccount.withSubscription, PlansModel.paidFeatures, Icons.Outlined.LockOpen, strong = true)
    Spacer(Modifier.height(12.dp))
    Features(StrAccount.freeAlways, PlansModel.freeFeatures, Icons.Outlined.Check, strong = false)
    Spacer(Modifier.height(16.dp))
    Term(Icons.Outlined.Autorenew, StrAccount.renewEarly)
    Term(Icons.Outlined.MoneyOff, StrAccount.noAutoRenew)
    Term(Icons.Outlined.Shield, StrAccount.afterEnd)
  }
}

/** خانهٔ کوتاهِ «نام / مقدار» — مقدار اگر جا نشد به سطرِ دوم می‌رود، بریده نمی‌شود */
@Composable
private fun Fact(label: String, value: String, modifier: Modifier, accent: Boolean = false) {
  val c = PhoneV3.colors
  val ok = PhoneV3Roles.tone(c, V3Tone.SUCCESS)
  val shape = RoundedCornerShape(12.dp)
  Column(
    modifier
      .background(if (accent) ok.bg else c.bg, shape)
      .border(1.dp, if (accent) ok.bg else c.hairline, shape)
      .padding(horizontal = 10.dp, vertical = 8.dp),
  ) {
    Text(label, style = V3Type.caption, color = if (accent) ok.fg else c.inkMuted, maxLines = 1)
    Spacer(Modifier.height(2.dp))
    Text(value, style = v3Text(14, FontWeight.SemiBold, 19), color = if (accent) ok.fg else c.ink, maxLines = 2)
  }
}

/** گروهِ قابلیت‌ها: سرخطِ کوچک و تراشه‌ها؛ `strong` = آن‌چه همین اشتراک باز می‌کند (رنگِ برند) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Features(title: String, items: List<String>, icon: ImageVector, strong: Boolean) {
  val c = PhoneV3.colors
  val tint = if (strong) PhoneV3Roles.brandText(c) else c.inkMuted
  val ink = if (strong) tint else c.ink
  val bg = if (strong) PhoneV3Palette.soft(c, PhoneV3Palette.Hue.BRAND) else c.bg
  Text(title, style = V3Type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.ink, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp).semantics { heading() })
  FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    items.forEach { Chip(it, icon, tint, ink, bg, if (strong) bg else c.hairline) }
  }
}

@Composable
private fun Chip(text: String, icon: ImageVector, tint: Color, ink: Color, bg: Color, edge: Color) {
  val shape = RoundedCornerShape(50)
  Row(
    Modifier.background(bg, shape).border(1.dp, edge, shape).padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
    Spacer(Modifier.width(4.dp))
    Text(text, style = V3Type.footnote, color = ink)
  }
}

/** سطرِ قاعدهٔ تمدید و پایان */
@Composable
private fun Term(icon: ImageVector, text: String) {
  val c = PhoneV3.colors
  Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Top) {
    Icon(icon, contentDescription = null, tint = c.inkMuted, modifier = Modifier.padding(top = 1.dp).size(16.dp))
    Spacer(Modifier.width(8.dp))
    Text(text, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.weight(1f))
  }
}
