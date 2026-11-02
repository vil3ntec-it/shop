package ir.vil3ntec.tohid.ios.jvm

import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json

/** `Json.encodeToStream` ِ JVM (kotlinx.serialization روی `java.io.OutputStream`) — همان متن به UTF-8 */
fun <T> Json.encodeToStream(serializer: SerializationStrategy<T>, value: T, stream: java.io.OutputStream) {
  stream.write(encodeToString(serializer, value).encodeToByteArray())
}
