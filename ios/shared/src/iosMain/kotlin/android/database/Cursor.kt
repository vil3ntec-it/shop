package android.database

/** shimِ iOS ِ `Cursor` — فقط یک ردیفِ آماده (‎ContentResolver.query‎ ِ نامِ پرونده) */
interface Cursor : java.io.Closeable {
  fun moveToFirst(): Boolean
  fun getColumnIndex(columnName: String): Int
  fun getString(columnIndex: Int): String?
  fun getLong(columnIndex: Int): Long
  fun isNull(columnIndex: Int): Boolean
  val count: Int

  companion object {
    internal fun ofRow(columns: Array<String>, values: List<String?>): Cursor = object : Cursor {
      private var at = -1
      override fun moveToFirst(): Boolean { at = 0; return true }
      override fun getColumnIndex(columnName: String): Int = columns.indexOf(columnName)
      override fun getString(columnIndex: Int): String? = values.getOrNull(columnIndex).takeIf { at == 0 }
      override fun getLong(columnIndex: Int): Long = getString(columnIndex)?.toLongOrNull() ?: 0L
      override fun isNull(columnIndex: Int): Boolean = getString(columnIndex) == null
      override val count: Int get() = 1
      override fun close() {}
    }
  }
}
