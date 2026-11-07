package ir.vil3ntec.tohid.ui.v3.account.team

import androidx.compose.runtime.Composable
import ir.vil3ntec.tohid.data.repo.ShopRepository
import ir.vil3ntec.tohid.ui.screens.ApproverSetupSection
import ir.vil3ntec.tohid.ui.screens.DevicesSection
import ir.vil3ntec.tohid.ui.screens.MemberPermsSection
import ir.vil3ntec.tohid.ui.screens.StaffScopeSection
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope

/**
 *  بخش‌های «مجوزِ کارمندان و دستگاه‌ها» (STF) زیرِ کدهای پیوستن — همان بخش‌های صفحهٔ کارمندانِ پیشین، با همان
 *  `ShopRepository` و پرچم‌های سرور (هر کدام بی پرچمِ روشنِ خودش هیچ نمی‌کشد)؛ ویرایش فقط برای صاحب.
 */
@Composable
internal fun RouteScope.TeamStaffSections(shops: ShopRepository, isOwner: Boolean) {
  ApproverSetupSection(shops, snackbar = snackbar)
  //  STF-01: نقش‌های سفارشی (پرچمِ `shop.custom_roles`؛ بی آن هیچ نمی‌کشد)
  CustomRolesSection(shops, isOwner)
  MemberPermsSection(shops, canEdit = isOwner, snackbar = snackbar)
  StaffScopeSection(shops, canEdit = isOwner, snackbar = snackbar)
  DevicesSection(shops, canEdit = isOwner, snackbar = snackbar)
}
