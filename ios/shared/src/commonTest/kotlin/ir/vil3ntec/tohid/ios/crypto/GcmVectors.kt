package ir.vil3ntec.tohid.ios.crypto

/**
 *  بردارهای مرجعِ AES-GCM، ساختهٔ `javax.crypto` ِ JDK 21 (همان رمزنگاریِ اندروید و کامپیوتر) — مشترکِ
 *  `jvmTest` و `iosTest`. TC4 همان «Test Case 4» ِ McGrew–Viega (پیوستِ مشخصاتِ GCM) است.
 */
object GcmVectors {
  class V(val name: String, val key: String, val iv: String, val aad: String, val plain: String, val sealed: String)

  val all = listOf(
    V(
      "farsi-utf8",
      key = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
      iv = "a0a1a2a3a4a5a6a7a8a9aaab",
      aad = "746f6869642d616164",
      plain = "d8b3d984d8a7d98520d8afdaa9d8a7d98620e2809420546f686964203132333b2078787878787878787878787878787878787878787878787878787878787878787878787878787878",
      sealed = "3eaba5a99d6cdb3a42bd2809aea26707f68cbb9006971603f46742a64e99463af20e3f87d75a2b4527e47cb07102fb813f633e301aa8620639267326dc08fdc8ccc4fd1748dd9c988c201c46edf72b3219f7a71cf7f097b27d",
    ),
    V("empty-iv16", key = "404142434445464748494a4b4c4d4e4f", iv = "101112131415161718191a1b1c1d1e1f", aad = "", plain = "", sealed = "d1f8962aa9ea2a93e3aecc62d06f9f1b"),
    V(
      "65-bytes",
      key = "404142434445464748494a4b4c4d4e4f",
      iv = "202122232425262728292a2b",
      aad = "",
      plain = (7 until 72).joinToString("") { it.toString(16).padStart(2, '0') },
      sealed = "751c541a92f8ca198b6a0a7f38d4ae0425b1ff4eaf1806a1f94cbfdedcbd4820839c4a66c6630d9e7940c618f574a5a06298e35227b491d6987cb639c5e95ab9a946e5dbd09fe14a9b91dd5140a668af05",
    ),
    V(
      "nist-tc4",
      key = "feffe9928665731c6d6a8f9467308308",
      iv = "cafebabefacedbaddecaf888",
      aad = "feedfacedeadbeeffeedfacedeadbeefabaddad2",
      plain = "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a721c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b39",
      sealed = "42831ec2217774244b7221b784d0d49ce3aa212f2c02a4e035c17e2329aca12e21d514b25466931c7d8f6a5aac84aa051ba30b396a0aac973d58e0915bc94fbc3221a5db94fae95ae7121a47",
    ),
  )
}

fun hex(b: ByteArray): String = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

fun unhex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
