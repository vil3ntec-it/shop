package ir.vil3ntec.tohid.core.pay

/**
 *  PAY-14 — «مدیر بررسی می‌کند»: ثبتِ بررسیِ بستنِ صندوقی که اختلافش «نیازمند بررسی» شد.
 *
 *  ⛔ بررسی **هیچ عددی را عوض نمی‌کند**: Zِ ذخیره‌شده، شمرده‌ها و اختلاف همان می‌مانند (اختلاف هرگز خودکار اصلاح
 *  نمی‌شود). فقط یک ردیفِ تازه در سابقه می‌افزاید (`type = shift_review`، `refId` = شناسهٔ همان `shift_close`) با
 *  بررسی‌کننده، زمان و یادداشتِ اجباری («چه پیدا شد / چه شد»).
 *
 *  قاعده‌ها:
 *   • فقط بستنی که `needsReview` دارد بررسی‌پذیر است.
 *   • فقط صاحب یا مدیر؛ شاگرد نه (همان کسی که در نمای کور اختلاف را نمی‌بیند).
 *   • جداییِ وظیفه: مدیری که خودش آن صندوق را بسته، بستنِ خودش را بررسی نمی‌کند؛ صاحب می‌کند (همه‌کاره است).
 *   • یک بار: بستنِ بررسی‌شده دوباره بررسی نمی‌شود. اگر دو دستگاهِ آفلاین هم‌زمان ثبت کنند، [reviewOf] همیشه
 *     همان یکی را برمی‌گرداند (زودترین، با شناسه در هم‌زمانی) — بی وابستگی به ترتیبِ رسیدن.
 *
 *  کاتلینِ خالص در commonMain (بی Double، بی ساعتِ دستگاه: زمان از بیرون می‌آید).
 */
object ShiftReview {
  const val TYPE = "shift_review"
  const val NOTE_MIN = 3
  const val NOTE_MAX = 300

  enum class Role { OWNER, MANAGER, STAFF }

  /** یک ردیفِ بررسیِ ثبت‌شده (`closeId` = `refId` ِ ردیف) */
  data class Review(val id: String, val closeId: String, val at: Long, val by: String, val note: String)

  sealed class Result {
    data class Ok(val review: Review) : Result()
    /** NOT_NEEDED، ROLE_DENIED، SELF_REVIEW، ALREADY_REVIEWED، NOTE_REQUIRED، NOTE_TOO_LONG، BY_REQUIRED */
    data class Err(val code: String) : Result()
  }

  /** بررسیِ معتبرِ یک بستن، یا null: زودترین؛ در هم‌زمانی شناسهٔ کوچک‌تر */
  fun reviewOf(closeId: String, reviews: List<Review>): Review? =
    reviews.filter { it.closeId == closeId }.minWithOrNull(compareBy<Review>({ it.at }, { it.id }))

  /** وضعیتِ یک بستن برای فهرستِ سابقه: بی نیاز، در انتظارِ بررسی، یا بررسی‌شده */
  enum class State { NONE, PENDING, REVIEWED }

  fun state(needsReview: Boolean?, closeId: String, reviews: List<Review>): State = when {
    needsReview != true -> State.NONE
    reviewOf(closeId, reviews) != null -> State.REVIEWED
    else -> State.PENDING
  }

  /**
   *  ساختنِ ردیفِ بررسی. `closedBy` بندندهٔ همان صندوق است (کاربرِ ردیفِ `shift_close`)؛ `by` بررسی‌کننده.
   *  یادداشت تراشیده می‌شود و باید [NOTE_MIN] تا [NOTE_MAX] نویسه باشد.
   */
  fun review(
    id: String, closeId: String, needsReview: Boolean?, closedBy: String?,
    role: Role, by: String, note: String, at: Long, existing: List<Review>,
  ): Result {
    if (needsReview != true) return Result.Err("NOT_NEEDED")
    if (role == Role.STAFF) return Result.Err("ROLE_DENIED")
    val who = by.trim()
    if (who.isEmpty()) return Result.Err("BY_REQUIRED")
    if (role == Role.MANAGER && closedBy != null && closedBy.trim() == who) return Result.Err("SELF_REVIEW")
    if (reviewOf(closeId, existing) != null) return Result.Err("ALREADY_REVIEWED")
    val text = note.trim()
    if (text.length < NOTE_MIN) return Result.Err("NOTE_REQUIRED")
    if (text.length > NOTE_MAX) return Result.Err("NOTE_TOO_LONG")
    return Result.Ok(Review(id, closeId, at, who, text))
  }
}
