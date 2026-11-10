package ir.vil3ntec.tohid.customer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.shopper.ChatActs
import ir.vil3ntec.tohid.core.shopper.ChatFailure
import ir.vil3ntec.tohid.core.shopper.ChatHit
import ir.vil3ntec.tohid.core.shopper.ChatMessage
import ir.vil3ntec.tohid.core.shopper.ChatOutbox
import ir.vil3ntec.tohid.core.shopper.ShopperApi
import ir.vil3ntec.tohid.core.shopper.ShopperError
import ir.vil3ntec.tohid.core.shopper.ShopperResult
import ir.vil3ntec.tohid.core.shopper.ShopperText
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  پاسخ، ویرایش، سنجاق و جست‌وجوی مشتری (CHAT-14) — همان `store/chat-acts.js`؛ قاعده‌ها در `ChatActs`/`ChatCompose` ِ core،
 *  این‌جا فقط ترتیبِ درخواست‌ها. هر پاسخِ سرور فقط وقتی می‌نشیند که همان رشته هنوز باز است ([ChatModel.live]):
 *  خطا یا نتیجهٔ دیرِ رشتهٔ پیشین روی رشتهٔ تازه نمی‌آید (دستهٔ ۴۲).
 */
class ChatActsModel internal constructor(private val chat: ChatModel, private val api: ShopperApi) {
  /** پیامِ روشنِ کارها (ویرایش شد، سنجاق شد، خطا) — ناحیهٔ زنده */
  var note: String? by mutableStateOf(null)
    private set
  /** «ذخیره» در راه (دوبار زدن یک درخواست) */
  var saving: Boolean by mutableStateOf(false)
    private set
  /** سنجاق/برداشتن در راه */
  var pinning: Boolean by mutableStateOf(false)
    private set
  /** نوارِ جست‌وجو باز است */
  var finding: Boolean by mutableStateOf(false)
    private set
  var query: String by mutableStateOf("")
  /** null ⇒ هنوز جست‌وجویی نشده؛ تهی ⇒ «پیامی پیدا نشد» */
  var hits: List<ChatHit>? by mutableStateOf(null)
    private set
  /** «در جست‌وجو…»، «n پیام پیدا شد» یا خطا */
  var findNote: String by mutableStateOf("")
    private set
  /** رفتن به حبابِ یک پیام؛ صفحه پس از پیمایش [jumped] را می‌زند */
  var jump: String? by mutableStateOf(null)
    private set

  /** مهلتِ ویرایشی که سرور گفت گذشته (حتی با ساعتِ عقبِ گوشی) */
  private val passed = HashSet<String>()

  /** رشتهٔ دیگری باز شد یا صفحه بسته شد: نتیجه‌ها، پیام‌ها و کارهای در راه دیگر از آنِ این رشته نیستند */
  internal fun reset() {
    note = null
    saving = false
    pinning = false
    hits = null
    findNote = ""
    jump = null
  }

  fun canReply(m: ChatMessage): Boolean = ChatActs.canReply(chat.current(), m)

  fun canEdit(m: ChatMessage): Boolean = ChatActs.canEdit(chat.current(), m, chat.clock(), passed)

  fun canPin(m: ChatMessage): Boolean = ChatActs.canPin(chat.current(), m)

  fun pinned(m: ChatMessage): Boolean = ChatActs.pinned(chat.current(), m.id)

  /** «پاسخ» ⇒ برچسبِ «پاسخ به …» بالای کادر؛ پیام با `replyTo` از همان صف می‌رود */
  fun reply(m: ChatMessage) {
    val t = chat.current() ?: return
    ChatActs.reply(chat.compose, t, m)?.let { chat.compose = it; chat.dropActions(); chat.focusComposer() }
  }

  /** «ویرایش» ⇒ کادر همان متن را می‌گیرد و «فرستادن» ⇒ «ذخیره» */
  fun edit(m: ChatMessage) {
    val t = chat.current() ?: return
    ChatActs.edit(t, m, chat.clock(), passed)?.let { chat.compose = it; chat.dropActions(); chat.focusComposer() }
  }

  /** «انصراف» ِ برچسب: پاسخ/ویرایش رها (متنِ ویرایش هم) */
  fun stop() {
    chat.compose = chat.compose.stop(true)
  }

