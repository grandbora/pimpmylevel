import com.twitter.finagle.exp.mysql.{ResultSet, StringValue}
import com.twitter.util.Future
import play.api.libs.json.{JsArray, JsObject, JsString}

class Nominator {

  private val nominateQuery = """INSERT INTO `nominations` (`user`, `nominee`) VALUES (?, ?)"""

  private val allNominationsPerNominatorQuery = """SELECT `user`, `nominee` from `nominations` ORDER BY `user`"""

  def nominate(user: String, nominee: String) = {
    MysqlClient.richClient.prepare(nominateQuery)(user, nominee).map {
      resultSet =>
        "ok"
    }
  }

  def allNominationsPerNominator: Future[String] = {
    MysqlClient.richClient.prepare(allNominationsPerNominatorQuery)().map {
      case rs: ResultSet =>
        val userToNominee = rs.rows.map {
          row =>
            row("user").get -> row("nominee").get
        }

        val userToNomineeJson = userToNominee.groupBy(_._1).map {
          case (user, nominations) =>
            val nominationIds = nominations.map(_._2).map(_.asInstanceOf[StringValue].s).map{id => JsString(id)}
            user.asInstanceOf[StringValue].s -> JsArray(nominationIds)
        }

        JsObject(userToNomineeJson.toSeq).toString()

      case _ => "{}"
    }
  }

}
