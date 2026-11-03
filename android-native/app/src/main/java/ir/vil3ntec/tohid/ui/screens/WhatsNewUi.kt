package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.core.guide.WhatsNew
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  OPS-05 — رابطِ «تازه‌ها» (فقط نسخهٔ ۳؛ گوشی، کامپیوتر و آیفون): برگهٔ یک‌باره پس از به‌روزرسانی ([WhatsNewSheet]) و
 *  صفحهٔ همیشگیِ «بیشتر» ← «تازه‌ها» ([WhatsNewScreen]، مسیرِ `whats-new`). متنِ سطرها از `docs/WHATS-NEW-fa.md` ِ
 *  همراهِ برنامه است (`WhatsNew.bundled`)؛ دری با لایهٔ واژه، انگلیسی و پشتو همان فارسی با یک خطِ یادآوری.
 *  بی Context و بی نوشتن — باز و بسته و «دیده‌شده» در `WhatsNewAndroid.kt`.
 */

/** برگهٔ یک‌باره: فقط سطرهای ندیده، دسته‌شده به بخش؛ «فهمیدم» یا بستن ⇒ [onDone] (دیده‌شده) */
@Composable
internal fun WhatsNewSheet(sections: List<WhatsNew.Section>, current: String, onDone: () -> Unit) {
  V3Sheet(onDismiss = onDone, title = StrSettings.whatsNewNow) { closer ->
    Text(
      StrSettings.whatsNewVersion(current),
      style = V3Type.footnote,
      color = PhoneV3.colors.inkMuted,
      modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    )
    sections.forEach { WhatsNewSectionBlock(it, current) }
    PersianOnlyNote()
    Spacer(Modifier.height(12.dp))
    V3Button(StrSettings.whatsNewGotIt, { closer.close() }, Modifier.fillMaxWidth())
  }
}

/** «بیشتر» ← «تازه‌ها»: همهٔ بخش‌های همراهِ همین نسخه، تازه‌ترین اول */
@Composable
fun WhatsNewScreen(onBack: () -> Unit) {
  val current = BuildConfig.VERSION_NAME
  val sections = WhatsNew.bundled.sections
  V3Scaffold(title = StrSettings.whatsNew, subtitle = StrSettings.whatsNewVersion(current), onBack = onBack) { pad ->
    if (sections.isEmpty()) V3EmptyState(Icons.Outlined.NewReleases, StrSettings.whatsNewEmpty)
    else LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = pad.calculateBottomPadding() + 16.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      itemsIndexed(sections, key = { i, s -> "$i:${s.title}" }) { _, s -> WhatsNewSectionBlock(s, current) }
      item(key = "note") { PersianOnlyNote() }
    }
  }
}

/** یک بخش: عنوان (با شمارهٔ ساختِ همین کانال اگر پیش از این سامانه پخش شده) و سطرها با برچسبِ نوع */
@Composable
private fun WhatsNewSectionBlock(section: WhatsNew.Section, current: String) {
  val lang = I18n.source().code
  val title = section.titleIn(lang)
  val heading = WhatsNew.versionOf(section, current)?.let { StrSettings.whatsNewVersion(it) + " · " + title } ?: title
  V3Section(heading, padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
    section.entries.forEachIndexed { i, e ->
      if (i > 0) Spacer(Modifier.height(10.dp))
      WhatsNewEntryRow(e, lang)
    }
  }
}

@Composable
private fun WhatsNewEntryRow(e: WhatsNew.Entry, lang: String) {
  val c = PhoneV3.colors
  val (status, label) = when (e.kind) {
    WhatsNew.Kind.REMOVED -> V3Status.CANCELLED to StrSettings.whatsNewKindRemoved
    WhatsNew.Kind.CHANGED -> V3Status.IN_DELIVERY to StrSettings.whatsNewKindChanged
    WhatsNew.Kind.FIXED -> V3Status.PREPARING to StrSettings.whatsNewKindFixed
    WhatsNew.Kind.NEW -> V3Status.DELIVERED to StrSettings.whatsNewKindNew
  }
  Column(Modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically) { V3StatusPill(status, label) }
    Spacer(Modifier.height(4.dp))
    Text(e.textIn(lang), style = V3Type.callout, color = c.ink)
    e.insteadIn(lang)?.let { Text(StrSettings.whatsNewInstead(it), style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 2.dp)) }
  }
}

/** متنِ سطرها فارسی است (برای صاحب‌کار نوشته می‌شود)؛ انگلیسی و پشتو یک خطِ یادآوری می‌گیرند */
@Composable
private fun PersianOnlyNote() {
  val lang = I18n.source().code
  if (lang != "en" && lang != "ps") return
  Text(
    StrSettings.whatsNewPersianOnly,
    style = V3Type.footnote,
    color = PhoneV3.colors.inkMuted,
    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
  )
}