  /** «ذخیره»: در صف نمی‌رود (بی‌اینترنت ⇒ پیامِ روشن، متن در کادر) */
  fun save() {
    if (saving) return
    val s = chat.compose.saving(chat.current()) ?: return
    val slug = chat.slug ?: return
    val tk = chat.ticketOrLost() ?: return
    saving = true
    note = null
    chat.scope.launch {
      val r = withContext(chat.io) { api.chatEdit(slug, tk, s.convId, s.messageId, s.text) }
      if (!chat.live(s.convId)) return@launch   //  رشتهٔ دیگری باز شد ⇒ پاسخِ دیرِ آن رشته این‌جا نمی‌نشیند
      saving = false
      when (r) {
        is ShopperResult.Ok -> {
          if (chat.compose.editing == s.messageId) chat.compose = chat.compose.stop(true)
          chat.put(s.convId, r.value)
          note = ShopperText.chatEditOk
          chat.focusComposer()
        }
        is ShopperResult.Fail -> {
          val e = r.error
          if (ChatOutbox.failureOf(e) == ChatFailure.VERIFY) { chat.lost(); return@launch }
          if (ChatActs.editEnds(e)) {
            if (e.code == "edit_window_passed") passed += s.messageId
            if (chat.compose.editing == s.messageId) chat.compose = chat.compose.stop(false)   //  متن می‌ماند
          }
          if (e.code == "chat_closed") chat.refresh(s.convId)
          note = if (e.network) ShopperText.chatEditOff else ShopperText.error(e)
        }
      }
    }
  }

  /** سنجاق/برداشتن — فقط برای خودم؛ نوار از `conversation.pinned` ِ پاسخِ سرور */
  fun pin(m: ChatMessage, on: Boolean) {
    if (pinning) return
    val t = chat.current() ?: return
    if (on && !ChatActs.canPin(t, m)) return
    val slug = chat.slug ?: return
    val tk = chat.ticketOrLost() ?: return
    val convId = t.conversation.id
    pinning = true
    note = null
    chat.dropActions()
    chat.scope.launch {
      val r = withContext(chat.io) { api.chatPin(slug, tk, convId, m.id, on) }
      if (!chat.live(convId)) return@launch
      pinning = false
      when (r) {
        is ShopperResult.Ok -> {
          chat.adopt(r.value)
          note = if (on) ShopperText.chatPinOk else ShopperText.chatUnpinOk
        }
        is ShopperResult.Fail -> {
          if (ChatOutbox.failureOf(r.error) == ChatFailure.VERIFY) { chat.lost(); return@launch }
          note = if (r.error.network) ShopperText.chatPinOff else ShopperText.error(r.error)
        }
      }
    }
  }

  /** رفتن به حبابِ پیامی از همین رشته؛ نبود ⇒ «این پیام در این صفحه باز نیست» */
  fun go(messageId: String): Boolean {
    if (ChatActs.indexOf(chat.current(), messageId) == null) {
      note = ShopperText.chatFar
      return false
    }
    jump = messageId
    return true
  }

  fun jumped() {
    jump = null
  }

  fun toggleFind() {
    finding = !finding
  }

  /** جست‌وجو در همین دکان و همین شماره؛ کوتاه‌تر از ۲ ⇒ پیامِ روشن، بی درخواست */
  fun find() {
    val q = ChatActs.query(query) ?: run { findNote = ShopperText.error(ShopperError(400, "query_too_short")); hits = null; return }
    val slug = chat.slug ?: return
    val convId = chat.current()?.conversation?.id ?: return
    val tk = chat.ticketOrLost() ?: return
    findNote = ShopperText.chatFinding
    chat.scope.launch {
      val r = withContext(chat.io) { api.chatSearch(slug, tk, q) }
      if (!chat.live(convId)) return@launch
      when (r) {
        is ShopperResult.Ok -> {
          hits = r.value
          findNote = if (r.value.isEmpty()) ShopperText.chatNone else ShopperText.chatHits(r.value.size)
        }
        is ShopperResult.Fail -> {
          if (ChatOutbox.failureOf(r.error) == ChatFailure.VERIFY) { chat.lost(); return@launch }
          findNote = ShopperText.error(r.error)
        }
      }
    }
  }

  /** خطِ یک نتیجه و این‌که در همین رشته است (رفتن به حباب) */
  fun hitLine(h: ChatHit): String = ChatActs.hitLine(h, chat.current()?.conversation?.id)

  fun here(h: ChatHit): Boolean = h.conversationId == chat.current()?.conversation?.id

  /** نتیجهٔ همین رشته ⇒ رفتن به حباب و بستنِ نوارِ جست‌وجو */
  fun open(h: ChatHit) {
    if (here(h) && go(h.id)) finding = false
  }
}
