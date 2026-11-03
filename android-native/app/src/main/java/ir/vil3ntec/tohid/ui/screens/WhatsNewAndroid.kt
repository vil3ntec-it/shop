package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.core.guide.WhatsNew
import ir.vil3ntec.tohid.data.WhatsNewPref

/**
 *  OPS-05 — مرزِ پلتفرمِ «تازه‌ها» (خواندن و نوشتنِ `WhatsNewPref`، نسخهٔ `BuildConfig`)؛ منطق در `core/guide/WhatsNew`.
 *
 *  [WhatsNewOnUpdate] در پوستهٔ نسخهٔ ۳ (`AppRoot`، پس از همهٔ قفل‌ها و دروازه‌ها): نصبِ تازه همهٔ سطرهای همراهِ
 *  برنامه را «دیده‌شده» می‌گذارد و هیچ نشان نمی‌دهد؛ پس از به‌روزرسانی، سطرهایی که این دستگاه ندیده یک بار در
 *  برگه، و «فهمیدم» (یا بستن) ⇒ دیده‌شده. کلیدِ ردهٔ A خاموش ⇒ هیچ خواندن و نوشتنی.
 */
@Composable
fun WhatsNewOnUpdate() {
  val context = LocalContext.current
  val current = BuildConfig.VERSION_NAME
  val sections = WhatsNew.bundled.sections
  val pending = remember<List<WhatsNew.Section>> {
    if (!WhatsNewPref.on(context)) return@remember emptyList()
    val plan = WhatsNew.plan(sections, WhatsNewPref.keys(context), WhatsNewPref.legacySeen(context), current)
    plan.storeNow?.let { WhatsNewPref.setKeys(context, it) }
    plan.show
  }
  var open by remember { mutableStateOf(pending.isNotEmpty()) }
  if (!open) return
  WhatsNewSheet(pending, current) {
    WhatsNewPref.setKeys(context, WhatsNew.ack(sections, WhatsNewPref.keys(context)))
    open = false
  }
}

/** مسیرِ صفحهٔ «تازه‌ها» (`FoundationRoutes`): «بیشتر» ِ گوشی و کامپیوتر و «دربارهٔ برنامه» */
const val WHATS_NEW_ROUTE = "whats-new"

/** «دیدنِ همهٔ تغییرها» در «دربارهٔ برنامه» ِ نسخهٔ ۳ ⇒ همان صفحهٔ «بیشتر» ← «تازه‌ها» */
fun openWhatsNew(nav: ir.vil3ntec.tohid.ui.v3.nav.V3Nav?) {
  nav?.open(WHATS_NEW_ROUTE) ?: ir.vil3ntec.tohid.ui.AppNav.go(WHATS_NEW_ROUTE)
}

/**
 *  کلیدِ ردهٔ A (`settings/data/whats-new`) روشن شد: «از همین نسخه به بعد» — یادداشت‌های گذشته ناگهان سر باز نمی‌کنند.
 *  (خاموش ⇒ دیگر هیچ خوانده و نوشته نمی‌شود.)
 */
fun whatsNewFromNow(context: android.content.Context) {
  if (WhatsNewPref.keys(context) == null) WhatsNewPref.setKeys(context, WhatsNew.seenAll(WhatsNew.bundled.sections))
}
