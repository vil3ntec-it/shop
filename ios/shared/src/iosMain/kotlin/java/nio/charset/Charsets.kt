package java.nio.charset

import java.nio.ByteBuffer
import java.nio.CharBuffer

/*
 *  shimِ iOS ِ رمزگشای سخت‌گیرِ JDK: `Charsets.UTF_8.newDecoder().onMalformedInput(REPORT)…decode(…)`.
 *  UTF-8 ِ نادرست ⇐ CharacterCodingException (همان `kotlin.text`؛ در JVM همین نام)، نه «�».
 */
enum class CodingErrorAction { REPORT, REPLACE, IGNORE }

class CharsetDecoder internal constructor() {
  private var strict = true
  fun onMalformedInput(a: CodingErrorAction): CharsetDecoder { strict = a == CodingErrorAction.REPORT; return this }
  fun onUnmappableCharacter(a: CodingErrorAction): CharsetDecoder = this
  fun decode(input: ByteBuffer): CharBuffer = CharBuffer(input.bytes.decodeToString(throwOnInvalidSequence = strict))
}

typealias CharacterCodingException = kotlin.text.CharacterCodingException
