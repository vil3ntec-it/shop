package ir.vil3ntec.tohid.ui.v3.settings.sections

import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrSettings

/** سنجشِ درجای فرمِ «رمزِ حساب» — خالص؛ خودِ تغییر همان `auth.changePassword` است */
internal object PasswordModel {
  /** همان مرزِ امروز: رمزِ تازه دستِ‌کم هشت نویسه */
  const val MIN = 8

  data class Check(val freshError: String?, val repeatError: String?, val ready: Boolean)

  fun check(current: String, fresh: String, repeat: String): Check {
    val freshError = if (fresh.isNotEmpty() && fresh.length < MIN) StrSettings.pwdMin8 else null
    val repeatError = if (repeat.isNotEmpty() && repeat != fresh) Str.passwordsDiffer else null
    return Check(freshError, repeatError, ready = current.isNotEmpty() && fresh.length >= MIN && repeat == fresh)
  }
}
