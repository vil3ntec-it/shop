package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.orders.OrderDraft
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 *  ترجیحِ جایگزینی (CHK-15) روی سیم — همان شکلی که `server/src/lib/orders.js` می‌خواند و برمی‌گرداند.
 */
class OrdersSubstitutionWireTest {
  private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject
  private val form = OrderDraft.Form(name = "احمد", lines = listOf(OrderDraft.LineInput("برنج", "1", "120")))
  private fun body(f: OrderDraft.Form) = (OrderDraft.build(f) as OrderDraft.Result.Ok).body

  @Test fun manual_order_sends_substitution_only_when_chosen() {
    assertFalse(OrdersRepository.createJson("app-k1", body(form)).containsKey("substitution"))
    val chosen = OrdersRepository.createJson("app-k2", body(form.copy(substitution = "best_match")))
    assertEquals("\"best_match\"", chosen["substitution"].toString())
  }

  @Test fun order_detail_and_config_read_substitution_and_old_server_defaults() {
    val o = ApiJson.decode<OrderDto>(obj("""{"id":"o1","substitution":"refund","lines":[
      {"lineNo":1,"name":"شیر","substitution":"specific","substituteProductId":"milk2","substituteName":"شیرِ کابل"},
      {"lineNo":2,"name":"موز","substitution":"refund"}]}"""))
    assertEquals("refund", o.substitution)
    assertEquals(listOf("شیرِ کابل", ""), o.lines.map { it.substituteName })
    //  سرورِ پیش از ۱۳۹ این کلیدها را ندارد ⇒ خالی، و فرمِ برنامه ترجیحِ کلی را نشان نمی‌دهد
    val old = ApiJson.decode<OrderDto>(obj("""{"id":"o2","lines":[{"lineNo":1,"name":"شیر"}]}"""))
    assertEquals("", old.substitution)
    assertEquals("", old.lines[0].substituteName)
    assertFalse(ApiJson.decode<OrdersConfigDto>(obj("""{"enabled":true}""")).substitution)
    assertEquals(true, ApiJson.decode<OrdersConfigDto>(obj("""{"enabled":true,"substitution":true}""")).substitution)
  }
}
