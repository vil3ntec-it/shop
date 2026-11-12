package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.shopper.ShopperFunnel.Step
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.FakeHttp
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.ok
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  RPT-14 — تپشِ بالای قیف از برنامهٔ خرید ([ShopperFunnel]): همان `POST /api/store/:slug/funnel/:step` ِ ویترینِ وب
 *  (`store.js` ‹`beat`›)، فقط با `features.funnel`، یک بار برای هر گام در هر بازکردنِ دکان؛ گامِ پیش از رسیدنِ دکان
 *  گم نمی‌شود و با رسیدنش می‌رود.
 */
class ShopperFunnelTest {

  private fun page(funnel: Boolean) = ShopperSamples.page().let {
    StorePage(it.shop, it.delivery, it.verificationAvailable, it.marketAvailable, StoreFeatures(funnel = funnel))
  }

  @Test fun featuresFunnelIsReadFromStoreResponse() {
    val on = FakeHttp { ok(ShopperSamples.STORE_JSON.replace("\"features\": {\"chat\": true}", "\"features\": {\"chat\": true, \"funnel\": true}")) }
    assertTrue(ShopperApi(on).store("golestan").valueOrNull()!!.features.funnel)
    val off = FakeHttp { ok(ShopperSamples.STORE_JSON) }
    assertFalse(ShopperApi(off).store("golestan").valueOrNull()!!.features.funnel, "پرچمِ خاموش ⇒ کلید نیست ⇒ بی تپش")
  }

  @Test fun beatIsBodylessPostToTheSameDoorAsTheWebAndEmpty204IsOk() {
    val http = FakeHttp { ok("", status = 204) }
    val api = ShopperApi(http)
    assertTrue(api.funnel("golestan", Step.VISIT) is ShopperResult.Ok)
    assertTrue(api.funnel("گلستان", Step.CART) is ShopperResult.Ok)
    assertEquals(listOf("POST", "POST"), http.seen.map { it.method })
    assertEquals("/api/store/golestan/funnel/visit", http.seen[0].path)
    assertEquals("/api/store/" + ShopperUrl.encode("گلستان") + "/funnel/cart", http.seen[1].path)
    assertNull(http.seen[0].body)
    //  پرچمِ خاموش (۴۰۴ ِ مسیرِ ناشناخته) یا شبکه ⇒ شکست، بی پرتاب
    http.reply = { ShopperSamples.fail(404, "not_found") }
    assertTrue(api.funnel("golestan", Step.VIEW) is ShopperResult.Fail)
    http.reply = { throw IllegalStateException("offline") }
    assertTrue(api.funnel("golestan", Step.VIEW) is ShopperResult.Fail)
  }

  @Test fun eachStepOncePerShopOpeningAndOnlyWithTheFlag() {
    val f = ShopperFunnel()
    assertEquals(emptyList(), f.ready("golestan", page(funnel = false)), "پرچمِ خاموش ⇒ هیچ تپش")
    assertFalse(f.take("golestan", page(funnel = false), Step.VIEW))

    assertEquals(listOf(Step.VISIT), f.ready("golestan", page(funnel = true)))
    assertTrue(f.take("golestan", page(funnel = true), Step.VIEW))
    assertFalse(f.take("golestan", page(funnel = true), Step.VIEW), "کالای دوم همان بازکردن ⇒ همان یک تپش")
    assertTrue(f.take("golestan", page(funnel = true), Step.CART))
    assertFalse(f.take("golestan", page(funnel = true), Step.CART))
    //  دکانِ دیگر جدا می‌شمارد و نشانِ این دکان را پاک نمی‌کند
    assertEquals(listOf(Step.VISIT), f.ready("herat", page(funnel = true)))
    assertFalse(f.take("golestan", page(funnel = true), Step.CART))
    assertTrue(f.take("herat", page(funnel = true), Step.CART))
    //  بازکردنِ دوباره (همان «بارِ تازهٔ صفحه» ِ وب) ⇒ دوباره؛ سرور خودش هر روز یک بار می‌شمارد
    assertEquals(listOf(Step.VISIT), f.ready("golestan", page(funnel = true)))
    assertTrue(f.take("golestan", page(funnel = true), Step.CART))
    assertFalse(f.take("herat", page(funnel = true), Step.CART), "بازکردنِ دکانِ دیگر نشانِ این یکی را پاک نکرد")
  }

  @Test fun stepBeforeTheShopArrivesWaitsAndGoesWithTheVisit() {
    val f = ShopperFunnel()
    //  پیوندِ مستقیمِ کالا: کالا پیش از دکان رسید
    assertFalse(f.take("golestan", null, Step.VIEW))
    assertFalse(f.take("golestan", null, Step.VIEW))
    assertEquals(listOf(Step.VISIT, Step.VIEW), f.ready("golestan", page(funnel = true)))
    assertFalse(f.take("golestan", page(funnel = true), Step.VIEW))
    //  منتظر با پرچمِ خاموش دور ریخته می‌شود
    assertFalse(f.take("herat", null, Step.CART))
    assertEquals(emptyList(), f.ready("herat", page(funnel = false)))
    assertFalse(f.take("herat", page(funnel = false), Step.CART))
    assertEquals(emptyList(), f.ready("herat", page(funnel = false)), "منتظرِ دور ریخته برنگشت")
    //  منتظرِ دکانِ دیگر با این دکان نمی‌رود و سرِ جایش می‌ماند
    assertFalse(f.take("mazar", null, Step.CART))
    assertEquals(listOf(Step.VISIT), f.ready("kabul", page(funnel = true)))
    assertEquals(listOf(Step.VISIT, Step.CART), f.ready("mazar", page(funnel = true)))
  }
}
