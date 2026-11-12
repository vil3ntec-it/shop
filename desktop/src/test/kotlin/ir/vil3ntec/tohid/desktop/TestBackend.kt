package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.net.TokenStorage
import ir.vil3ntec.tohid.data.repo.Backend

/**
 *  `Backend.api(context)` ِ صفحه‌ها ⇐ یک سرورِ ساختگیِ 127.0.0.1 (`TestHttpServer`) — فقط در طولِ یک آزمون.
 *
 *  صفحه‌هایی که خودشان `Backend.api(LocalContext.current)` را صدا می‌زنند (مسیرهای نسخهٔ ۳، «انتخاب از بانک»، کارتِ ورود و
 *  خروج) مخزنِ تزریقی ندارند؛ این ابزار همان سیم‌کشیِ واقعی (`ApiClient` ⇐ `HttpEngine`) را به سرورِ ساختگی وصل می‌کند و
 *  با [close] سیم‌کشیِ پیشین را برمی‌گرداند. `online` قابلِ عوض کردن است (بی شبکه ⇒ `ApiFailure.Offline` پیش از هر درخواست).
 */
class TestBackend : AutoCloseable {
  val server = TestHttpServer()
  @Volatile var online = true

  /** نشستِ باز در حافظه — همان قراردادِ `TokenStorage` */
  private class Session : TokenStorage {
    override var accessToken: String? = "test-session"
    override var refreshToken: String? = null
    override var accessExpiresAt: Long = Long.MAX_VALUE / 2
    override var staffDeviceKey: String? = null
    override fun save(access: String?, refresh: String?, expiresAt: Long) { accessToken = access }
    override fun clear() { accessToken = null }
  }

  private val field = Backend::class.java.getDeclaredField("wiring").apply { isAccessible = true }
  private val before: Any? = field.get(null)
  val api = ApiClient(HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true, online = { online }), Session())

  init {
    val cls = Class.forName("ir.vil3ntec.tohid.data.repo.Backend\$Wiring")
    val wiring = cls.getDeclaredConstructor(android.content.Context::class.java).apply { isAccessible = true }.newInstance(DesktopContext)
    cls.getDeclaredField("api").apply { isAccessible = true }.set(wiring, api)
    field.set(null, wiring)
  }

  override fun close() {
    field.set(null, before)
    server.close()
  }
}
