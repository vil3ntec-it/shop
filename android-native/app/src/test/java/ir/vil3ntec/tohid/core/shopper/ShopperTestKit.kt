package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.net.TokenStorage

/** حافظهٔ ساده به جای SharedPreferences */
class MemoryStorage : ShopperStorage {
  val map = HashMap<String, String>()
  override fun read(key: String): String? = map[key]
  override fun write(key: String, value: String?) {
    if (value == null) map.remove(key) else map[key] = value
  }
}

/** ساعتِ دستی */
class Clock(var at: Long = 1_760_000_000_000L) {
  val now: () -> Long = { at }
}

/** بی توکنِ حساب — خریدار حسابِ دکان ندارد */
object NoTokens : TokenStorage {
  override var accessToken: String? = null
  override var refreshToken: String? = null
  override var accessExpiresAt: Long = 0
  override fun save(access: String?, refresh: String?, expiresAt: Long) = Unit
  override fun clear() = Unit
}

fun clientOf(server: TestHttpServer): ApiClient =
  ApiClient(HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true), NoTokens)

/** مسیرِ سرورِ آزمایشی با پیشوندِ نسخه‌دار */
fun TestHttpServer.at(path: String, handler: (TestHttpServer.Request) -> Pair<Int, String>) = on("/api/v1$path", handler)

fun ok(json: String): Pair<Int, String> = 200 to json

fun err(status: Int, code: String, message: String = "خطا", extra: String = ""): Pair<Int, String> =
  status to """{"error":{"code":"$code","message":"$message"${if (extra.isEmpty()) "" else ",$extra"}}}"""

/*
 *  پاسخ‌ها به همان شکلِ سرور (`cardOf`، `profile`، `productView`، `publicOrder`) —
 *  اگر سرور شکل را عوض کند، این‌ها باید با آن عوض شوند.
 */
object Fixtures {
  const val STATUS_OPEN = """{"badge":"open_online","mode":"auto","declaredUntil":null,"connection":"confirmed",
    "lastConfirmedAt":1760000000000,"orderAcceptance":"accepting","timezone":"Asia/Kabul","localTime":"10:30",
    "hoursSet":true,"holiday":false,"today":[[480,1260]],"notice":""}"""

  fun card(slug: String, title: String = "دکان $slug", extra: String = "") = """{"slug":"$slug","title":"$title",
    "tagline":"تازه","accent":"#108B81","category":"grocery","categoryLabel":"سوپرمارکت و خواربار","area":"کارته سه",
    "status":$STATUS_OPEN,"fulfillment":{"delivery":true,"pickup":true},"deliveryFeeMinor":5000,"minOrderMinor":20000,
    "currency":"AFN","productCount":42,"distanceKm":null$extra}"""

  fun product(id: String, price: Long?, extra: String = "") = """{"id":"$id","name":"کالا $id","unit":"عدد",
    "category":"لبنیات","priceMinor":${price ?: "null"},"maxQtyMilli":null,"photo":true,
    "stock":{"status":"unverified","checkedAt":null}$extra}"""

  fun profile(delivery: String = """{"zoned":false,"zones":[],"allowAsap":true,"scheduling":{"delivery":false,"pickup":false}}""") =
    """{"store":{"slug":"nan","title":"نانوایی","tagline":"","contactPhone":"+93700000000","accent":"#108B81",
    "status":$STATUS_OPEN,"fulfillment":{"delivery":true,"pickup":true},"deliveryFeeMinor":5000,"minOrderMinor":10000,
    "currency":"AFN","payment":{"methods":["cod"],"online":{"available":false,"decision":"D-08"}},
    "categories":["نان","شیرینی"],"delivery":$delivery},"verification":{"available":true},"market":{"available":true}}"""

  const val TOKEN = "01JABCDEFGHJKMNPQRSTVWXYZ0.abcdefghijklmnopqrstuvwxyzABCDEF"

  fun order(status: String = "new", timeline: String = """[{"status":"new","at":1000}]""", fulfillment: String = "delivery") =
    """{"token":"$TOKEN","orderNo":17,"status":"$status","statusReason":"","fulfillment":"$fulfillment",
    "paymentMethod":"cod","paymentStatus":"unpaid","currency":"AFN","itemsCount":1,
    "lines":[{"productId":"p1","name":"نان","unit":"عدد","qtyMilli":2000,"unitPriceMinor":1500,"lineTotalMinor":3000,"note":""}],
    "subtotalMinor":3000,"discountMinor":0,"deliveryFeeMinor":5000,"totalMinor":8000,"readyAt":null,
    "scheduledFor":null,"slotEndsAt":null,"zoneName":"","createdAt":1000,"updatedAt":2000,
    "canCancel":${status == "new"},"timeline":$timeline}"""
}
