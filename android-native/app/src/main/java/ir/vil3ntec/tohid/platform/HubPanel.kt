package ir.vil3ntec.tohid.platform

/**
 *  SYN-09 — درگاهِ صفحهٔ «هابِ شبکهٔ دکان» (کارتِ `HubLanCardAndroid.kt` در «دستگاه‌ها»). فقط کامپیوتر پیاده‌اش می‌کند
 *  (`desktop/…/hub/HubBoot.kt`، وقتی پرچمِ `app.multi_server` روشن است)؛ روی گوشی `null` ⇒ کارتِ کامپیوتر نیست.
 *
 *  ⛔ همهٔ این‌ها فقط در همین فرایند صدا زده می‌شوند — هیچ درِ شبکه‌ای برگهٔ جفت‌سازی نمی‌سازد؛ برگه و برداشتنِ
 *     دستگاه فقط برای صاحب/مدیر (`HubPair.mayPair`).
 */
object HubPanel {
  @Volatile var port: Port? = null

  /** کدهای دلیل — متنشان در `StrHub.why` */
  object Why {
    const val FLAG_OFF = "flag_off"
    const val NO_SERVER_ID = "no_server_id"
    const val NO_SHOP = "no_shop"
    const val NO_KEY = "no_key"
    const val PORT_BUSY = "port_busy"
    const val NOT_RUNNING = "not_running"
    const val ROLE = "role"
  }

  data class Device(
    val deviceId: String,
    val pairedAt: Long,
    val pairedBy: String,
    /** آخرین درخواستِ این دستگاه به هاب از وقتی هاب روشن شد (`null` ⇒ هنوز نه) */
    val lastSeenAt: Long?,
    val ip: String,
  )

  data class Pairing(val code: String, val qr: String, val expiresAt: Long)

  data class State(
    val running: Boolean,
    val address: String,
    val pin: String,
    val devices: List<Device>,
    val pending: Int,
    val forwarded: Long,
    val pairing: Pairing?,
    val discoverable: Boolean,
  ) {
    companion object {
      val OFF = State(false, "", "", emptyList(), 0, 0, null, false)
    }
  }

  interface Port {
    fun state(): State
    /** `null` ⇒ روشن شد؛ وگرنه کدِ [Why] */
    fun start(): String?
    fun stop()
    /** برگهٔ تازه (کد و QR) — `null` ⇒ ساخته شد؛ وگرنه کدِ [Why] */
    fun pair(): String?
    fun cancelPairing()
    fun unpair(deviceId: String)
  }

  /** «وصل است» یعنی در این چند دقیقه درخواستی داد */
  const val ONLINE_MS: Long = 3 * 60_000L

  fun online(d: Device, now: Long): Boolean = d.lastSeenAt != null && now - d.lastSeenAt in 0..ONLINE_MS
}
