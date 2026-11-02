package ir.vil3ntec.tohid.platform

import android.content.Context
import ir.vil3ntec.tohid.core.hub.HubRelay
import ir.vil3ntec.tohid.core.hub.HubRelayPort
import kotlinx.serialization.json.JsonObject

/*
 *  جایگزینِ iOS ِ `platform/HubRelayAndroid.kt` (هابِ محلیِ SYN-09، پشتِ پرچمِ `app.multi_server`).
 *  روی آیفون آن پرچم خاموش است (‎ServerSwitchIos‎)، پس `of` همیشه null — همان «هاب نیست» ِ اندروید.
 */
internal class HubRelayAndroid private constructor() : HubRelayPort {
  override suspend fun relay(body: JsonObject): HubRelay.Outcome = HubRelay.Outcome()

  companion object {
    fun of(context: Context): HubRelayAndroid? = null
  }
}
