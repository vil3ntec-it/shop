package ir.vil3ntec.tohid.core.storefront

import ir.vil3ntec.tohid.core.storefront.AreaRequests.Cell
import ir.vil3ntec.tohid.core.storefront.AreaRequests.Contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** CHK-10 — فهرستِ «خبرم کن» ِ بیرونِ محدوده در برنامهٔ مدیریت (همزادِ `license/area-requests.js` ِ وب) */
class AreaRequestsTest {

  @Test fun uncoveredPlacesFirstThenBusiestAndAtMostTwenty() {
    val cells = listOf(
      Cell(34.5, 69.1, 1, "مرکز"),
      Cell(34.6, 69.2, 5, null),
      Cell(34.7, 69.3, 9, "شمال"),
      Cell(34.8, 69.4, 2, null),
    ) + List(30) { Cell(35.0 + it / 100.0, 70.0, 1, null) }
    val v = AreaRequests.view(40, 2, cells, emptyList())
    assertEquals(AreaRequests.MAX_CELLS, v.cells.size)
    assertEquals(listOf(5, 2), v.cells.take(2).map { it.count })
    assertEquals(null, v.cells[0].coveredBy, "جایی که هنوز ارسال نمی‌رسد اول")
    //  همه بی‌پوشش‌اند تا بیست ⇒ پوشیده‌ها بیرون ماندند
    assertEquals(0, v.cells.count { it.coveredBy != null })
    val few = AreaRequests.view(4, 2, cells.take(4), emptyList())
    assertEquals(listOf(null, null, "شمال", "مرکز"), few.cells.map { it.coveredBy })
  }

  @Test fun onlyContactsWithIdAndPhoneAndNotifiedLeavesTheList() {
    val a = Contact("r1", "+93791200003", "مرکز", 10)
    val b = Contact("r2", "+93791200004", "مرکز", 20)
    val v = AreaRequests.view(3, 2, emptyList(), listOf(a, b, Contact("", "+937", "x", 1), Contact("r3", " ", "x", 1)))
    assertEquals(listOf("r1", "r2"), v.contacts.map { it.id })
    val after = AreaRequests.without(v, "r1")
    assertEquals(listOf("r2"), after.contacts.map { it.id })
    assertEquals(1 to 2, after.covered to after.total)
    assertSame(v, AreaRequests.without(v, "nope"), "شناسهٔ ناشناخته ⇒ همان فهرست")
    //  شمارِ منفی از سرورِ ناجور ⇒ صفر
    assertEquals(0 to 0, AreaRequests.view(-1, -3, emptyList(), emptyList()).let { it.total to it.covered })
  }

  @Test fun coordinatesAreShortAndStable() {
    assertEquals("34.535", AreaRequests.coord(34.5349))
    assertEquals("69.170", AreaRequests.coord(69.17))
    assertEquals("-0.500", AreaRequests.coord(-0.5))
  }
}
