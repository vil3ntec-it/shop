package ir.vil3ntec.tohid.ios

import ir.vil3ntec.tohid.data.AppleCredential
import ir.vil3ntec.tohid.data.AppleSignInUi
import java.security.MessageDigest
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationErrorCanceled
import platform.AuthenticationServices.ASAuthorizationScopeEmail
import platform.AuthenticationServices.ASAuthorizationScopeFullName
import platform.AuthenticationServices.ASPresentationAnchor
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*
 *  پنجرهٔ «ورود با اپل» ِ آیفون (`ASAuthorizationAppleIDProvider`) پشتِ همان قراردادِ مشترکِ `data/ExternalSignIn.kt`.
 *  کدِ مشترک (`AuthController.apple`) nonce ِ خامِ سرور را می‌دهد؛ این‌جا SHA-256 ِ هگزِ آن به اپل می‌رود (اپل همان
 *  را در ادعای `nonce` ِ توکن می‌گذارد و سرور با nonce ِ خام می‌سنجد — `server/src/lib/apple.js`). توکن خودش سنجیده
 *  نمی‌شود: فقط سرور می‌سنجد. ایمیل از خودِ توکن خوانده می‌شود چون اپل `email` ِ اعتبارنامه را فقط بارِ نخست می‌دهد
 *  (فقط برای نمایش و «ورودهای ذخیره‌شده»)؛ نام هم فقط بارِ نخست هست و با توکن به سرور می‌رود.
 *
 *  پیش‌نیاز (BLOCKED-EXTERNAL، `docs/IOS-PLAN-fa.md` §۰.۶): قابلیتِ «Sign in with Apple» در شناسهٔ برنامه و profile،
 *  و `APPLE_CLIENT_IDS` روی سرور. بی آن‌ها دکمه ساخته نمی‌شود (`/config` ⇐ `appleLogin: false`)، یا اپل خطای ۱۰۰۰
 *  می‌دهد که همان پیامِ روشنِ «ورود با اپل انجام نشد — …» است.
 */
internal object AppleSignInIos : AppleSignInUi {

  override suspend fun pick(nonce: String): AppleCredential? =
    suspendCancellableCoroutine { cont ->
      IosUi.onMain {
        val request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = listOf(ASAuthorizationScopeFullName, ASAuthorizationScopeEmail)
        request.nonce = sha256Hex(nonce)
        val window = Window(cont)
        val controller = ASAuthorizationController(authorizationRequests = listOf(request))
        controller.delegate = window
        controller.presentationContextProvider = window
        //  نماینده در UIKit ضعیف نگه داشته می‌شود ⇒ تا پاسخ نگه داشته شود
        IosUi.hold(window)
        controller.performRequests()
      }
    }

  /** نماینده و لنگرِ پنجرهٔ اپل — یک بار پاسخ می‌دهد و خودش را رها می‌کند */
  private class Window(
    private val cont: CancellableContinuation<AppleCredential?>,
  ) : NSObject(), ASAuthorizationControllerDelegateProtocol, ASAuthorizationControllerPresentationContextProvidingProtocol {

    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization: ASAuthorization) {
      IosUi.release(this)
      val cred = didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
      val token = cred?.identityToken?.let { utf8(it) }
      if (cred == null || token.isNullOrBlank()) {
        resumeFail("پاسخِ اپل شناخته نشد")
        return
      }
      val name = cred.fullName?.let { listOfNotNull(it.givenName, it.familyName).joinToString(" ").trim() }.orEmpty()
      val email = cred.email?.takeIf { it.isNotBlank() } ?: emailOf(token)
      if (cont.isActive) cont.resume(AppleCredential(idToken = token, email = email, name = name))
    }

    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithError: NSError) {
      IosUi.release(this)
      //  کاربر خودش بست — نه خطا، نه پیام (مثلِ `GetCredentialCancellationException` ِ اندروید)
      if (didCompleteWithError.code == ASAuthorizationErrorCanceled) {
        if (cont.isActive) cont.resume(null)
      } else {
        resumeFail(didCompleteWithError.localizedDescription.take(160))
      }
    }

    override fun presentationAnchorForAuthorizationController(controller: ASAuthorizationController): ASPresentationAnchor =
      IosUi.top()?.view?.window ?: (UIApplication.sharedApplication.keyWindow ?: UIWindow())

    private fun resumeFail(detail: String) {
      if (cont.isActive) cont.resumeWithException(IllegalStateException("ورود با اپل انجام نشد — $detail"))
    }
  }

  private fun utf8(data: NSData): String? = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()

  /** SHA-256 ِ هگزِ کوچک — همان `nonceHash` ِ سرور */
  internal fun sha256Hex(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray()).joinToString("") {
      (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }

  /** ادعای `email` ِ توکن (بی سنجش؛ فقط برای نمایش — سنجش کارِ سرور است) */
  internal fun emailOf(token: String): String = runCatching {
    val part = token.split('.')[1].replace('-', '+').replace('_', '/')
    val padded = part + "=".repeat((4 - part.length % 4) % 4)
    val json = NSData.create(base64EncodedString = padded, options = 0u)?.let { utf8(it) } ?: return@runCatching ""
    Json.parseToJsonElement(json).jsonObject["email"]?.jsonPrimitive?.content.orEmpty()
  }.getOrDefault("")
}
