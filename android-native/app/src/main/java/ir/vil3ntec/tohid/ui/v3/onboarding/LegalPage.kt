package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.core.legal.Legal
import ir.vil3ntec.tohid.core.legal.LegalKind
import ir.vil3ntec.tohid.core.legal.LegalSection
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3FormBar
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/**
 *  `legal/privacy` و `auth/terms` (نامِ دیگرش `legal/terms`) — «حریمِ خصوصی» و «شرایط و ضوابط» **درونِ برنامه**، به زبانِ
 *  همان لحظه (فارسی، دری، انگلیسی، پشتو) و بی اینترنت؛ متن از منبعِ یکتای [Legal] (همان که سرور در `/legal/privacy|terms` می‌دهد).
 *  پیش‌تر «حریمِ خصوصی» صفحهٔ بیرونیِ مرورگر بود. برنامهٔ کامپیوتر و آیفونِ دکان همین فایل را کامپایل می‌کنند.
 *
 *  عنوانِ درشتِ جمع‌شونده و خطِ زیرش (تاریخِ بازبینی، یا «نسخهٔ …» ِ شرایط)، هر بخش یک کارت؛ بخش‌های شرایط شمارهٔ خودشان را
 *  دارند تا بشود گفت «بندِ چهار». دکمهٔ چسبانِ پایین: «می‌پذیرم» وقتی از گامِ ثبت‌نام آمده و هنوز نپذیرفته، وگرنه «خواندم — بستن».
 */
@Composable
internal fun LegalPage(kind: LegalKind, onClose: () -> Unit, accepting: Boolean = false, onAccept: () -> Unit = onClose) {
  val lang = I18n.lang.code
  val doc = Legal.doc(kind, lang)
  val stagger = rememberV3Stagger()
  V3Scaffold(
    title = doc.title,
    subtitle = Legal.subtitle(doc, lang),
    onBack = onClose,
    notice = null,
    bottomBar = {
      V3FormBar(primary = if (accepting) StrAuth.iAccept else StrAuth.readClose, onPrimary = if (accepting) onAccept else onClose)
    },
  ) { pad ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      LazyColumn(
        Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = pad.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        itemsIndexed(doc.sections, key = { _, s -> s.id }) { i, s ->
          V3Card(Modifier.v3Stagger(stagger, i, i), padding = PaddingValues(16.dp)) {
            LegalSectionBody(s, number = if (kind == LegalKind.TERMS) i + 1 else null)
          }
        }
      }
    }
  }
}

@Composable
private fun LegalSectionBody(s: LegalSection, number: Int?) {
  val c = PhoneV3.colors
  Row(verticalAlignment = Alignment.Top) {
    if (number != null) {
      Box(
        Modifier.size(28.dp).clip(CircleShape).background(PhoneV3Palette.soft(c, PhoneV3Palette.Hue.BRAND)),
        contentAlignment = Alignment.Center,
      ) {
        Text(plain(number), style = V3Type.subhead, color = PhoneV3Palette.glyph(c, PhoneV3Palette.Hue.BRAND))
      }
      Spacer(Modifier.width(12.dp))
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(s.title, style = V3Type.headline, color = c.ink)
      val body = V3Type.callout.copy(lineHeight = V3Type.callout.lineHeight * 1.15f)
      s.paras.forEach { Text(legalText(it), style = body, color = c.inkMuted) }
      s.items.forEach {
        Row(Modifier.padding(top = 2.dp)) {
          Text("•", style = body, color = c.inkMuted)
          Spacer(Modifier.width(8.dp))
          Text(legalText(it), style = body, color = c.inkMuted, modifier = Modifier.weight(1f))
        }
      }
    }
  }
}

/** `**پررنگ**` ⇒ پررنگ؛ پیوندِ نسبیِ سرور (`/portal/…`) درونِ برنامه فقط نوشته است — حذفِ حساب در خودِ برنامه هم هست */
private fun legalText(s: String): AnnotatedString = buildAnnotatedString {
  for (span in Legal.spans(s)) {
    if (span.bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) } else append(span.text)
  }
}

/** همان صفحه برای «شرایط» با نامِ پیشینش (گامِ ثبت‌نام، مسیرِ `auth/terms`، نمای کامپیوتر) */
@Composable
internal fun TermsPage(accepting: Boolean, onClose: () -> Unit, onAccept: () -> Unit) =
  LegalPage(LegalKind.TERMS, onClose = onClose, accepting = accepting, onAccept = onAccept)
