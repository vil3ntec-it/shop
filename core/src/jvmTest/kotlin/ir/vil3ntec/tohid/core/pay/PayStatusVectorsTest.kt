package ir.vil3ntec.tohid.core.pay

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  PAY-07: `PayStatus` در برابرِ `constants.statuses` و `constants.transitions` ِ `core/test-vectors/pay-v1.json` —
 *  همان جدولی که `server/test/pay-core.test.js` با `license/pay-core.js` می‌خواند. هر جفتِ (از، به): سندی که
 *  نه گذارِ جدول است نه اصلاحِ `PayStatus.CORRECTIONS`، یا مقصدِ دستی نیست، کنار می‌رود و `check` ردش می‌کند؛ بقیه
 *  می‌نشیند. اصلاح فقط از «تأییدشده» و فقط به «نیازمند بررسی»/«ناموفق»/«لغو» (نه بازپرداخت، نه برگشت به «در انتظار»).
 */
class PayStatusVectorsTest {

  private val constants: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/pay-v1.json").takeIf { it.isFile } ?: File(dir, "test-vectors/pay-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject["constants"]!!.jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/pay-v1.json پیدا نشد")
  }

  private val statuses = constants["statuses"]!!.jsonArray.map { it.jsonPrimitive.content }
  private val table: Map<String, List<String>> =
    constants["transitions"]!!.jsonObject.mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.content } }

  @Test
  fun coreTableIsTheVectorTable() {
    assertEquals(statuses, Pay.STATUSES)
    assertEquals(table, Pay.TRANSITIONS)
  }

  @Test
  fun everyPairFollowsTheTableAndManualTargets() {
    var applied = 0
    var rejected = 0
    for (from in statuses) for (to in statuses + listOf("paid")) {
      //  ردیفی با همین وضعیتِ ذخیره‌شده (بی `manual`، تا آغازش همان [from] باشد)
      val row = PayStatus.Row("afpay_card", "card", 10_000, from)
      val change = PayStatus.Change("x", "s", 0, "afpay_card", from, to, 5, "سارا")
      val expect = to in PayStatus.TARGETS && (to in table[from].orEmpty() || to in PayStatus.CORRECTIONS[from].orEmpty())
      val got = PayStatus.apply("s", listOf(row), listOf(change))
      assertEquals(if (expect) to else from, got.statuses.single(), "$from ⇒ $to")
      val check = PayStatus.check(PayStatus.Input("x", "s", listOf(row), 0, to, 5, "سارا", sell = true, manage = true))
      assertEquals(expect, check is PayResult.Ok, "$from ⇒ $to: $check")
      if (expect) applied++ else rejected++
    }
    //  ۱۰ گذارِ دستی (۴ از «در انتظار»، ۳ از «نیازمند بررسی»، ۳ اصلاحِ «تأییدشده»)؛ بقیه رد
    assertEquals(10, applied)
    assertTrue(rejected > 40)
  }

  @Test
  fun correctionsOnlyLeaveConfirmedAndNeverRefundOrReopen() {
    assertEquals(setOf("confirmed"), PayStatus.CORRECTIONS.keys)
    for (to in PayStatus.CORRECTIONS.values.flatten()) {
      assertTrue(to in listOf("needs_review", "failed", "cancelled"), to)
      assertTrue(to !in table["confirmed"].orEmpty(), to)
    }
    //  ثبتِ دستیِ ذخیره‌شده (`pending` + `manual`) از «تأییدشده» آغاز می‌شود
    assertEquals("confirmed", PayStatus.initial(PayStatus.Row("hawala", "transfer", 1, "pending", "manual")))
  }
}
