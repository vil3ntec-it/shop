package ir.vil3ntec.tohid.platform

import android.content.Context
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.hub.HubRoute
import ir.vil3ntec.tohid.core.server.ServerAddress
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.v1.SyncOp

/**
 *  S29 / SYN-09 — گوشی (یا کامپیوترِ دیگرِ دکان) ⇄ هابِ کامپیوتر برای همگام‌سازیِ v1 (`HubPeerLink`، `HubExchangeV1`).
 *
 *  ⛔ پیش‌فرض خاموش، دو قفل: پرچمِ `app.multi_server` **و** تنظیمِ همین دستگاه `hub.peer` (فقط جفت‌سازیِ موفق روشنش
 *     می‌کند؛ «جدا کردن» خاموش). هر کدام خاموش ⇒ [of] `null` ⇒ موتورِ v1 همان امروز (مستقیم با سرور).
 *  ⛔ هاب فقط برای **همان سرور**: `serverId` ِ پروفایلِ فعال باید همانی باشد که هاب هنگامِ جفت شدن گفت.
 *  کلیدِ جفت‌شده در `HubKeyStore` (همان روشِ `TokenStore`)؛ نشانی، سنجاق و cursor ِ هاب باز (عمومی‌اند).
 */
class HubLinkV1Android private constructor(private val app: Context, private val link: HubPeerLink) {

  private val store = object : HubExchangeV1.Store {
    private val p get() = state(app)
    override var since: Long
      get() = p.getLong(SINCE, 0)
      set(v) { p.edit().putLong(SINCE, v).apply() }
    override var epoch: String
      get() = p.getString(EPOCH, "").orEmpty()
      set(v) { p.edit().putString(EPOCH, v).apply() }
    override fun seenFromCloud(ids: Collection<String>): Set<String> = ring(app).let { r -> ids.filterTo(HashSet()) { it in r } }
    override fun sawFromCloud(ids: Collection<String>) = saw(app, ids)
  }

  /** یک دورِ «سرور نرسید» — صدازننده `SyncV1Engine` است (زیرِ `gate`) */
  fun exchange(
    deviceId: String, queued: List<SyncOp>, caps: (List<SyncOp>) -> Boolean, schema: Int, since: Long?,
    cloudCursor: Long, absorb: (List<SyncOp>) -> Unit,
  ): HubExchangeV1.Outcome {
    val out = HubExchangeV1(link, store).exchange(deviceId, queued, queued.size, caps, schema, since, cloudCursor, absorb)
    //  هاب این دستگاه را دیگر نمی‌شناسد (صاحب «جدا» زد) ⇒ تنظیم خاموش، همان راهِ امروز
    if (out.refused == "hub_not_paired") forget(app)
    return out
  }

  companion object {
    /** همان `PREFS` ِ `ServerSwitchAndroid` */
    private const val SERVER_PREFS = "tohid-servers"
    private const val STATE_PREFS = "tohid-hub-link"
    const val SETTING_PEER = "hub.peer"
    const val SETTING_SID = "hub.sid"
    private const val SINCE = "since"
    private const val EPOCH = "epoch"
    private const val RING = "seen"
    /** `op_id`هایی که از سرور رسیدند — پشتیبانِ پالایشِ `server_seq` ِ خودِ هاب */
    const val RING_MAX = 2_000

    private fun servers(c: Context) = c.applicationContext.getSharedPreferences(SERVER_PREFS, Context.MODE_PRIVATE)
    private fun state(c: Context) = c.applicationContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)

    /** همهٔ شرط‌ها بی شبکه؛ هر کدام نه ⇒ `null` */
    fun of(context: Context, deviceId: String): HubLinkV1Android? {
      val app = context.applicationContext
      if (!ServerSwitchAndroid.enabled(app)) return null
      val s = servers(app)
      if (!s.getBoolean(SETTING_PEER, false)) return null
      val url = s.getString(HubRoute.SETTING_URL, null) ?: return null
      val pin = s.getString(HubRoute.SETTING_PIN, null) ?: return null
      val sid = s.getString(SETTING_SID, null) ?: return null
      val key = HubKeyStore.get(app) ?: return null
      if (ServerSwitchAndroid.activeProfile(app)?.serverId != sid) return null
      val host = (ServerAddress.check(url, lanAllowed = true) as? ServerAddress.Verdict.Ok)?.parts?.host ?: return null
      //  HW-13: بی اجازهٔ «شبکهٔ محلی» ِ اندروید ۱۷ همان دم نه (همان قاعدهٔ `HubRelayAndroid`)
      if (ir.vil3ntec.tohid.hw.LocalNetworkAccess.blocked(app, host)) return null
      return HubLinkV1Android(app, HubPeerLink(url, pin, key, sid, { identity(app, deviceId) }))
    }

    /** همان سرآیندهای سرور؛ `X-Device` همان `device_id` ِ v1 ِ همین حساب (`SyncV1Engine.deviceId`) */
    private fun identity(app: Context, deviceId: String): Map<String, String> = mapOf(
      "Authorization" to (Backend.tokens(app).accessToken?.let { "Bearer $it" } ?: ""),
      "X-Device" to deviceId,
      "X-App" to AppConfig.appId,
      "X-App-Version" to AppConfig.appVersion,
    )

    /** op ِ رسیده از سرور ⇒ حلقهٔ «دیده از سرور» (فقط وقتی جفت شده؛ وگرنه هیچ) */
    fun saw(context: Context, ids: Collection<String>) {
      if (ids.isEmpty()) return
      val app = context.applicationContext
      if (!servers(app).getBoolean(SETTING_PEER, false)) return
      val r = LinkedHashSet(ring(app))
      r.addAll(ids)
      val keep = r.toList().takeLast(RING_MAX)
      state(app).edit().putString(RING, keep.joinToString(",")).apply()
    }

    private fun ring(app: Context): Set<String> =
      state(app).getString(RING, "").orEmpty().split(',').filterTo(HashSet()) { it.isNotEmpty() }

    /** جفت شدنِ موفق ⇒ نشانی، سنجاق، سرور و کلید؛ تنظیمِ `hub.peer` روشن */
    fun remember(context: Context, p: HubPairClient.Paired) {
      val app = context.applicationContext
      HubKeyStore.put(app, p.key)
      servers(app).edit()
        .putString(HubRoute.SETTING_URL, p.url).putString(HubRoute.SETTING_PIN, p.pin).putString(SETTING_SID, p.serverId)
        .putBoolean(SETTING_PEER, true).apply()
      state(app).edit().putLong(SINCE, 0).putString(EPOCH, "").apply()
    }

    /** «جدا کردن» ⇒ کلید پاک، تنظیم خاموش؛ موتور همان راهِ مستقیم */
    fun forget(context: Context) {
      val app = context.applicationContext
      HubKeyStore.put(app, null)
      servers(app).edit().putBoolean(SETTING_PEER, false).apply()
      state(app).edit().clear().apply()
    }

    fun paired(context: Context): Boolean = servers(context.applicationContext).getBoolean(SETTING_PEER, false)

    fun pairedUrl(context: Context): String = servers(context.applicationContext).getString(HubRoute.SETTING_URL, "").orEmpty()

    /** سرآیندهای سرورِ همین دستگاه — برای جفت‌سازی (توکن فقط روی گواهیِ سنجاق‌شده می‌رود) */
    fun identityOf(context: Context): Map<String, String> =
      identity(context.applicationContext, ir.vil3ntec.tohid.sync.v1.SyncV1Engine.of(context).deviceId())
  }
}
