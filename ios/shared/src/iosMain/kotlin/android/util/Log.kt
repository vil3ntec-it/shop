package android.util

/** shimِ iOS ِ `Log` روی `println` (همان خروجیِ کنسولِ Xcode)؛ بی دادهٔ شخصی — همان قاعدهٔ اندروید */
object Log {
  fun d(tag: String, msg: String): Int { println("D/$tag: $msg"); return 0 }
  fun i(tag: String, msg: String): Int { println("I/$tag: $msg"); return 0 }
  fun w(tag: String, msg: String): Int { println("W/$tag: $msg"); return 0 }
  fun w(tag: String, msg: String, tr: Throwable?): Int { println("W/$tag: $msg ${tr?.message.orEmpty()}"); return 0 }
  fun e(tag: String, msg: String): Int { println("E/$tag: $msg"); return 0 }
  fun e(tag: String, msg: String, tr: Throwable?): Int { println("E/$tag: $msg ${tr?.message.orEmpty()}"); return 0 }
  fun v(tag: String, msg: String): Int = 0
}
