package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.market.Fulfillment
import ir.vil3ntec.tohid.core.market.PublicOrder
import ir.vil3ntec.tohid.core.market.PublicOrderEvent
import ir.vil3ntec.tohid.core.market.PublicProduct
import ir.vil3ntec.tohid.core.market.PublicShop
import ir.vil3ntec.tohid.core.market.StockBadge

/** نمونه‌های آزمونِ برنامهٔ خرید — شکلِ همان پاسخ‌های `server/src/routes/storefront.js` (دادهٔ ساختگی). */
internal object ShopperSamples {

  /** فرستندهٔ جعلی: هر درخواست را نگه می‌دارد و پاسخِ [reply] را می‌دهد */
  class FakeHttp(var reply: (ShopperRequest) -> ShopperResponse) : ShopperHttp {
    val seen = mutableListOf<ShopperRequest>()
    override fun send(request: ShopperRequest): ShopperResponse {
      seen += request
      return reply(request)
    }
  }

  fun ok(body: String, status: Int = 200, headers: Map<String, String> = emptyMap()) = ShopperResponse(status, body, headers)

  fun fail(status: Int, code: String, extra: String = "", headers: Map<String, String> = emptyMap()) =
    ShopperResponse(status, """{"error":{"code":"$code","message":"پیامِ سرور"$extra}}""", headers)

  const val STORE_JSON = """{
    "store": {
      "slug": "golestan", "title": "سوپرمارکت گلستان", "tagline": "کالای روزانه", "contactPhone": "0700000001",
      "accent": "#0f766e",
      "status": {"badge": "open_online", "connection": "confirmed", "orderAcceptance": "accepting", "localTime": "09:23"},
      "fulfillment": {"delivery": true, "pickup": true}, "deliveryFeeMinor": 5000, "minOrderMinor": 20000,
      "currency": "AFN", "payment": {"methods": ["cod"], "online": {"available": false}},
      "categories": ["خوراکی", "شوینده"], "media": {"logo": null, "header": null},
      "delivery": {"zoned": true, "allowAsap": true, "scheduling": {"delivery": false, "pickup": false},
        "zones": [{"id": "zone_a", "name": "کارتهٔ سه", "feeMinor": 3000, "minOrderMinor": 60000, "freeOverMinor": 100000, "smallBasketFeeMinor": 1500},
                  {"id": "zone_b", "name": "شهرِ نو", "feeMinor": 8000, "minOrderMinor": 40000, "freeOverMinor": null, "smallBasketFeeMinor": null}]},
      "somethingNew": {"x": 1}
    },
    "verification": {"available": true}, "market": {"available": false}, "features": {"chat": true}
  }"""

  fun shop(delivery: Boolean = true, pickup: Boolean = true, fee: Long = 5000, min: Long = 20000) =
    PublicShop(slug = "golestan", title = "سوپرمارکت گلستان", fulfillment = Fulfillment(delivery, pickup), deliveryFeeMinor = fee, minOrderMinor = min)

  val ZONE_A = DeliveryZone("zone_a", "کارتهٔ سه", feeMinor = 3000, minOrderMinor = 60000, freeOverMinor = 100000, smallBasketFeeMinor = 1500)
  val ZONE_B = DeliveryZone("zone_b", "شهرِ نو", feeMinor = 8000, minOrderMinor = 40000)

  fun page(shop: PublicShop = shop(), zones: List<DeliveryZone> = emptyList(), scheduling: Scheduling = Scheduling(), allowAsap: Boolean = true) =
    StorePage(shop, StoreDelivery(zoned = zones.isNotEmpty(), zones = zones, allowAsap = allowAsap, scheduling = scheduling), true, false)

  fun product(id: String, price: Long?, max: Long? = null, stock: String? = null, name: String = id) =
    PublicProduct(id = id, name = name, unit = "دانه", priceMinor = price, maxQtyMilli = max, stock = stock?.let { StockBadge(it) })

  fun line(id: String, price: Long, qty: Long) = CartLine(productId = id, name = id, priceMinor = price, qtyMilli = qty)

  fun order(status: String, fulfillment: String = "delivery", timeline: List<Pair<String, Long>> = emptyList(), paymentStatus: String = "") = PublicOrder(
    token = "tok", orderNo = 7, status = status, fulfillment = fulfillment, totalMinor = 25000, paymentStatus = paymentStatus,
    createdAt = 1000, updatedAt = 9000, timeline = timeline.map { PublicOrderEvent(it.first, it.second) },
  )

  const val ORDER_JSON = """{"token":"v1.abc","orderNo":12,"status":"new","statusReason":"","substitution":"",
    "fulfillment":"delivery","paymentMethod":"cod","paymentStatus":"unpaid","currency":"AFN","itemsCount":2,
    "lines":[{"productId":"rice","name":"برنج","unit":"کیلو","qtyMilli":2000,"unitPriceMinor":12050,"lineTotalMinor":24100,"note":"","substitution":"","substituteName":""}],
    "subtotalMinor":24100,"discountMinor":0,"deliveryFeeMinor":3000,"totalMinor":27100,"readyAt":null,"scheduledFor":null,
    "slotEndsAt":null,"zoneName":"کارتهٔ سه","smallBasketFeeMinor":0,"slotFeeMinor":0,"createdAt":1761800000000,
    "updatedAt":1761800000000,"canCancel":true,"timeline":[{"status":"new","at":1761800000000}]}"""
}
