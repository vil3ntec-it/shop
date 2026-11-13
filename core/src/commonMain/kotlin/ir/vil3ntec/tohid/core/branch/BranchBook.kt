package ir.vil3ntec.tohid.core.branch

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 *  شعبه درونِ یک دکان — فازِ ۲ ِ برنامه: خواندنِ `GET /api/shop/branches` و `/members` (`server/src/routes/shop-branches.js`)
 *  و تصمیمِ «انتخابِ شعبه». منطقِ خالص (گوشی، کامپیوتر، آیفون).
 *
 *  ⛔ پرچمِ `shop.branches` خاموش ⇒ سرور همان ۴۰۴ ِ مسیرِ ناشناخته (یا پاسخِ بی `enabled`) ⇒ [Answer.Off] ⇒ هیچ چیزِ تازه.
 *  ⛔ فقط شعبه‌هایی که سرور برای همین عضو داده ([Book.choices]): صاحب ⇐ همهٔ فعال‌ها؛ دیگران ⇐ فقط شعبه‌های خودشان.
 *     بایگانی هرگز «انتخاب» نیست (داده‌اش برای گزارشِ صاحب می‌ماند). شناسهٔ بدشکل دور ریخته می‌شود.
 */
object BranchBook {

  const val PATH = "/shop/branches"
  const val MEMBERS = "$PATH/members"

  /** `PATCH` ِ یک شعبه — شعبهٔ اصلی با `main` (همان `branches.idOf` ِ سرور) */
  fun branchPath(id: String): String = "$PATH/" + if (BranchKey.isMain(id)) "main" else id

  /** `PUT` ِ شعبه‌های یک عضو */
  fun memberPath(memberId: String): String = "$MEMBERS/$memberId"

  data class Branch(val id: String, val name: String, val archived: Boolean, val main: Boolean)

  data class Book(
    /** صاحبِ دکان (`all`) ⇐ همهٔ شعبه‌ها، ساختن/نام/بایگانی و «جمعِ همه» */
    val owner: Boolean,
    /** شعبه‌های فعالِ دسترسیِ همین عضو (سرور) */
    val mine: List<String>,
    /** صاحب ⇐ همه (بایگانی هم)؛ دیگران ⇐ فقط شعبه‌های خودش */
    val branches: List<Branch>,
  ) {
    /** شعبه‌هایی که این عضو می‌تواند در آن کار کند */
    val choices: List<Branch> get() = branches.filter { !it.archived && (owner || it.id in mine) }

    fun nameOf(id: String): String? = branches.firstOrNull { it.id == id }?.name
  }

  sealed interface Answer {
    /** پرچمِ خاموش یا سرورِ کهنه */
    data object Off : Answer
    data class On(val book: Book) : Answer
  }

  data class Member(val id: String, val userId: String, val name: String, val role: String, val branches: List<String>)
  data class Members(val branches: List<Branch>, val members: List<Member>)

  /** پس از ورود یا در شروع: چه باید کرد */
  sealed interface Pick {
    /** شعبهٔ جاری درست است؛ هیچ */
    data object Stay : Pick
    /** فقط یک شعبه ⇒ همان، بی پرسش */
    data class Use(val branch: Branch) : Pick
    /** چند شعبه ⇒ بپرس */
    data class Ask(val choices: List<Branch>) : Pick
    /** هیچ شعبه‌ای برای این عضو باز نیست (سرور هر همگام‌سازی را رد می‌کند) */
    data object None : Pick
  }

  /** کدهای ردِ شعبهٔ سرور (`branches.resolve`، `lib/branch-gate.js`) */
  enum class Refusal { REQUIRED, MAIN_ONLY, UNSUPPORTED, FORBIDDEN }

  fun refusalOf(code: String?): Refusal? = when (code) {
    "branch_required" -> Refusal.REQUIRED
    "branch_main_only" -> Refusal.MAIN_ONLY
    "branch_unsupported" -> Refusal.UNSUPPORTED
    "branch_forbidden" -> Refusal.FORBIDDEN
    else -> null
  }

  /**
   *  [current] شعبهٔ همین دستگاه؛ [afterSignIn] همین حالا وارد شد ⇒ با بیش از یک شعبه همیشه بپرس.
   *  بی ورودِ تازه ⇒ شعبهٔ جاری اگر هنوز در دسترس است می‌ماند، وگرنه دوباره بپرس.
   */
  fun decide(book: Book, current: String, afterSignIn: Boolean): Pick {
    val c = book.choices
    return when {
      c.isEmpty() -> Pick.None
      c.size == 1 -> if (c[0].id == current) Pick.Stay else Pick.Use(c[0])
      afterSignIn -> Pick.Ask(c)
      c.any { it.id == current } -> Pick.Stay
      else -> Pick.Ask(c)
    }
  }

  private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
  private fun JsonElement?.flag(): Boolean = (this as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull == true
  private fun JsonElement?.list(): List<JsonElement> = (this as? JsonArray).orEmpty()

  private fun branchOf(e: JsonElement): Branch? {
    val o = e as? JsonObject ?: return null
    val id = BranchKey.idOf(o["id"].text()) ?: return null
    return Branch(id, o["name"].text()?.trim().orEmpty(), o["status"].text() == "archived", BranchKey.isMain(id))
  }

  private fun idsOf(e: JsonElement?): List<String> = e.list().mapNotNull { BranchKey.idOf(it.text()) }.distinct()

  /** پاسخِ `GET /shop/branches` ⇐ دفترچهٔ شعبه‌ها؛ بی `enabled: true` ⇒ [Answer.Off] */
  fun of(o: JsonObject): Answer {
    if (!o["enabled"].flag()) return Answer.Off
    val branches = o["branches"].list().mapNotNull(::branchOf).distinctBy { it.id }
    return Answer.On(Book(owner = o["all"].flag(), mine = idsOf(o["mine"]), branches = branches))
  }

  /** پاسخِ `GET /shop/branches/members` (فقط صاحب) */
  fun membersOf(o: JsonObject): Members = Members(
    branches = o["branches"].list().mapNotNull(::branchOf).distinctBy { it.id },
    members = o["members"].list().mapNotNull { e ->
      val m = e as? JsonObject ?: return@mapNotNull null
      val id = m["id"].text()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      Member(id, m["userId"].text().orEmpty(), m["name"].text()?.trim().orEmpty(), m["role"].text().orEmpty(), idsOf(m["branches"]))
    },
  )
}
