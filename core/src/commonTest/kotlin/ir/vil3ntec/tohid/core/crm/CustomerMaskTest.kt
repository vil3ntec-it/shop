package ir.vil3ntec.tohid.core.crm

import ir.vil3ntec.tohid.core.crm.CustomerMask.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CRM-15 — پوشاندنِ شماره و نشانیِ مشتری بر پایهٔ نقش (پرچمِ `shop.customer_role_mask`) */
class CustomerMaskTest {

  private val phone = "+93791200008"
  private val handling = CustomerMask.Order(handling = true)

  private fun allowed(role: String, layers: List<Any?>? = null, on: Boolean = true) = CustomerMask.contactAllowed(role, layers, on)
  private fun viewOf(role: String, order: CustomerMask.Order? = null, layers: List<Any?>? = null) =
    CustomerMask.view(role, allowed(role, layers), on = true, order = order)

  @Test fun maskedFormatShowsOnlyTheLastThreeDigits() {
    assertEquals("•••• 008", CustomerMask.masked(phone))
    assertEquals("•••• ۰۰۸", CustomerMask.masked("۰۷۹۱۲۰۰۰۰۸"))
    //  درازای شماره پیدا نیست
    assertEquals(CustomerMask.masked("0791200008"), CustomerMask.masked("+93 79 120 0008"))
    //  جهت‌نماهای متنِ نمایشی می‌مانند
    assertEquals("\u2066•••• 008\u2069", CustomerMask.masked("\u20660791200008\u2069"))
    assertEquals("••••", CustomerMask.masked("123"))
    assertEquals("", CustomerMask.masked(""))
    assertEquals("a•••@mail.af", CustomerMask.masked("ali@mail.af"))
    assertFalse(CustomerMask.masked(phone).contains("0791"))
    assertTrue(ir.vil3ntec.tohid.core.text.ContactMask.isMasked(CustomerMask.masked(phone)))
  }

  @Test fun ownerAlwaysSeesFullPhoneAndAddress() {
    assertEquals(CustomerMask.FULL_VIEW, viewOf("owner"))
    //  صاحب هرگز بسته نمی‌شود
    assertEquals(CustomerMask.FULL_VIEW, viewOf("owner", layers = listOf(CustomerMask.DENY_LAYER)))
    assertEquals(phone, CustomerMask.phone(phone, viewOf("owner").phone, on = true))
    assertEquals("کارتهٔ ۴", CustomerMask.address("کارتهٔ ۴", viewOf("owner").address))
  }

  @Test fun managerSeesFullUnlessTheOwnerClosedIt() {
    assertEquals(CustomerMask.FULL_VIEW, viewOf("manager"))
    val closed = listOf<Any?>(emptyMap<String, Any?>(), CustomerMask.DENY_LAYER)
    assertEquals(CustomerMask.PRIVATE, viewOf("manager", layers = closed))
  }

  @Test fun cashierAndStaffSeeMaskedPhoneAndNoAddressOutsideTheirOrder() {
    for (role in listOf("cashier", "staff", "storekeeper", "picker", "someNewRole")) {
      val v = viewOf(role)
      assertEquals(CustomerMask.PRIVATE, v, role)
      assertEquals("•••• 008", CustomerMask.phone(phone, v.phone, on = true), role)
      assertNull(CustomerMask.address("کارتهٔ ۴", v.address), role)
      //  سفارشی که خودش در دست دارد
      assertEquals(CustomerMask.FULL_VIEW, viewOf(role, handling), role)
      //  سفارشِ کسِ دیگر
      assertEquals(CustomerMask.PRIVATE, viewOf(role, CustomerMask.Order()), role)
    }
  }

  @Test fun explicitGrantByTheOwnerStillWins() {
    val grant = listOf<Any?>(emptyMap<String, Any?>(), mapOf(CustomerMask.KEY to mapOf("mode" to "allow")))
    assertEquals(CustomerMask.FULL_VIEW, viewOf("cashier", layers = grant))
    assertEquals(CustomerMask.FULL_VIEW, viewOf("accountant", layers = grant))
  }

  @Test fun courierSeesOnlyAssignedOrdersUntilDelivered() {
    assertEquals(CustomerMask.PRIVATE, viewOf("courier"))
    assertEquals(CustomerMask.PRIVATE, viewOf("courier", CustomerMask.Order(assignedToMe = false)))
    assertEquals(CustomerMask.FULL_VIEW, viewOf("courier", CustomerMask.Order(assignedToMe = true)))
    assertEquals(CustomerMask.PRIVATE, viewOf("courier", CustomerMask.Order(assignedToMe = true, delivered = true)))
    //  «در دست داشتن» پیک را باز نمی‌کند — فقط سپرده
    assertEquals(CustomerMask.PRIVATE, viewOf("courier", handling))
  }

  @Test fun accountantSeesNameAndBalanceOnly() {
    val v = viewOf("accountant")
    assertEquals(CustomerMask.PRIVATE, v)
    //  حسابدار کارِ سفارش ندارد ⇒ «در دست» هم باز نمی‌کند
    assertEquals(CustomerMask.PRIVATE, viewOf("accountant", handling))
    assertEquals("•••• 008", CustomerMask.phone(phone, v.phone, on = true))
  }

  @Test fun flagOffIsExactlyTodaysRule() {
    //  امروز: صندوق‌دار و حسابدار `customers.contact` دارند، انباردار و جمع‌آور نه
    assertTrue(allowed("cashier", on = false))
    assertTrue(allowed("accountant", on = false))
    assertFalse(allowed("picker", on = false))
    assertEquals(CustomerMask.FULL_VIEW, CustomerMask.view("cashier", true, on = false))
    assertEquals(CustomerMask.PRIVATE, CustomerMask.view("picker", false, on = false))
    //  قالبِ امروز (دو رقمِ آخر)
    assertEquals("+•••••••••08", CustomerMask.phone(phone, Level.MASKED, on = false))
    assertEquals(ir.vil3ntec.tohid.core.text.ContactMask.show(phone, false), CustomerMask.show(phone, visible = false, on = false))
    assertNull(CustomerMask.layers("cashier", null, on = false))
  }

  @Test fun printedPhoneIsMaskedForEveryRoleUnlessTheOwnerTurnedFullOn() {
    assertEquals("•••• 008", CustomerMask.printPhone(phone, on = true, printFull = false))
    assertEquals(phone, CustomerMask.printPhone(phone, on = true, printFull = true))
    //  پرچمِ خاموش ⇒ همان چاپِ امروز
    assertEquals(phone, CustomerMask.printPhone(phone, on = false, printFull = false))
    assertTrue(CustomerMask.printMasked(on = true, printFull = false))
    assertFalse(CustomerMask.printMasked(on = true, printFull = true))
  }
}
