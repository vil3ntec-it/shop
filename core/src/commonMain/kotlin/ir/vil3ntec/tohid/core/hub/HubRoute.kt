package ir.vil3ntec.tohid.core.hub

import ir.vil3ntec.tohid.core.server.ServerAddress
import ir.vil3ntec.tohid.core.server.ServerProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 *  دستگاه کجا push کند (`docs/SYN-09-HUB-fa.md` §۱). ⛔ سرور همیشه اول است؛ هاب فقط وقتی سرور نمی‌رسد،
 *  و فقط هابی که **همان `serverId`** ِ پروفایلِ فعال را می‌گوید، در شبکهٔ دکان، با https و گواهیِ سنجاق‌شده.
 *  پرچمِ `app.multi_server` یا تنظیمِ هاب خاموش ⇒ همیشه `CLOUD` (همان امروز).
 */
object HubRoute {

  enum class Route { CLOUD, HUB }

  enum class Why { OFF, CLOUD_OK, NO_HUB, UNPINNED_SERVER, HUB_NOT_LAN, HUB_NOT_PINNED, HUB_UNREACHABLE, OTHER_SERVER, OK }

  data class Decision(val route: Route, val why: Why)

  /** پاسخِ `GET /hub/v1/hello` */
  data class HubInfo(val serverId: String?, val protocol: Int)

  const val PROTOCOL: Int = 1

  /** تنظیم‌های همین دستگاه در `tohid-servers` (همگام نمی‌شوند). روی کامپیوترِ هاب `hub.local` یعنی «هاب باش» */
  const val SETTING_ON: String = "hub.local"
  /** نشانی و سنجاقِ هابی که این دستگاه به آن وصل می‌شود — از جفت‌سازی (هنوز دستی) */
  const val SETTING_URL: String = "hub.url"
  const val SETTING_PIN: String = "hub.pin"

  private val json = Json { ignoreUnknownKeys = true }

  fun choose(
    enabled: Boolean,
    profile: ServerProfile?,
    cloudReachable: Boolean,
    hubUrl: String?,
    hubPin: String?,
    hub: HubInfo?,
  ): Decision {
    if (!enabled || profile == null) return Decision(Route.CLOUD, Why.OFF)
    if (cloudReachable) return Decision(Route.CLOUD, Why.CLOUD_OK)
    if (hubUrl.isNullOrBlank()) return Decision(Route.CLOUD, Why.NO_HUB)
    val sid = profile.serverId ?: return Decision(Route.CLOUD, Why.UNPINNED_SERVER)
    val parts = (ServerAddress.check(hubUrl, lanAllowed = true) as? ServerAddress.Verdict.Ok)?.parts
    if (parts == null || !parts.https || parts.kind != ServerAddress.HostKind.LAN) return Decision(Route.CLOUD, Why.HUB_NOT_LAN)
    if (ServerProfile.normalizeCertPin(hubPin) == null) return Decision(Route.CLOUD, Why.HUB_NOT_PINNED)
    if (hub == null || hub.protocol != PROTOCOL) return Decision(Route.CLOUD, Why.HUB_UNREACHABLE)
    if (hub.serverId != sid) return Decision(Route.CLOUD, Why.OTHER_SERVER)
    return Decision(Route.HUB, Why.OK)
  }

  fun parseHello(text: String?): HubInfo? {
    val o = text?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject ?: return null
    if ((o["ok"] as? JsonPrimitive)?.content != "true") return null
    val protocol = (o["hub"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return null
    val sid = (o["server_id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    return HubInfo(sid, protocol)
  }
}
