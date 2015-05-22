import java.net.InetSocketAddress

import auth.{BasicAuthentication, GoogleAuthentication}
import com.twitter.finagle.Service
import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.http.{Http, Response}
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http._

import scala.util.Properties

object Web {
  val port = Properties.envOrElse("PORT", "8080").toInt

  def main(args: Array[String]) {

    println("Starting on port:" + port)

    ServerBuilder()
      .codec(Http())
      .name("pimp-server")
      .bindTo(new InetSocketAddress(port))
      .build(new Pimp)
  }
}

class Pimp extends Service[HttpRequest, HttpResponse] {
  val basicAuthentication = new BasicAuthentication
  val googleAuthentication = new GoogleAuthentication
  val dataScraper = new DataScraper
  val voter = new Voter

  val googleAuthCallbackPattern = """\/google-auth-callback\?code=(.*)""".r

  def apply(req: HttpRequest): Future[HttpResponse] = {

    println("REQ GOT")
    println(req.getUri)

    req.getUri match {
      case "/favicon.ico" =>
        val response = Response()
        response.setStatusCode(200)
        response.setContentString(":-)")
        Future(response)

      case "/jquery" =>
        val response = Response()
        response.setStatusCode(200)
        response.setContentType("application/x-javascript")
        val jquery = scala.io.Source.fromFile("src/main/resources/asset/jquery").mkString
        response.setContentString(jquery)
        Future(response)

      case "/" =>
        val response = Response()
        response.setStatusCode(301)
        response.headers.set("Location", googleAuthentication.redirectUrl)
        Future(response)

      case googleAuthCallbackPattern(code) =>
        googleAuthentication.tokenResponse(code) match {
          case Some((userId, email)) =>
            val response = Response()
            response.setStatusCode(200)
            response.setContentType("text/html")
            dataScraper.loadOrFallback.map {
              scrapedData =>
                val template = scala.io.Source.fromFile("src/main/resources/template/index.html").mkString
                  .replace("{{bambooData}}", scrapedData)
                  .replace("{{userEmail}}", email)
                response.setContentString(template)
                response
            }

          case None =>
            val response = Response()
            response.setStatusCode(200)
            response.setContentString("Yikes. It's borked")
            Future(response)
        }

      case "/scrape" =>
        basicAuthentication.authenticate(req) {
          req =>
            val response = Response()
            response.setStatusCode(200)
            response.setContentString("creating data.json")
            dataScraper.scrapeBamboo
            Future(response)
        }

      case "/data" =>
        basicAuthentication.authenticate(req) {
          req =>
            dataScraper.loadWithFallbackAndScrape.map {
              scrapedData =>
                val response = Response()
                response.setStatusCode(200)
                response.setContentType("application/json")
                response.setContentString(scrapedData)
                response
            }
        }

      case "/top" =>
        basicAuthentication.authenticate(req) {
          req =>
            voter.totalVotes.flatMap {
              voteData =>
                dataScraper.loadOrFallback.map {
                  scrapeData =>
                    val response = Response()
                    response.setStatusCode(200)
                    response.setContentType("text/html")
                    val template = scala.io.Source.fromFile("src/main/resources/template/top.html").mkString
                      .replace("{{bambooData}}", scrapeData)
                      .replace("{{voteData}}", voteData)
                    response.setContentString(template)
                    response
                }
            }
        }

      case "/upvote" =>
        val userId = new String(req.getContent.toByteBuffer.array(), "UTF-8")
        voter.upvote(userId).map {
          res =>
            val response = Response()
            response.setStatusCode(200)
            response.setContentType("text/html")
            response.setContentString("OK")
            response
        }

      case _ =>
        val response = Response()
        response.setStatusCode(200)
        response.setContentType("text/html")
        response.setContentString(s"Could not find any route for path ${req.getUri}")
        Future.value(response)
    }
  }
}
