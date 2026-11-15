package ir.vil3ntec.tohid.ui.v3.branches

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Store
import ir.vil3ntec.tohid.core.i18n.StrBranchDesk
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.SettingsEntry
import ir.vil3ntec.tohid.ui.v3.nav.V3Route

/** شعبه درونِ یک دکان، فازِ ۲ — نشانی‌ها */
object BranchPaths {
  const val ROOT = "branches"
  const val REPORT = "branches/report"
}

/**
 *  بخشِ «شعبه‌ها» (`docs/BRANCHES-fa.md` §۸ فازِ ۲). راهش ردیفِ «حسابِ من» ([BranchProfileCard])، ردیفِ تنظیمات و پرسشِ
 *  پس از ورود ([BranchHost])، همه فقط با پاسخِ روشنِ سرور (`shop.branches`)؛ نشانیِ مستقیم با پرچمِ خاموش فقط «روشن نیست».
 *  سنجشِ صاحب/عضو در سرور است (نوشتن‌ها فقط صاحب، گزارشِ جمع فقط صاحب).
 */
val BranchesModule = AppModule(
  id = "branches",
  title = { StrBranchDesk.title },
  icon = Icons.Outlined.Store,
  //  ردیفِ «دکان و امنیت» فقط با پاسخِ روشن (صاحب، یا عضوِ چندشعبه‌ای)
  settings = listOf(
    SettingsEntry({ StrBranchDesk.title }, BranchPaths.ROOT, Icons.Outlined.Store, SettingsLayout.SHOP, shown = { BranchLive.visible }, subtitle = { StrBranchDesk.pickNote }),
  ),
  routes = listOf(
    V3Route(BranchPaths.ROOT, RouteKind.PAGE, { StrBranchDesk.title }, chrome = Chrome.OWN) { BranchesPage() },
    V3Route(BranchPaths.REPORT, RouteKind.PAGE, { StrBranchDesk.report }, parent = BranchPaths.ROOT, chrome = Chrome.OWN) { BranchReportPage() },
  ),
)
