package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.courier.CourierEvent
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.courier.QueueState
import ir.vil3ntec.tohid.core.courier.RejectedEvent
import org.json.JSONArray
import org.json.JSONObject

/** JSONِ حالتِ پیک روی گوشی — جدا تا بی دستگاه آزموده شود (`CourierCodecTest`) */
object CourierCodec {

  fun eventJson(e: CourierEvent): JSONObject = JSONObject()
    .put("eventId", e.eventId).put("assignmentId", e.assignmentId).put("type", e.type)
    .put("deviceTime", e.deviceTime).put("deviceSeq", e.deviceSeq)
    .put("from", e.from).put("reason", e.reason).put("note", e.note)
    .put("cashCollectedMinor", e.cashCollectedMinor ?: JSONObject.NULL)
    .put("cashReason", e.cashReason).put("code", e.code)

  fun event(o: JSONObject): CourierEvent = CourierEvent(
    eventId = o.optString("eventId"),
    assignmentId = o.optString("assignmentId"),
    type = o.optString("type"),
    deviceTime = o.optLong("deviceTime"),
    deviceSeq = o.optLong("deviceSeq"),
    from = o.optString("from"),
    reason = o.optString("reason"),
    note = o.optString("note"),
    cashCollectedMinor = if (o.isNull("cashCollectedMinor")) null else o.optLong("cashCollectedMinor"),
    cashReason = o.optString("cashReason"),
    code = o.optString("code"),
  )

  fun queueJson(q: QueueState): String {
    val pending = JSONArray().also { a -> q.pending.forEach { a.put(eventJson(it)) } }
    val rejected = JSONArray().also { a ->
      q.rejected.forEach { a.put(JSONObject().put("event", eventJson(it.event)).put("code", it.code).put("message", it.message)) }
    }
    return JSONObject().put("v", 1).put("nextSeq", q.nextSeq).put("pending", pending).put("rejected", rejected).toString()
  }

  /** خراب یا خالی ⇒ صفِ تازه؛ ولی `nextSeq` هرگز از بیشترین ترتیبِ دیده‌شده پایین‌تر نمی‌آید */
  fun queue(raw: String?): QueueState = runCatching {
    if (raw.isNullOrBlank()) return QueueState()
    val o = JSONObject(raw)
    val p = o.optJSONArray("pending") ?: JSONArray()
    val pending = (0 until p.length()).map { event(p.getJSONObject(it)) }.filter { it.eventId.isNotBlank() }
    val r = o.optJSONArray("rejected") ?: JSONArray()
    val rejected = (0 until r.length()).map {
      val x = r.getJSONObject(it)
      RejectedEvent(event(x.getJSONObject("event")), x.optString("code"), x.optString("message"))
    }
    val maxSeen = (pending.map { it.deviceSeq } + rejected.map { it.event.deviceSeq }).maxOrNull() ?: 0L
    QueueState(maxOf(o.optLong("nextSeq", 1L), maxSeen + 1), pending, rejected)
  }.getOrDefault(QueueState())

  fun cardJson(c: MissionCard): JSONObject = JSONObject()
    .put("id", c.id).put("orderNo", c.orderNo).put("status", c.status)
    .put("landmark", c.landmark).put("addressText", c.addressText)
    .put("lat", c.lat ?: JSONObject.NULL).put("lng", c.lng ?: JSONObject.NULL)
    .put("customerName", c.customerName).put("phoneMasked", c.phoneMasked)
    .put("callBeforeArrival", c.callBeforeArrival).put("leaveWith", c.leaveWith)
    .put("cashTenderMinor", c.cashTenderMinor ?: JSONObject.NULL)
    .put("itemsCount", c.itemsCount).put("paymentMethod", c.paymentMethod)
    .put("expectedCollectMinor", c.expectedCollectMinor)
    .put("cashCollectedMinor", c.cashCollectedMinor ?: JSONObject.NULL)
    .put("fareMinor", c.fareMinor).put("requiresCode", c.requiresCode).put("photoAllowed", c.photoAllowed)
    .put("recallRequested", c.recallRequested).put("updatedAt", c.updatedAt)
    .put("area", c.area).put("gateColor", c.gateColor)
    .put("addressCorrectedAt", c.addressCorrectedAt ?: JSONObject.NULL)

  fun card(o: JSONObject): MissionCard = MissionCard(
    id = o.optString("id"),
    orderNo = o.optLong("orderNo"),
    status = o.optString("status"),
    landmark = o.optString("landmark"),
    addressText = o.optString("addressText"),
    lat = if (o.isNull("lat")) null else o.optDouble("lat"),
    lng = if (o.isNull("lng")) null else o.optDouble("lng"),
    customerName = o.optString("customerName"),
    phoneMasked = o.optString("phoneMasked"),
    callBeforeArrival = o.optBoolean("callBeforeArrival"),
    leaveWith = o.optString("leaveWith"),
    cashTenderMinor = if (o.isNull("cashTenderMinor")) null else o.optLong("cashTenderMinor"),
    itemsCount = o.optInt("itemsCount"),
    paymentMethod = o.optString("paymentMethod"),
    expectedCollectMinor = o.optLong("expectedCollectMinor"),
    cashCollectedMinor = if (o.isNull("cashCollectedMinor")) null else o.optLong("cashCollectedMinor"),
    fareMinor = o.optLong("fareMinor"),
    requiresCode = o.optBoolean("requiresCode"),
    photoAllowed = o.optBoolean("photoAllowed"),
    recallRequested = o.optBoolean("recallRequested"),
    updatedAt = o.optLong("updatedAt"),
    area = o.optString("area"),
    gateColor = o.optString("gateColor"),
    //  کارتِ کهنه‌تر این کلید را ندارد ⇒ null (isNull برای کلیدِ نبوده هم true است)
    addressCorrectedAt = if (o.isNull("addressCorrectedAt")) null else o.optLong("addressCorrectedAt"),
  )

  fun cardsJson(cards: List<MissionCard>): String = JSONArray().also { a -> cards.forEach { a.put(cardJson(it)) } }.toString()

  fun cards(raw: String?): List<MissionCard> = runCatching {
    if (raw.isNullOrBlank()) return emptyList()
    val a = JSONArray(raw)
    (0 until a.length()).map { card(a.getJSONObject(it)) }.filter { it.id.isNotBlank() }
  }.getOrDefault(emptyList())
}
