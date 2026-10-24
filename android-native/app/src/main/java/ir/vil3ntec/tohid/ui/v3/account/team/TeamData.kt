package ir.vil3ntec.tohid.ui.v3.account.team

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.model.MemberDto
import ir.vil3ntec.tohid.core.model.ShopDeviceDto
import ir.vil3ntec.tohid.core.model.ShopPermsDto
import ir.vil3ntec.tohid.core.model.StaffCodeDto
import ir.vil3ntec.tohid.data.repo.ShopRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 *  دادهٔ کارمندان که چند مسیر با هم می‌خوانند (`team`، `team/members/{id}`، برگه‌های
 *  ساخت/نمایشِ کد، برداشتن و از کار انداختن) — یک بار از سرور، همان فراخوان‌های
 *  صفحهٔ پیشین (`ShopRepository`)، و پس از هر کار دوباره.
 *
 *  `fresh` کدِ تازه است که **فقط یک بار** دیده می‌شود: هرگز در نشانیِ مسیر یا
 *  حالتِ ذخیره‌شده نمی‌رود، فقط در حافظه تا «بستن».
 */
@Stable
internal class TeamData {
  var loaded by mutableStateOf(false)
  var members by mutableStateOf<List<MemberDto>>(emptyList())
  var maxMembers by mutableIntStateOf(0)
  var codes by mutableStateOf<List<StaffCodeDto>>(emptyList())
  /** کدِ ثابتِ دکان — همیشه همان است، پس هر بار نشان داده می‌شود */
  var standing by mutableStateOf("")
  /** STF-11: صاحب دکان کدِ ثابت را خاموش کرده */
  var standingOff by mutableStateOf(false)
  /** «دعوتِ پیک» (DLV-01) فقط وقتی سرور تحویل را برای این دکان روشن کرده */
  var courierAllowed by mutableStateOf(false)
  var perms by mutableStateOf<ShopPermsDto?>(null)
  var devices by mutableStateOf<List<ShopDeviceDto>?>(null)
  var error by mutableStateOf<String?>(null)
  var fresh by mutableStateOf<String?>(null)

  fun member(id: String): MemberDto? = members.firstOrNull { it.id == id }

  suspend fun reload(shops: ShopRepository, isOwner: Boolean) {
    shops.members()
      .onSuccess { page -> members = page.members; maxMembers = page.maxMembers; error = null }
      .onFailure { error = it.userMessage }
    shops.staffCodes().onSuccess { codes = it }
    //  کدِ ثابت جدا خوانده می‌شود؛ سرورِ قدیمی این مسیر را ندارد و نبودنش بقیه را خراب نمی‌کند
    shops.standingState().let { (code, off) -> standing = code; standingOff = off }
    courierAllowed = isOwner && shops.courierInviteAllowed()
    shops.permissions().onSuccess { perms = it.shop }
    shops.devices().onSuccess { devices = it }
    loaded = true
  }
}

internal object TeamSession {
  val data = TeamData()

  /**
   *  کارِ برگه‌های تأیید (برداشتن، از کار انداختن) پس از بسته شدنِ برگه ادامه دارد؛
   *  دامنهٔ خودِ برگه با بسته شدنش لغو می‌شد و درخواست نیمه‌کاره می‌ماند.
   */
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
