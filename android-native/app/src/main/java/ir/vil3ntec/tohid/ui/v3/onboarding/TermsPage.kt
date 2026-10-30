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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.data.Terms
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
 *  `auth/terms` — متنِ کاملِ شرایط و ضوابط (همان متن و نسخهٔ `Terms`، دست‌نخورده): عنوانِ درشتِ
 *  جمع‌شونده، «نسخهٔ …»، بندها با شمارهٔ خودشان تا بشود گفت «بندِ چهار»، و دکمهٔ چسبانِ پایین —
 *  «می‌پذیرم» وقتی از گامِ ۳ آمده و هنوز نپذیرفته (تیک می‌خورد و برمی‌گردد)، وگرنه «خواندم — بستن».
 */
@Composable
internal fun TermsPage(accepting: Boolean, onClose: () -> Unit, onAccept: () -> Unit) {
  val c = PhoneV3.colors
  val stagger = rememberV3Stagger()
  V3Scaffold(
    title = Terms.TITLE,
    subtitle = Str.version(Terms.VERSION),
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
        itemsIndexed(Terms.SECTIONS, key = { i, _ -> i }) { i, (title, body) ->
          V3Card(Modifier.v3Stagger(stagger, i, i), padding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
              Box(
                Modifier.size(28.dp).clip(CircleShape).background(PhoneV3Palette.soft(c, PhoneV3Palette.Hue.BRAND)),
                contentAlignment = Alignment.Center,
              ) {
                Text(plain(i + 1), style = V3Type.subhead, color = PhoneV3Palette.glyph(c, PhoneV3Palette.Hue.BRAND))
              }
              Spacer(Modifier.width(12.dp))
              Column(Modifier.weight(1f)) {
                Text(title, style = V3Type.headline, color = c.ink)
                Spacer(Modifier.height(6.dp))
                Text(body, style = V3Type.callout.copy(lineHeight = V3Type.callout.lineHeight * 1.15f), color = c.inkMuted)
              }
            }
          }
        }
      }
    }
  }
}
