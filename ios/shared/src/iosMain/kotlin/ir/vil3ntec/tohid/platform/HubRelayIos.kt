package ir.vil3ntec.tohid.platform

import android.content.Context
import ir.vil3ntec.tohid.core.hub.HubRelay
import ir.vil3ntec.tohid.core.hub.HubRelayPort
import kotlinx.serialization.json.JsonObject

/*
 *  جایگزینِ iOS ِ `platform/HubRelayAndroid.kt` (هابِ محلیِ SYN-09، پشتِ پرچمِ `app.multi_server`).
 *  «سرورِ خودِ دکان» از گامِ ۴ روی آیفون هست (‎platform/ServerSwitchAndroid.kt‎ ِ خودِ اندروید)، ولی هابِ محلی هنوز نه:
 *  `of` همیشه null — همان «هاب نیست» ِ اندروید، و درخواست مستقیم به سرورِ فعال می‌رود.
 */
internal class HubRelayAndroid private constructor() : HubRelayPort {
  override suspend fun relay(body: JsonObject): HubRelay.Outcome = HubRelay.Outcome()

  companion object {
    fun of(context: Context): HubRelayAndroid? = null
  }
}
