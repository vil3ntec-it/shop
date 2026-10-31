package ir.vil3ntec.tohid.sync.v1

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 *  یک سطر از دفترِ تغییرات — بندِ ۲۰.۱ پرامپت.
 *
 *  `insert` کلِ ردیفِ تازه را دارد، `update` **فقط فیلدهای عوض‌شده** را،
 *  و `delete` هیچ فیلدی ندارد (چند صد بایت، همین).
 */
data class SyncOp(
  val opId: String,
  val ts: Long,
  val table: String,
  val rowId: String,
  val type: String,
  val fields: JsonObject?,
  /**
   *  فقط در op ِ **رسیده** با پرچمِ سرورِ `sync.revive_full_row`: ردیفِ کاملِ سرور پس از همین op، وقتی
   *  این op ردیفِ حذف‌شده را زنده کرد. دستگاهی که ردیف را ندارد (حذف را دیده) آن را کامل می‌سازد، نه فقط
   *  با فیلدهای همین op (`LedgerDiff.applyRemote`). هرگز به سرور فرستاده نمی‌شود (نه در [toJson]).
   */
  val row: JsonObject? = null,
) {
  val hash: String get() = OpHash.of(table, rowId, type, fields)

  fun toJson(): JsonObject = buildJsonObject {
    put("op_id", JsonPrimitive(opId))
    put("ts", JsonPrimitive(ts))
    put("table", JsonPrimitive(table))
    put("row_id", JsonPrimitive(rowId))
    put("type", JsonPrimitive(type))
    if (type != DELETE) put("fields", fields ?: JsonObject(emptyMap()))
    put("hash", JsonPrimitive(hash))
  }

  /** همان [toJson] به‌اضافهٔ [row] — فقط ژورنالِ `SyncCommit` (بازیابیِ «نشاندن» همان دفتر را از نو می‌سازد) */
  fun toJournalJson(): JsonObject =
    if (row == null) toJson() else JsonObject(toJson() + ("row" to row))

  companion object {
    const val INSERT = "insert"
    const val UPDATE = "update"
    const val DELETE = "delete"

    fun fromJson(o: JsonObject): SyncOp? {
      val table = (o["table"] as? JsonPrimitive)?.content ?: return null
      val rowId = (o["row_id"] as? JsonPrimitive)?.content ?: return null
      val type = (o["type"] as? JsonPrimitive)?.content ?: return null
      if (type != INSERT && type != UPDATE && type != DELETE) return null
      return SyncOp(
        opId = (o["op_id"] as? JsonPrimitive)?.content.orEmpty(),
        ts = (o["ts"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L,
        table = table,
        rowId = rowId,
        type = type,
        fields = o["fields"] as? JsonObject,
        row = if (type == DELETE) null else o["row"] as? JsonObject,
      )
    }
  }
}

/**
 *  نتیجهٔ هر op در پاسخِ `push` — `applied | duplicate | rejected`.
 *  `seq` همان `server_seq` (برای `applied` و `duplicate`؛ نبود ⇒ ۰) — دفترچهٔ
 *  `ReplayBook` با آن می‌فهمد پس از بازیابیِ سرور کدام op باید دوباره برود (§۹.۵).
 */
data class OpResult(val opId: String, val status: String, val reason: String = "", val seq: Long = 0L) {
  val accepted: Boolean get() = status == "applied" || status == "duplicate"
}
