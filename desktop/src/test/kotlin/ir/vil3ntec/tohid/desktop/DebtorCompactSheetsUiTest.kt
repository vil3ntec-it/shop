package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.plusDays
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.money.DebtorForm
import ir.vil3ntec.tohid.ui.v3.money.DebtorFormSheet
import ir.vil3ntec.tohid.ui.v3.money.DebtorFormsModel
import ir.vil3ntec.tohid.ui.v3.money.MoneyText
import ir.vil3ntec.tohid.ui.v3.money.TX_DATE_TAG
import ir.vil3ntec.tohid.ui.v3.money.TX_KIND_TAG
import ir.vil3ntec.tohid.ui.v3.money.TxForm
import ir.vil3ntec.tohid.ui.v3.money.TxFormSheet
import ir.vil3ntec.tohid.yesterdayIso
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  صاحب‌کار ۱۴۰۵/۰۸/۲۳ (تبلت) — دو برگهٔ قرض‌داران در پنجرهٔ واقعیِ Compose:
 *   ۱) «قرض‌دارِ جدید» فقط نام (لازم) و تلفن (اختیاری) — «اسم و شماره بسه»؛
 *   ۲) «ثبتِ تراکنش» ِ کوچک‌شده: قوطیِ «برد/رسید» کنارِ مبلغ به‌جای کلیدِ بزرگ، تاریخِ نوشته‌ایِ سربرگ (پیش‌فرض امروز؛
 *      زدن ⇒ همان کادرِ تاریخ)، سررسید فقط تراشه بی «یک ماه» — و op ِ ذخیره‌شده همان نوع، تاریخ و سررسید را دارد
 *      (`DebtorFormsModel.submit`، همان راهِ `TxFormRoute`).
 *  زدن‌ها با کارِ معناییِ OnClick (مثلِ `LineSheetHarness`) تا جای برگه در پنجره مهم نباشد.
 */
class DebtorCompactSheetsUiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext
  private val owing = Debtor(id = "free", name = "آزاد")
  private val data = ShopData(
    debtors = listOf(owing),
    transactions = listOf(DebtTransaction(id = "t0", debtorId = "free", type = "give", amount = 1000.0, date = "2026-10-01")),
  )

  private fun forgetChoice() = ctx.getSharedPreferences("tohid", 0).edit().remove("motion").commit()

  @Before fun setUp() {
    I18n.source = { Lang.FA }
    forgetChoice()
    Motion.reducedForTest = true
    Motion.load(ctx)
  }

  @After fun tearDown() {
    Motion.reducedForTest = null
    forgetChoice()
    Motion.load(ctx)
  }

  private fun show(content: @Composable () -> Unit) {
    rule.setContent { CompositionLocalProvider(LocalContext provides ctx) { TohidTheme(ThemeChoice.LIGHT) { content() } } }
    rule.waitForIdle()
  }

  private fun field(label: String): SemanticsNodeInteraction = rule.onNode(hasSetTextAction() and hasContentDescription(label))

  private fun tap(text: String) {
    rule.onNode(hasText(text) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  private fun tapTag(tag: String) {
    rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  private fun state(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

  /* ------------------------------ قرض‌دارِ جدید ------------------------------ */

  @Test fun newDebtorFormAsksOnlyNameAndPhone() {
    var saved: DebtorForm? = null
    show { DebtorFormSheet(null, onClose = {}, onSave = { saved = it; true }, onSaved = {}) }
    assertEquals("فقط دو کادر", 2, rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
    field(StrMoney.name).assertExists()
    field(StrMoney.phoneOptional).assertExists()
    val gone = listOf(
      StrMoney.phone2Optional, StrMoney.notesOptional, StrMoney.creditLimitField, StrMoney.tagsField, StrMoney.addressLandmark,
      StrMoney.addressArea, StrMoney.addressGate, StrMoney.addressFloor, StrMoney.addressPlusCode, StrMoney.addressPostal,
      StrMoney.addressPin, StrMoney.consentSource,
    )
    for (label in gone) rule.onAllNodes(hasContentDescription(label)).assertCountEquals(0)
    for (title in listOf(StrMoney.addressTitle, StrMoney.consentTitle, StrMoney.consentUnknown)) rule.onAllNodesWithText(title).assertCountEquals(0)
    //  نامِ خالی ⇒ ذخیره نه (همان سنجشِ امروز)
    tap(StrKit.save)
    assertNull(saved)
    field(StrMoney.name).performTextInput("زرغونه")
    field(StrMoney.phoneOptional).performTextInput("0791112233")
    tap(StrKit.save)
    assertEquals("زرغونه", saved?.name)
    assertEquals("0791112233", saved?.phone)
  }

  /* ------------------------------ ثبتِ تراکنش ------------------------------ */

  private fun showTx(kind: DebtorEngine.Kind, got: MutableList<TxForm>) = show {
    TxFormSheet(
      owing, 1000.0, kind,
      overLimitOf = { DebtorFormsModel.overLimit(data, "free", it) },
      onClose = {},
      onSubmit = { f, _ -> got += f; true },
      onDone = {},
    )
  }

  /** همان راهِ `TxFormRoute` ⇒ ردیفِ تازهٔ دفتر */
  private fun saved(f: TxForm): DebtTransaction {
    val ok = DebtorFormsModel.submit(data, "free", f, todayIso(), 1L, { "n" }, overLimit = false) as DebtorEngine.Result.Ok
    return ok.data.transactions.single { it.id == "n" }
  }

  @Test fun theKindBoxNextToTheAmountSwitchesTheTypeAndTheSavedOpHasThatType() {
    val got = ArrayList<TxForm>()
    showTx(DebtorEngine.Kind.GIVE, got)
    //  کلیدِ بزرگِ «برد — جنس یا پول برد / رسید — پول رساند» نیست
    rule.onAllNodesWithText(StrMoney.giveKind).assertCountEquals(0)
    rule.onAllNodesWithText(StrMoney.receiveKind).assertCountEquals(0)
    //  پیش‌فرض همان دکمه‌ای که برگه را باز کرد (برد)
    rule.onNodeWithTag(TX_KIND_TAG).assert(state(StrMoney.giveTag))
    field(StrMoney.amount).performTextInput("250")
    tapTag(TX_KIND_TAG)
    rule.onNodeWithTag(TX_KIND_TAG).assert(state(StrMoney.receiveTag))
    rule.onAllNodesWithText(StrMoney.recordGive).assertCountEquals(0)
    tap(StrMoney.recordReceive)
    val f = got.single()
    assertEquals(DebtorEngine.Kind.RECEIVE, f.kind)
    val tx = saved(f)
    assertEquals("receive", tx.type)
    assertEquals(250.0, tx.amount, 0.0)
  }

  @Test fun openedFromReceiveTheBoxStartsOnReceiveAndFlipsBackToGive() {
    val got = ArrayList<TxForm>()
    showTx(DebtorEngine.Kind.RECEIVE, got)
    rule.onNodeWithTag(TX_KIND_TAG).assert(state(StrMoney.receiveTag))
    field(StrMoney.amount).performTextInput("40")
    tapTag(TX_KIND_TAG)
    rule.onNodeWithTag(TX_KIND_TAG).assert(state(StrMoney.giveTag))
    tap(StrMoney.recordGive)
    assertEquals("give", saved(got.single()).type)
  }

  @Test fun theHeaderDateIsPlainTodayAndTappingItOpensThePickerThatChangesTheSavedDate() {
    val got = ArrayList<TxForm>()
    showTx(DebtorEngine.Kind.GIVE, got)
    //  کادرِ بزرگِ تاریخ و «امروز/دیروز» دیده نمی‌شوند؛ سربرگ امروز را به تقویمِ برنامه می‌گوید
    rule.onAllNodes(hasSetTextAction() and hasContentDescription(StrMoney.date)).assertCountEquals(0)
    rule.onAllNodesWithText(StrKit.yesterday).assertCountEquals(0)
    rule.onNodeWithTag(TX_DATE_TAG).assertTextEquals(MoneyText.date(todayIso()))
    tapTag(TX_DATE_TAG)
    field(StrMoney.date).assertExists()
    rule.onAllNodesWithText(Str.today).assertCountEquals(1)
    tap(StrKit.yesterday)
    //  انتخاب ⇒ کادر بسته و سربرگ تاریخِ تازه
    rule.onAllNodesWithText(StrKit.yesterday).assertCountEquals(0)
    rule.onNodeWithTag(TX_DATE_TAG).assertTextEquals(MoneyText.date(yesterdayIso()))
    field(StrMoney.amount).performTextInput("120")
    tap(StrMoney.recordGive)
    val f = got.single()
    assertEquals(yesterdayIso(), f.date)
    assertEquals(yesterdayIso(), saved(f).date)
  }

  @Test fun dueIsOnlyTheChipsWithoutOneMonthAndAChipSetsTheSavedDueDate() {
    val got = ArrayList<TxForm>()
    showTx(DebtorEngine.Kind.GIVE, got)
    rule.onAllNodesWithText(StrMoney.dueMonth).assertCountEquals(0)
    rule.onAllNodes(hasContentDescription(StrMoney.dueField)).assertCountEquals(0)
    rule.onAllNodesWithText(StrMoney.dueField).assertCountEquals(0)
    rule.onNode(hasText(StrMoney.dueNone) and hasClickAction()).assertIsSelected()
    field(StrMoney.amount).performTextInput("300")
    tap(StrMoney.dueWeek)
    rule.onNode(hasText(StrMoney.dueWeek) and hasClickAction()).assertIsSelected()
    tap(StrMoney.dueTwoWeeks)
    rule.onNode(hasText(StrMoney.dueTwoWeeks) and hasClickAction()).assertIsSelected()
    tap(StrMoney.recordGive)
    val f = got.single()
    assertEquals(plusDays(todayIso(), 14), f.due)
    assertEquals(plusDays(todayIso(), 14), saved(f).dueDate)
  }

  @Test fun theNoDueChipClearsTheDueDate() {
    val got = ArrayList<TxForm>()
    showTx(DebtorEngine.Kind.GIVE, got)
    field(StrMoney.amount).performTextInput("300")
    tap(StrMoney.dueWeek)
    tap(StrMoney.dueNone)
    tap(StrMoney.recordGive)
    assertEquals("", got.single().due)
    assertNull(saved(got.single()).dueDate)
  }
}
