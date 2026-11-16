package ir.vil3ntec.tohid.ui.desk

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.data.ShopRole

/**
 *  تنها جای `Context` ِ پوستهٔ کامپیوتر (مرزِ `*Android.kt` — منطقِ `ui/desk` بی وابستگیِ پلتفرمی، راهِ آیفون): نقشِ کاربر برای منو
 *  و «همهٔ بخش‌ها»، و تازه کردنِ پیکربندیِ گفت‌وگو و پرچم‌های ماژول.
 */
internal data class DeskRole(
  /** صاحب/مدیر — تنظیمات، تخفیف‌ها و کارمندان (`ShopRole.canOpenSettings`) */
  val canAdmin: Boolean,
  /** صاحبِ دکان — برگهٔ «خاموش» نامِ پرچم و «پیام به پشتیبانی» دارد */
  val owner: Boolean,
)

/** نقشِ همین لحظه — هر بار خوانده می‌شود (ورود/خروج و عوض شدنِ نقش بی راه‌اندازیِ دوباره دیده شود)، مثلِ منوی پیشین */
@Composable
internal fun rememberDeskRole(): DeskRole {
  val context = LocalContext.current
  return DeskRole(ShopRole.canOpenSettings(context), ShopRole.current(context) == ShopRole.OWNER)
}

/** «همهٔ بخش‌ها»: پیکربندیِ گفت‌وگو و پرچم‌های ماژول یک بار تازه می‌شوند — همان کارِ «بیشتر» ِ گوشی */
@Composable
internal fun DeskMoreRefresh() {
  val context = LocalContext.current
  LaunchedEffect(Unit) {
    ir.vil3ntec.tohid.data.ShopChatSync.refreshConfig(context)
    ir.vil3ntec.tohid.ui.v3.nav.ModuleFlags.load(context)
  }
}
