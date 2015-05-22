package auth

import com.twitter.finagle.http.Response
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http.HttpRequest

import scala.util.Properties

class BasicAuthentication {

  private val scPass = Properties.envOrNone("SC_PASSWORD").get

  def isAuthenticated(req: HttpRequest): Boolean = {
    req.headers.get("Authorization") == s"Basic $scPass"
  }

  def requestPassword: Future[Response] = {
    val response = Response()
    response.setStatusCode(401)
    response.headers.set("WWW-Authenticate", "Basic")
    Future(response)
  }

  def authenticate(req: HttpRequest)(f: HttpRequest => Future[Response]) = {
    isAuthenticated(req) match {
      case false => requestPassword
      case true => f(req)
    }
  }
}
