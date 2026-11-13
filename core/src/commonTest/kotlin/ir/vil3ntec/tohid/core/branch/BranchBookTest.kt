package ir.vil3ntec.tohid.core.branch

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  شعبه، فازِ ۲: خواندنِ `GET /api/shop/branches` و `/members` (`server/src/routes/shop-branches.js`) و تصمیمِ
 *  «انتخابِ شعبه» پس از ورود — فقط شعبه‌های فعالِ دسترسیِ همین عضو؛ یک شعبه ⇒ بی پرسش.
 */
class BranchBookTest {

  private val b1 = "brn_00112233445566778899aabb"
  private val b2 = "brn_00112233445566778899aabc"
  private val b3 = "brn_00112233445566778899aabd"

  private fun json(s: String) = Json.parseToJsonElement(s).jsonObject

  private val ownerBody = json(
    """{"enabled":true,"all":true,"mine":["","$b1"],"branches":[
      {"id":"","name":"مرکز","status":"active","main":true},
      {"id":"$b1","name":"بازار","status":"active","main":false},
      {"id":"$b2","name":"کهنه","status":"archived","main":false},
      {"id":"bad","name":"بدشکل","status":"active","main":false}]}""",
  )

  @Test
  fun offWhenServerSaysNothingOrNotEnabled() {
    assertEquals(BranchBook.Answer.Off, BranchBook.of(json("""{}""")))
    assertEquals(BranchBook.Answer.Off, BranchBook.of(json("""{"enabled":false}""")))
    assertEquals(BranchBook.Answer.Off, BranchBook.of(json("""{"enabled":"true","branches":[]}""")))
  }

  @Test
  fun ownerSeesAllActiveAndArchivedIsNotAChoice() {
    val book = assertIs<BranchBook.Answer.On>(BranchBook.of(ownerBody)).book
    assertTrue(book.owner)
    assertEquals(listOf("", b1, b2), book.branches.map { it.id })
    assertEquals(listOf("", b1), book.choices.map { it.id })
    assertTrue(book.branches.single { it.id == b2 }.archived)
    assertEquals("مرکز", book.nameOf(""))
    assertEquals("بازار", book.nameOf(b1))
    assertNull(book.nameOf(b3))
  }

  @Test
  fun staffSeesOnlyItsOwnBranches() {
    val body = json(
      """{"enabled":true,"all":false,"mine":["$b1"],"branches":[
        {"id":"$b1","name":"بازار","status":"active","main":false}]}""",
    )
    val book = assertIs<BranchBook.Answer.On>(BranchBook.of(body)).book
    assertEquals(false, book.owner)
    assertEquals(listOf(b1), book.choices.map { it.id })
  }

  @Test
  fun oneChoiceNeverAsks() {
    val one = BranchBook.Book(owner = false, mine = listOf(b1), branches = listOf(BranchBook.Branch(b1, "بازار", archived = false, main = false)))
    assertEquals(BranchBook.Pick.Use(one.choices.single()), BranchBook.decide(one, BranchKey.MAIN, afterSignIn = true))
    assertEquals(BranchBook.Pick.Stay, BranchBook.decide(one, b1, afterSignIn = true))
    val onlyMain = BranchBook.Book(owner = false, mine = listOf(""), branches = listOf(BranchBook.Branch("", "مرکز", archived = false, main = true)))
    assertEquals(BranchBook.Pick.Stay, BranchBook.decide(onlyMain, BranchKey.MAIN, afterSignIn = true))
  }

  @Test
  fun severalChoicesAskAfterSignInAndKeepOnRestart() {
    val book = assertIs<BranchBook.Answer.On>(BranchBook.of(ownerBody)).book
    assertEquals(BranchBook.Pick.Ask(book.choices), BranchBook.decide(book, BranchKey.MAIN, afterSignIn = true))
    assertEquals(BranchBook.Pick.Stay, BranchBook.decide(book, b1, afterSignIn = false))
    //  شعبهٔ جاری بایگانی شد یا از دسترسی بیرون رفت ⇒ دوباره بپرس
    assertEquals(BranchBook.Pick.Ask(book.choices), BranchBook.decide(book, b2, afterSignIn = false))
  }

  @Test
  fun noAccessAtAllIsNone() {
    val none = BranchBook.Book(owner = false, mine = emptyList(), branches = emptyList())
    assertEquals(BranchBook.Pick.None, BranchBook.decide(none, BranchKey.MAIN, afterSignIn = true))
  }

  @Test
  fun membersAndTheirBranches() {
    val body = json(
      """{"branches":[{"id":"","name":"مرکز","status":"active","main":true},{"id":"$b1","name":"بازار","status":"active","main":false}],
        "members":[{"id":"mem_1","userId":"usr_1","name":"علی","role":"cashier","status":"active","branches":[""]},
                   {"id":"mem_2","userId":"usr_2","name":"","role":"manager","status":"active","branches":["$b1","bad"]}]}""",
    )
    val m = BranchBook.membersOf(body)
    assertEquals(listOf("", b1), m.branches.map { it.id })
    assertEquals(listOf("mem_1", "mem_2"), m.members.map { it.id })
    assertEquals(listOf(""), m.members[0].branches)
    assertEquals(listOf(b1), m.members[1].branches, "بدشکل دور ریخته می‌شود")
  }

  @Test
  fun pathsUseMainForTheMainBranch() {
    assertEquals("/shop/branches", BranchBook.PATH)
    assertEquals("/shop/branches/main", BranchBook.branchPath(BranchKey.MAIN))
    assertEquals("/shop/branches/$b1", BranchBook.branchPath(b1))
    assertEquals("/shop/branches/members/mem_1", BranchBook.memberPath("mem_1"))
  }

  @Test
  fun refusalCodesAreRecognised() {
    assertEquals(BranchBook.Refusal.REQUIRED, BranchBook.refusalOf("branch_required"))
    assertEquals(BranchBook.Refusal.MAIN_ONLY, BranchBook.refusalOf("branch_main_only"))
    assertEquals(BranchBook.Refusal.UNSUPPORTED, BranchBook.refusalOf("branch_unsupported"))
    assertEquals(BranchBook.Refusal.FORBIDDEN, BranchBook.refusalOf("branch_forbidden"))
    assertNull(BranchBook.refusalOf("forbidden"))
    assertNull(BranchBook.refusalOf(null))
  }
}
