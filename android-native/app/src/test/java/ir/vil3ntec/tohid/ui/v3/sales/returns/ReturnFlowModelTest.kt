package ir.vil3ntec.tohid.ui.v3.sales.returns

import ir.vil3ntec.tohid.core.i18n.StrRetPolicy
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.returns.ReturnPolicy
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ReturnFlow
import ir.vil3ntec.tohid.data.ShopData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** RET-09 — متن‌های سد و هشدارِ سیاستِ برگهٔ برگشتِ نسخهٔ ۳ (`ReturnFlowModel`) */
class ReturnFlowModelTest {

  @Test fun `دلیلِ سیاست با نامِ خودش نه «مجوز»`() {
    assertEquals(StrRetPolicy.why, ReturnFlowModel.whyText(listOf(ReturnFlow.WHY_POLICY)))
    assertEquals(StrSale.whyNoReceipt + "، " + StrRetPolicy.why, ReturnFlowModel.whyText(listOf(ReturnFlow.WHY_NO_RECEIPT, ReturnFlow.WHY_POLICY)))
    assertEquals(StrSale.whyPermission, ReturnFlowModel.whyText(listOf(ReturnFlow.WHY_PERM)))
  }

  @Test fun `هشدارِ سیاست — مهلت و نامِ کالا، استثنا فقط برای مدیر و صاحب`() {
    val d = ShopData(products = listOf(Product(id = "p40", name = "کفش سایز ۴۰")))
    val policy = ReturnPolicy.Policy(days = 7)
    assertNull(ReturnFlowModel.policyNotice(d, ReturnPolicy.Verdict(), policy, staff = true))
    val v = ReturnPolicy.Verdict(late = true, ageDays = 16, blockedIds = listOf("p40", "gone"))
    val staff = ReturnFlowModel.policyNotice(d, v, policy, staff = true)!!
    assertEquals(StrRetPolicy.late(16, 7) + "\n" + StrRetPolicy.blockedItems("کفش سایز ۴۰، gone"), staff.first)
    assertNull(staff.second)
    assertEquals(StrRetPolicy.exception, ReturnFlowModel.policyNotice(d, v, policy, staff = false)!!.second)
  }
}
