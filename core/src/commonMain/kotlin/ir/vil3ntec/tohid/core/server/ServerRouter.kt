package ir.vil3ntec.tohid.core.server

/**
 *  چند نشانیِ **یک** سرور برای برنامه‌ای که درخواستش را هم‌گام می‌فرستد (خریدِ مشتری؛ پرچمِ خاموشِ همان برنامه).
 *  برنامهٔ دکان و حالتِ پیکِ آن همین کار را با `ServerSwitchAndroid` و همین قاعده‌های core می‌کنند.
 *
 *  • پروفایل همان `ServerProfile` ِ پیش‌فرض است: نشانیِ زمانِ ساخت اول، و نشانی‌هایی که خودِ سرورِ سنجاق‌شده اعلام
 *    کرد و هر کدام جدا همان `serverId` را داد ([refresh]، `ServerLearn`). کاربرِ این برنامه نشانی نمی‌افزاید.
 *  • [send]: نشانیِ همین حالا؛ **نرسیدن** (استثنا: مهلت، خطای شبکه) ⇒ نشانیِ بعدیِ همان سرور، فقط اگر همان
 *    `serverId` جواب بدهد. ⛔ پاسخِ HTTP (۴xx و ۵xx هم) استثنا نیست ⇒ هرگز جابه‌جایی. ⛔ درخواستی که شاید رسیده
 *    باشد فقط با اجازهٔ [send] (`again`) دوباره فرستاده می‌شود (مثلاً GET یا کلیدِ یکتایی) — ثبتِ سفارش دوبار نمی‌شود.
 *  • آخرین نشانیِ سالم به خاطر می‌ماند (حتی پس از بستنِ برنامه)؛ هر [ServerFailover.RECHECK_MS] نشانیِ برتر دوباره
 *    سنجیده می‌شود ([refreshIfDue]).
 *  • ⛔ نشست و توکن هرگز به نشانیِ سرورِ دیگر نمی‌رود: نشانیِ جایگزین فقط با همان `serverId`؛ نشانیِ زمانِ ساخت که
 *    ناگهان شناسهٔ دیگری بدهد ⇒ سنجاقِ تازه و **همهٔ** نشانی‌های آموختهٔ سرورِ پیشین دور ریخته می‌شوند.
 */
class ServerRouter(
  private val buildUrl: String,
  private val store: Store,
  /** پاسخِ `config/server` ِ یک نشانی با مهلتِ داده‌شده؛ `null` یعنی نرسید یا سرورِ این خانواده نیست */
  private val ask: (url: String, timeoutMs: Int) -> ServerInfo?,
  private val now: () -> Long,
  private val devBuild: Boolean = false,
) {

  /** مخزنِ کوچکِ همین دستگاه (کلید-مقدار) */
  interface Store {
    fun get(key: String): String?
    fun put(key: String, value: String?)
  }

  /** همه‌چیز نرسید ⇒ تا این‌قدر نشانی‌ها دوباره پرسیده نمی‌شوند (بی اینترنت، هر درخواست چند پرسشِ بیهوده نسازد) */
  private var failedAt: Long = Long.MIN_VALUE / 2

  fun profile(): ServerProfile = ServerProfiles.decode(store.get(K_PROFILE), buildUrl).default

  /** نشانیِ همین حالا — ⛔ هرگز به شبکه نمی‌رود */
  fun current(): String = currentOf(profile())

  private fun currentOf(p: ServerProfile): String = store.get(K_CHOSEN)?.takeIf { it in p.urls } ?: p.primary

  private fun remember(url: String) {
    if (store.get(K_CHOSEN) != url) store.put(K_CHOSEN, url)
  }

  private fun askOf(url: String): ServerInfo? = runCatching { ask(url, ServerFailover.timeoutOf(url)) }.getOrNull()

  /**
   *  یک درخواست. [call] با نشانیِ پایه صدا زده می‌شود و نرسیدن را با استثنا می‌گوید. [again]: آیا همین درخواست را
   *  می‌شود پس از این خطا روی نشانیِ دیگر دوباره فرستاد. پروفایلِ تک‌نشانی یا سنجاق‌نشده ⇒ دقیقاً همان یک بار.
   */
  fun <T> send(again: (Throwable) -> Boolean, call: (base: String) -> T): T {
    val p = profile()
    val first = currentOf(p)
    try {
      return call(first).also { remember(first) }
    } catch (e: Throwable) {
      if (p.urls.size < 2 || p.serverId == null) throw e
      val t = now()
      if (t - failedAt < COOLDOWN_MS) throw e
      var resend = again(e)
      for (u in ServerFailover.order(p.urls) - first) {
        if (ServerFailover.judge(p, ServerFailover.Probe(u, askOf(u))) != ServerFailover.Judgement.ACCEPT) continue
        //  همان سرور است ⇒ درخواست‌های بعدی همین‌جا می‌روند، حتی اگر این یکی دوباره فرستادنی نباشد
        remember(u)
        if (!resend) throw e
        try {
          return call(u)
        } catch (e2: Throwable) {
          resend = again(e2)
        }
      }
      failedAt = t
      throw e
    }
  }

  /** فقط وقتش رسیده باشد: روی نشانیِ برتر هر [ServerLearn.LEARN_MS]، روی جایگزین هر [ServerFailover.RECHECK_MS] */
  fun refreshIfDue() {
    val p = profile()
    val last = store.get(K_CHECKED_AT)?.toLongOrNull() ?: 0L
    val onBest = currentOf(p) == ServerFailover.order(p.urls).firstOrNull()
    val every = if (onBest && p.serverId != null) ServerLearn.LEARN_MS else ServerFailover.RECHECK_MS
    if (now() - last >= every) refresh()
  }

  /**
   *  سنجشِ پس‌زمینه (از رشتهٔ اصلی صدا نزنید): نشانیِ برتر، سنجاقِ نخستین بارِ نشانیِ زمانِ ساخت، و آموختنِ نشانی‌های
   *  تازه از خودِ سرور. هیچ نرسید ⇒ همه‌چیز همان می‌ماند.
   */
  fun refresh() {
    var p = profile()
    store.put(K_CHECKED_AT, now().toString())
    val pick = ServerFailover.choose(p, null, ::askOf) as? ServerFailover.Pick.Chosen ?: return
    val observed = pick.info.serverId
    if (pick.url == p.primary && observed != null && observed != p.serverId) {
      //  نشانیِ زمانِ ساخت شناسهٔ تازه داد: سرور جابه‌جا شده ⇒ نشانی‌های آموختهٔ سرورِ پیشین دیگر «همان سرور» نیستند
      p = p.copy(urls = listOf(p.primary), serverId = observed, certPins = emptyMap())
    }
    remember(pick.url)
    p = ServerLearn.learn(p, pick.url, pick.info, devBuild) { u -> askOf(u) }
    save(p)
  }

  private fun save(p: ServerProfile) {
    val before = profile()
    if (before.urls == p.urls && before.serverId == p.serverId) return
    store.put(K_PROFILE, ServerProfiles(ServerProfiles.DEFAULT_ID, listOf(p.copy(lastInfo = null))).encode())
    if (store.get(K_CHOSEN)?.let { it in p.urls } == false) store.put(K_CHOSEN, null)
  }

  companion object {
    const val COOLDOWN_MS: Long = 30_000L
    const val K_PROFILE: String = "server.route.profile"
    const val K_CHOSEN: String = "server.route.chosen"
    const val K_CHECKED_AT: String = "server.route.checked"
  }
}
