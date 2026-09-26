package android.util

object Log {
  private fun out(level: String, tag: String?, msg: String?, tr: Throwable? = null): Int {
    System.err.println("$level/$tag: $msg")
    tr?.printStackTrace()
    return 0
  }
  fun d(tag: String?, msg: String): Int = out("D", tag, msg)
  fun i(tag: String?, msg: String): Int = out("I", tag, msg)
  fun w(tag: String?, msg: String): Int = out("W", tag, msg)
  fun w(tag: String?, msg: String?, tr: Throwable?): Int = out("W", tag, msg, tr)
  fun e(tag: String?, msg: String): Int = out("E", tag, msg)
  fun e(tag: String?, msg: String?, tr: Throwable?): Int = out("E", tag, msg, tr)
  fun v(tag: String?, msg: String): Int = 0
}

data class Size(val width: Int, val height: Int)
