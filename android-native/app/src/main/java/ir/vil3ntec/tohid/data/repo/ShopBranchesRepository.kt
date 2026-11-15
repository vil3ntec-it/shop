package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.branch.BranchBook
import ir.vil3ntec.tohid.core.branch.BranchTally
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 *  شعبه درونِ یک دکان، فازِ ۲: درهای `/api/shop/branches` (`server/src/routes/shop-branches.js`). خواندنِ پاسخ در
 *  `core/branch/BranchBook` و `BranchTally`؛ جدا از صفحه تا حالِ صفحه بی سرور آزموده شود.
 *  ⛔ پرچمِ `shop.branches` خاموش ⇒ سرور همان ۴۰۴ ِ مسیرِ ناشناخته ⇒ صفحه «روشن نیست» (`serverRefused`).
 *  نوشتن‌ها فقط صاحب و با احرازِ تازه (`reauth_required` ⇒ `ReauthGate` ِ `ApiClient`)؛ نام/بایگانی با POST
 *  (`HttpURLConnection` PATCH نمی‌فرستد).
 */
interface ShopBranchesSource {
  suspend fun book(): ApiResult<BranchBook.Answer>
  suspend fun create(name: String): ApiResult<Unit>
  /** [name] null ⇒ دست نخورد؛ [archived] null ⇒ دست نخورد */
  suspend fun update(id: String, name: String?, archived: Boolean?): ApiResult<Unit>
  suspend fun members(): ApiResult<BranchBook.Members>
  suspend fun setMember(memberId: String, branches: List<String>): ApiResult<BranchBook.Members>
  /** null ⇒ پاسخ بی `enabled` (روشن نیست) */
  suspend fun report(days: Int, todayIso: String): ApiResult<BranchTally.View?>
}

class ShopBranchesRepository(private val api: ApiClient) : ShopBranchesSource {
  override suspend fun book(): ApiResult<BranchBook.Answer> = result { BranchBook.of(api.get(BranchBook.PATH)) }

  override suspend fun create(name: String): ApiResult<Unit> = result {
    api.post(BranchBook.PATH, buildJsonObject { put("name", JsonPrimitive(name.trim())) })
    Unit
  }

  override suspend fun update(id: String, name: String?, archived: Boolean?): ApiResult<Unit> = result {
    api.post(BranchBook.branchPath(id), buildJsonObject {
      if (name != null) put("name", JsonPrimitive(name.trim()))
      if (archived != null) put("status", JsonPrimitive(if (archived) "archived" else "active"))
    })
    Unit
  }

  override suspend fun members(): ApiResult<BranchBook.Members> = result { BranchBook.membersOf(api.get(BranchBook.MEMBERS)) }

  override suspend fun setMember(memberId: String, branches: List<String>): ApiResult<BranchBook.Members> = result {
    val body = buildJsonObject { put("branches", JsonArray(branches.ifEmpty { listOf("") }.map { JsonPrimitive(it.ifEmpty { "main" }) })) }
    BranchBook.membersOf(api.put(BranchBook.memberPath(memberId), body))
  }

  override suspend fun report(days: Int, todayIso: String): ApiResult<BranchTally.View?> = result {
    BranchTally.of(api.get(BranchTally.path(days, todayIso)))
  }
}
