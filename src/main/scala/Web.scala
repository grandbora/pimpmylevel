import java.net.InetSocketAddress

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
  val authenticator = new Authenticator
  val dataScraper = new DataScraper
  val voter = new Voter

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

      case "/scrape" =>
        authenticator.authenticate(req) {
          req =>
            val response = Response()
            response.setStatusCode(200)
            response.setContentString("creating data.json")
            dataScraper.scrapeBamboo
            Future(response)
        }

      case "/data" =>
        authenticator.authenticate(req) {
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

      case "/" =>
        authenticator.authenticate(req) {
          req =>
            val response = Response()
            response.setStatusCode(200)
            response.setContentType("text/html")
            dataScraper.loadOrFallback.map {
              scrapedData =>
                val template = scala.io.Source.fromFile("src/main/resources/template/index.html").mkString
                  .replace("{{bambooData}}", scrapedData)
                response.setContentString(template)
                response
            }
        }

      case "/top" =>
        authenticator.authenticate(req) {
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
    }
  }
}
