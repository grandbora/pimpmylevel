import com.twitter.finagle.exp.mysql.{IntValue, ResultSet, StringValue}
import com.twitter.util.Future
import play.api.libs.json.{JsArray, JsNumber, JsObject, JsString}

class Voter {

  private val updateQuery = """UPDATE `votes` SET `count`=`count` + 1 WHERE `user_id` = ?"""

  private val selectUserQuery = """SELECT * FROM `votes` WHERE `user_id` = ?"""

  private val insertUserQuery = """INSERT INTO `votes` (`user_id`) VALUES (?)"""

  private val selectAllUsersQuery = """SELECT `user_id`, `count` FROM `votes` ORDER BY `count` DESC"""

  def upvote(userId: String) = {

    createIfDoesNotExist(userId).flatMap {
      _ =>
        MysqlClient.richClient.prepare(updateQuery).apply(userId).map {
          resultSet =>
            "ok"
        }
    }
  }

  private def createIfDoesNotExist(userId: String) = {
    MysqlClient.richClient.prepare(selectUserQuery).apply(userId).flatMap {
      case res@ResultSet(_, x +: xs) =>
        println(s"found votes entry for $userId")
        Future.value(res)
      case _ =>
        println(s"could not found votes entry for $userId, creating new one")
        MysqlClient.richClient.prepare(insertUserQuery).apply(userId)
    }
  }

  def totalVotes: Future[String] = {

    val userIdToVoteCountFuture = MysqlClient.richClient.prepare(selectAllUsersQuery).select() {
      row =>
        row("user_id").get.asInstanceOf[StringValue].s -> row("count").get.asInstanceOf[IntValue].i
    }
    userIdToVoteCountFuture.map {
      userIdToVoteCount =>
        JsArray(userIdToVoteCount.map { case (userId, count) => JsObject(Seq("userId" -> JsString(userId), "votes" -> JsNumber(count))) }).toString
    }
  }
}
