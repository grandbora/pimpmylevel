package auth

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow.Builder
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.twitter.util.Try

import scala.collection.JavaConverters._
import scala.util.Properties

class GoogleAuthentication {

  private val GOOGLE_CLIENT_ID = Properties.envOrNone("GOOGLE_CLIENT_ID").get
  private val GOOGLE_CLIENT_SECRET = Properties.envOrNone("GOOGLE_CLIENT_SECRET").get
  private val APP_HOST = Properties.envOrNone("APP_HOST").get

  private val authFlow = new Builder(new NetHttpTransport, new JacksonFactory, GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET,
    List("profile", "email").asJava).build

  val redirectUrl = authFlow.newAuthorizationUrl
    .setRedirectUri(s"$APP_HOST/google-auth-callback")
    .build


  def tokenResponse(code: String) = {
    val tokenRequest = authFlow.newTokenRequest(code).setRedirectUri(s"$APP_HOST/google-auth-callback")
    Try(tokenRequest.execute()).toOption.map{
      resp =>
        val payload = resp.parseIdToken().getPayload
        (payload.getSubject, payload.getEmail)
    }
  }
}
