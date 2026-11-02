package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.crm.CustomerErasure
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.LateApology
import ir.vil3ntec.tohid.core.orders.OrderEvidence
import kotlinx.serialization.json.JsonObject

/*
 *  درهای سه قابلیتِ سروری که تا امروز صفحه نداشتند — جدا از صفحه تا حالِ هر کدام بی سرور آزموده شود:
 *  پروندهٔ مدرکِ اختلاف (RSK-08، `routes/order-evidence.js`)، پوزشِ خودکارِ تأخیر (NTF-10، `routes/order-late-apology.js`)
 *  و حذفِ دادهٔ مشتری (SEC-10، `routes/shop-customer-erasure.js`). منطق و خواندنِ پاسخ در `core/orders`، `core/crm`.
 */

interface OrderEvidenceSource {
  /** پرسشِ بی‌اثر (هیچ سفارشی، هیچ سابقه‌ای) — همیشه خطا برمی‌گرداند؛ کدش «روشن» را می‌گوید (`OrderEvidence.probeSays`) */
  suspend fun probe(): ApiResult<Unit>
  suspend fun file(orderId: String): ApiResult<OrderEvidence.File>
  suspend fun write(orderId: String, p: OrderEvidence.Pending): ApiResult<OrderEvidence.Dispute>
}

class OrderEvidenceRepository(private val api: ApiClient) : OrderEvidenceSource {

  override suspend fun probe(): ApiResult<Unit> = result { api.get(OrderEvidence.probePath); Unit }

  override suspend fun file(orderId: String): ApiResult<OrderEvidence.File> = result {
    val path = OrderEvidence.filePath(orderId) ?: throw ApiFailure.InvalidResponse("id")
    OrderEvidence.file(api.get(path)) ?: throw ApiFailure.InvalidResponse("order")
  }

  override suspend fun write(orderId: String, p: OrderEvidence.Pending): ApiResult<OrderEvidence.Dispute> = result {
    val path = when (p.draft.act) {
      OrderEvidence.Act.OPEN -> OrderEvidence.openPath(orderId)
      OrderEvidence.Act.NOTE -> OrderEvidence.notePath(orderId)
      OrderEvidence.Act.RESOLVE -> OrderEvidence.resolvePath(orderId)
    } ?: throw ApiFailure.InvalidResponse("id")
    val body = OrderEvidence.body(p.draft, p.key, p.disputeId) ?: throw ApiFailure.InvalidResponse("body")
    OrderEvidence.written(api.post(path, body)) ?: throw ApiFailure.InvalidResponse("dispute")
  }
}

interface LateApologySource {
  suspend fun load(): ApiResult<LateApology.View>
  suspend fun save(body: JsonObject): ApiResult<LateApology.Policy>
}

class LateApologyRepository(private val api: ApiClient) : LateApologySource {
  override suspend fun load(): ApiResult<LateApology.View> = result { LateApology.view(api.get(LateApology.PATH)) }
  override suspend fun save(body: JsonObject): ApiResult<LateApology.Policy> =
    result { LateApology.saved(api.put(LateApology.PATH, body)) ?: throw ApiFailure.InvalidResponse("policy") }
}

interface CustomerErasureSource {
  /** پرسشِ بی‌اثر (`dryRun` بی هدف) — همیشه خطا؛ کدش «روشن و صاحب» را می‌گوید (`CustomerErasure.probeSays`) */
  suspend fun probe(): ApiResult<Unit>
  suspend fun erase(phone: String, dryRun: Boolean): ApiResult<CustomerErasure.Result>
}

class CustomerErasureRepository(private val api: ApiClient) : CustomerErasureSource {
  override suspend fun probe(): ApiResult<Unit> = result { api.post(CustomerErasure.PATH, CustomerErasure.probe()); Unit }
  override suspend fun erase(phone: String, dryRun: Boolean): ApiResult<CustomerErasure.Result> = result {
    CustomerErasure.result(api.post(CustomerErasure.PATH, CustomerErasure.body(phone, dryRun))) ?: throw ApiFailure.InvalidResponse("counts")
  }
}
