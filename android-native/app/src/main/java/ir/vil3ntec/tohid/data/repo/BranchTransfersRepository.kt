package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.stock.BranchStock
import ir.vil3ntec.tohid.core.stock.BranchTransfer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 *  INV-11/S21 (فازِ ۴ ِ شعبه‌ها، `docs/BRANCHES-fa.md` §۱۰): درهای `/api/shop/catalog/branch-transfers` (`server/src/routes/
 *  branch-transfers.js`). شکلِ هر درخواست و خواندنِ پاسخ در `core/stock/BranchTransfer`؛ این‌جا فقط فرستادن.
 *  ⛔ شعبهٔ کننده همان سرآیندِ `X-Branch-Id` ِ هر درخواستِ حساب‌دار است (`Backend` ⇐ `BranchDesk.headers` ِ میز)، نه سرآیندِ
 *     `Call` — هر دو یکی‌اند چون صفحه همیشه با شعبهٔ میز می‌سازد. کلیدِ یکتایی از `Call` (`postKeyed`/`putKeyed`).
 *  ⛔ پرچمِ `shop.branches` یا `shop.catalog` خاموش ⇒ سرور همان ۴۰۴ ِ مسیرِ ناشناخته ⇒ صفحه «روشن نیست» (`serverRefused`).
 */
interface BranchTransfersSource {
  /** همهٔ انتقال‌های همین شعبه (هر دو سو، تازه‌ترین اول، تا ۱۰۰) */
  suspend fun list(): ApiResult<List<BranchTransfer.Transfer>>
  suspend fun destinations(): ApiResult<List<BranchTransfer.End>>
  suspend fun detail(id: String): ApiResult<Pair<BranchTransfer.Transfer, List<BranchTransfer.Step>>>
  /** یک گامِ آماده (`BranchTransfer.Result.Ok.call`) ⇒ انتقالِ پس از گام */
  suspend fun step(call: BranchTransfer.Call): ApiResult<BranchTransfer.Transfer>
  /** موجودیِ سرور روشن و این عضو `stock.move` دارد؟ (`/shop/catalog/config` ِ همین شعبه؛ فقط برای نشان دادنِ «انتقالِ تازه») */
  suspend fun canMove(): ApiResult<Boolean> = ApiResult.Success(false)
}

/** کلیدهای شعبه‌ایِ دکان و مجوزِ هر عضو — فقط صاحب (`/api/shop/branches/settings`، `/members/:id/perms`) */
interface BranchRulesSource {
  suspend fun settings(): ApiResult<BranchStock.Settings>
  suspend fun saveSettings(body: JsonObject): ApiResult<BranchStock.Settings>
  suspend fun grant(memberId: String, on: Boolean): ApiResult<Unit>
}

class BranchTransfersRepository(private val api: ApiClient) : BranchTransfersSource, BranchRulesSource {

  override suspend fun list(): ApiResult<List<BranchTransfer.Transfer>> = result {
    BranchTransfer.listOf(api.get("${BranchTransfer.PATH}?limit=100"))
  }

  override suspend fun destinations(): ApiResult<List<BranchTransfer.End>> = result {
    BranchTransfer.destinationsOf(api.get(BranchTransfer.destinations(BranchTransfer.MAIN).path))
  }

  override suspend fun detail(id: String): ApiResult<Pair<BranchTransfer.Transfer, List<BranchTransfer.Step>>> = result {
    BranchTransfer.detailOf(api.get(BranchTransfer.detail(BranchTransfer.MAIN, id).path)) ?: throw ApiFailure.InvalidResponse()
  }

  override suspend fun step(call: BranchTransfer.Call): ApiResult<BranchTransfer.Transfer> = result {
    val key = call.headers[BranchTransfer.HEADER_KEY] ?: throw ApiFailure.InvalidResponse()
    val body = call.body ?: buildJsonObject { }
    val out = if (call.method == "PUT") api.putKeyed(call.path, body, key) else api.postKeyed(call.path, body, key)
    BranchTransfer.detailOf(out)?.first ?: throw ApiFailure.InvalidResponse()
  }

  override suspend fun canMove(): ApiResult<Boolean> = result {
    val c = api.get("/shop/catalog/config")
    val on = (c["enabled"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
    val move = ((c["permissions"] as? JsonObject)?.get("move") as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
    on && move
  }

  override suspend fun settings(): ApiResult<BranchStock.Settings> = result { BranchStock.settingsOf(api.get(BranchStock.SETTINGS_PATH)) }

  override suspend fun saveSettings(body: JsonObject): ApiResult<BranchStock.Settings> = result {
    BranchStock.settingsOf(api.put(BranchStock.SETTINGS_PATH, body))
  }

  override suspend fun grant(memberId: String, on: Boolean): ApiResult<Unit> = result {
    api.put(BranchStock.grantPath(memberId), BranchStock.grantBody(on))
    Unit
  }
}
