import com.twitter.finagle.Service
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.exp.mysql.StringValue
import com.twitter.finagle.http.{Http, RequestBuilder}
import com.twitter.util.Future
import org.apache.commons.codec.binary.Base64
import org.jboss.netty.handler.codec.http.{HttpRequest, HttpResponse}
import org.joda.time.DateTime
import play.api.libs.json._

import scala.util.Properties
import scala.xml.NodeSeq

class DataScraper {

  private val readScrapeQuery = """SELECT `data` FROM scrapes ORDER BY ID DESC LIMIT 1"""

  var bambooData: Option[JsObject] = None

  def scrapeBamboo: Future[JsObject] = {
    client(req).flatMap {
      res =>
        println("got response", res)
        println("BODY")
        val bodyText = new String(res.getContent.toByteBuffer.array(), "UTF-8")
        val rootElm = scala.xml.XML.loadString(bodyText)
        println(bodyText)
        val users = parseUsers(rootElm \\ "user")
        users.map(d => bambooData = Some(d))
        users
    }
  }

  private val userAndPass = Properties.envOrNone("USER_PASS").get

  private val authEncBytes = Base64.encodeBase64(userAndPass.getBytes())
  private val authStringEnc = new String(authEncBytes)

  private val client: Service[HttpRequest, HttpResponse] = ClientBuilder()
    .codec(Http())
    .hosts("api.bamboohr.com:443")
    .tls("api.bamboohr.com")
    .hostConnectionLimit(1)
    .build()

  private val req = {
    val r = RequestBuilder().url(s"https://api.bamboohr.com/api/gateway.php/soundcloud/v1/meta/users").buildGet
    r.headers().add("Authorization", "Basic " + authStringEnc)
    r
  }

  private def parseUsers(users: NodeSeq): Future[JsObject] = {
    val parsedUsers = users.map {
      u =>
        u.attribute("employeeId").flatMap(_.headOption).map(_.text) match {
          case None => Future.None
          case Some(employeeId) =>
            val userReq = RequestBuilder().url(s"https://api.bamboohr.com/api/gateway.php/soundcloud/v1/employees/$employeeId?fields=jobTitle,fullName1,fullName3,displayName,workEmail,birthday,hireDate").buildGet
            userReq.headers().add("Authorization", "Basic " + authStringEnc)
            userReq.headers().add("Accept", "application/json")

            client(userReq).flatMap {
              r =>
                val user = Some(new String(r.getContent.toByteBuffer.array(), "UTF-8")).filterNot(_.isEmpty)
                val titleRegexp = """.* \((\d)\)""".r

                println("got response", r)
                println(user)

                user.flatMap(u => (Json.parse(u) \ "jobTitle").asOpt[String]) match {
                  case Some(title@titleRegexp(level)) =>

                    println("LEVEL : TITLE")
                    println(level)
                    println(title)

                    val parsedUser = Json.parse(user.get).as[JsObject]
                    val userId = (parsedUser \ "id").as[String]

                    Future.value(Some(userId -> parsedUser.+("level" -> JsNumber(level.toInt))))
                  case _ => Future.None
                }
            }
        }
    }

    Future.collect(parsedUsers).map(_.flatten).map {
      idToUser =>

        println("DONE!! collected all the users")

        JsObject(Seq(
          "created" -> JsString(DateTime.now.toString("yyyy-MM-dd HH:mm:ss")),
          "users" -> JsObject(idToUser)
        ))
    }
  }

  def loadOrFallback: Future[String] = {
    bambooData match {
      case Some(data) => Future.value(data.toString)
      case None => fetchScrapeFromDb

    }
  }

  def loadWithFallbackAndScrape: Future[String] = {
    bambooData match {
      case Some(data) => Future.value(data.toString)
      case None =>
        scrapeBamboo
        fetchScrapeFromDb
    }
  }

  private def fetchScrapeFromDb: Future[String] = {
    (MysqlClient.richClient.select(readScrapeQuery) {
      row =>
        row("data").get.asInstanceOf[StringValue].s
    }).map(_.head)
  }
}
