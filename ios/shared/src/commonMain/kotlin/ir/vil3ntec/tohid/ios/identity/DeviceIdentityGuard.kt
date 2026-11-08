package ir.vil3ntec.tohid.ios.identity

import ir.vil3ntec.tohid.ios.identity.DeviceIdentityKeys as K

/**
 *  اجرای [DeviceIdentity.decide] روی انبارها (‎iosMain/…/IosDeviceIdentity‎؛ آزمون با انبارِ درونِ حافظه و «بسته شدن» پس از
 *  هر نوشتن: ‎jvmTest/…/DeviceIdentityJvmTest‎).
 *
 *  ── ترتیبِ نوشتن (هر جا که برنامه بسته شود، بارِ بعد همان‌جا ادامه می‌دهد) ─────────
 *  تازه/به‌روزرسانی: کلیددان ⇐ پرونده ⇐ prefs (`device_uid`، `device_mirrored`). بسته شدن پیش از prefs ⇒ بارِ بعد همان کار
 *  دوباره؛ هیچ «بازیابی» ای از نیمه‌کاره ساخته نمی‌شود.
 *  چرخش: ۱) ژورنال در prefs (`device_rotating_from`/`_to`) ۲) پاک کردنِ پرونده‌های «فقط همین دستگاه» ِ اندروید و کلیدهای
 *  دستگاهیِ `tohid-sync` ۳) replica ِ هر حساب ⇐ از شناسهٔ تازه ۴) `device_uid` ِ تازه و `device_mirrored` ۵) کلیددان و پرونده
 *  ۶) گزارش و برداشتنِ ژورنال. بسته شدن پس از ۱ ⇒ `Resume` با همان شناسهٔ تازه (همهٔ گام‌ها دوباره‌پذیرند).
 *
 *  ⛔ دفتر، صف، سایه، cursor، تنظیم‌های دکان و هر پرونده‌ای جز همین‌ها دست نمی‌خورد — همان که پشتیبانِ اندروید می‌برد.
 */
internal class DeviceIdentityGuard(private val io: Io) {

  interface Io {
    fun protectedData(): Boolean
    fun get(suite: String, key: String): String?
    fun keys(suite: String): Set<String>
    /** یک نوشتنِ prefs؛ `null` ⇒ برداشتن */
    fun edit(suite: String, changes: Map<String, String?>)
    fun clear(suite: String)
    fun keychain(): Probe
    fun putKeychain(uid: String)
    fun marker(): Probe
    fun putMarker(uid: String)
    fun newUid(): String
    fun log(line: String)
  }

  fun seen(): Seen {
    if (!io.protectedData()) return Seen(false, null, false, null, null, Probe.Unavailable("locked"), Probe.Unavailable("locked"))
    return Seen(
      protectedData = true,
      uid = io.get(K.SUITE, K.DEVICE),
      mirrored = io.get(K.SUITE, K.MIRRORED) == "1",
      rotatingFrom = io.get(K.SUITE, K.ROTATING_FROM),
      rotatingTo = io.get(K.SUITE, K.ROTATING_TO),
      keychain = io.keychain(),
      marker = io.marker(),
    )
  }

  /** یک بار؛ خطای انبار بیرون می‌رود (صدازننده نگه می‌دارد) و حال همیشه دوباره‌پذیر می‌ماند */
  fun run(): Verdict {
    val s = seen()
    val v = DeviceIdentity.decide(s)
    when (v) {
      is Verdict.Undecided -> Unit
      Verdict.Fresh -> {
        val uid = io.newUid()
        mirror(uid)
        io.edit(K.SUITE, mapOf(K.DEVICE to uid, K.MIRRORED to "1"))
        io.log("fresh ${DeviceIdentity.short(uid)}")
      }
      Verdict.Upgrade -> {
        val uid = s.uid.orEmpty()
        mirror(uid)
        io.edit(K.SUITE, mapOf(K.MIRRORED to "1"))
        io.log("upgrade ${DeviceIdentity.short(uid)} (آینهٔ نخست؛ شناسه همان)")
      }
      is Verdict.Same -> if (v.repair) {
        val uid = s.uid.orEmpty()
        if (s.keychain != Probe.Value(uid)) io.putKeychain(uid)
        if (s.marker != Probe.Value(uid)) io.putMarker(uid)
        if (!s.mirrored) io.edit(K.SUITE, mapOf(K.MIRRORED to "1"))
        io.log("same ${DeviceIdentity.short(uid)} (ترمیمِ آینه؛ شناسه همان)")
      }
      is Verdict.Restored -> rotate(v.old, io.newUid(), resumed = false)
      is Verdict.Resume -> rotate(v.from, v.to, resumed = true)
    }
    return v
  }

  private fun mirror(uid: String) {
    io.putKeychain(uid)
    io.putMarker(uid)
  }

  private fun rotate(from: String, to: String, resumed: Boolean) {
    io.edit(K.SUITE, mapOf(K.ROTATING_FROM to from, K.ROTATING_TO to to))
    for (suite in K.DEVICE_ONLY) if (suite != K.SUITE) io.clear(suite)
    val drop = io.keys(K.SUITE).filter { it !in K.SYNC_KEEP }
    if (drop.isNotEmpty()) io.edit(K.SUITE, drop.associateWith { null })
    val replicas = HashMap<String, String?>()
    for (key in io.keys(K.V1_SUITE)) {
      if (!key.endsWith(K.REPLICA_SUFFIX)) continue
      val kept = io.get(K.V1_SUITE, key) ?: continue
      val next = DeviceIdentity.remap(kept, from, to)
      if (next != kept) replicas[key] = next
    }
    if (replicas.isNotEmpty()) io.edit(K.V1_SUITE, replicas)
    io.edit(K.SUITE, mapOf(K.DEVICE to to, K.MIRRORED to "1"))
    mirror(to)
    io.log("restored ${DeviceIdentity.short(from)} ⇒ ${DeviceIdentity.short(to)}${if (resumed) " (ادامهٔ نیمه‌کاره)" else ""}")
    io.edit(K.SUITE, mapOf(K.ROTATING_FROM to null, K.ROTATING_TO to null))
  }
}
