package af.tohid.shop

import af.tohid.shop.util.Format
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test fun digitsBecomePersian() {
        assertEquals("۱۲۳", Format.toFa("123"))
    }

    @Test fun moneyIsGroupedAndPersian() {
        assertEquals("۱٬۰۰۰", Format.money(1000.0))
        assertEquals("۱٬۰۴۰", Format.money(1040.4))
    }

    @Test fun wholeNumbersDropDecimals() {
        assertEquals("۵", Format.number(5.0))
    }
}
