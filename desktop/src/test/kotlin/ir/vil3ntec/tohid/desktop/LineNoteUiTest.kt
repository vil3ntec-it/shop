package ir.vil3ntec.tohid.desktop

import androidx.compose.ui.test.junit4.createComposeRule
import ir.vil3ntec.tohid.core.i18n.StrSalePages
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.ui.components.pos.PosNotesPrefs
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  POS-11 در کادرِ ردیفِ گوشیِ نسخهٔ ۳ ([LineSheetHarness]): «یادداشتِ ردیف» (همان `posLineNote` ⇒ `CartNotes.set` ِ
 *  `CartLineDialog`)؛ تنظیمِ «یادداشت» ِ خاموش ⇒ بی کادر. (`SaleNotesWiringTest` فقط یادداشتِ فاکتور را می‌سنجد.)
 */
class LineNoteUiTest {

  @get:Rule val rule = createComposeRule()
  private lateinit var s: LineSheetHarness
  private var before = true

  @Before fun up() {
    PosNotesPrefs.load(DesktopContext)
    before = PosNotesPrefs.on.value
    s = LineSheetHarness(rule)
  }

  @After fun down() { s.close(); PosNotesPrefs.set(DesktopContext, before) }

  @Test fun aLineNoteIsSavedFromThePhoneLineSheet() {
    PosNotesPrefs.set(DesktopContext, true)
    s.open(SalesEngine.CartLine(s.rice.id, 1.0))
    s.h.await(StrSalePages.lineNote, substring = true)
    s.type(StrSalePages.lineNote, "بسته‌بندیِ جدا")
    s.save()
    s.h.awaitThat { s.line()?.note == "بسته‌بندیِ جدا" }
  }

  @Test fun noLineNoteFieldWhenNotesAreOff() {
    PosNotesPrefs.set(DesktopContext, false)
    s.open(SalesEngine.CartLine(s.rice.id, 1.0))
    assertFalse(s.h.has(StrSalePages.lineNote, substring = true))
  }
}
