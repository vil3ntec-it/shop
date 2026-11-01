package ir.vil3ntec.tohid.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/** زبانه‌های مشتریِ گوشی (CRM-04، CRM-14): تنظیمِ خاموش هیچ؛ RFM مبلغِ فروش است و فقط با `canSeeMoney` */
class CustomerReportsPhoneTest {
  @Test fun offShowsNothing() {
    assertEquals(emptyMap<String, String>(), crmTabs(crmOn = false, canSeeMoney = true))
  }

  @Test fun staffSeesAgingOnly() {
    assertEquals(listOf("aging"), crmTabs(crmOn = true, canSeeMoney = false).keys.toList())
  }

  @Test fun ownerSeesAgingThenRfm() {
    assertEquals(listOf("aging", "rfm"), crmTabs(crmOn = true, canSeeMoney = true).keys.toList())
  }
}
