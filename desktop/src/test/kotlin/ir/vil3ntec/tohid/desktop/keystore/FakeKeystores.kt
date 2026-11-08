package ir.vil3ntec.tohid.desktop.keystore

import java.io.File
import java.util.Base64

/*
 *  ابزارِ مشترکِ آزمون‌های جای کلید (`KekVaultTest`، `SessionKeyVaultTest`): کلیددانِ ساختگی با شکست‌های تزریقی و
 *  دیسکی که در گامِ nام برق را می‌بُرد.
 */

/** برق — `Error`، تا هیچ `catch (Exception)` ِ کوچ آن را «شکستِ عادی» نگیرد */
internal class PowerCut : Error("power cut")

internal class FakeStore(override val id: String = "fake", private val inBlob: Boolean = false) : OsKeyStore {
  override val label: String = "Fake"
  val items = HashMap<String, ByteArray>()
  var missing = false
  var locked = false
  var readFails = false
  var wrongRead = false
  var cutAfterStore = false
  var protects = 0
  var unprotects = 0

  override fun protect(ref: String, key: ByteArray): String {
    if (missing) throw KeyStoreFailure("missing")
    if (locked) throw KeyStoreFailure("locked")
    protects++
    val blob = if (inBlob) Base64.getEncoder().encodeToString(key.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()) else ""
    if (!inBlob) items[ref] = key.copyOf()
    if (cutAfterStore) { cutAfterStore = false; throw PowerCut() }
    return blob
  }

  override fun unprotect(ref: String, blob: String): ByteArray {
    if (missing) throw KeyStoreFailure("missing")
    if (locked) throw KeyStoreFailure("locked")
    if (readFails) throw KeyStoreFailure("read failed")
    unprotects++
    val k = if (inBlob) {
      Base64.getDecoder().decode(blob).map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
    } else {
      items[ref]?.copyOf() ?: throw KeyStoreFailure("not found", notFound = true)
    }
    if (wrongRead) k[0] = (k[0] + 1).toByte()
    return k
  }

  override fun remove(ref: String) {
    items.remove(ref)
  }
}

/** دیسکِ واقعی که در گامِ nام ِ نوشتن/پاک کردن/کنار گذاشتن برق را می‌بُرد — پیش یا پس از خودِ گام */
internal class CutDisk(private val cutAt: Int, private val after: Boolean) : KekVault.Disk {
  var ops = 0
  var dead = false
  private fun step(op: () -> Unit) {
    if (dead) throw PowerCut()
    ops++
    if (ops == cutAt && !after) { dead = true; throw PowerCut() }
    op()
    if (ops == cutAt) { dead = true; throw PowerCut() }
  }
  override fun read(f: File): ByteArray {
    if (dead) throw PowerCut()
    return KekVault.Disk.Real.read(f)
  }
  override fun write(f: File, bytes: ByteArray) = step { KekVault.Disk.Real.write(f, bytes) }
  override fun delete(f: File): Boolean {
    var r = false
    step { r = KekVault.Disk.Real.delete(f) }
    return r
  }
  override fun moveAside(from: File, to: File) = step { KekVault.Disk.Real.moveAside(from, to) }
  override fun syncDir(dir: File) = step { }
}
