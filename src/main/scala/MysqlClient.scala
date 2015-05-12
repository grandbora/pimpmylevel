import com.twitter.finagle.client.DefaultPool
import com.twitter.finagle.exp.Mysql
import com.twitter.util.Duration

import scala.util.Properties

object MysqlClient {

  private val dbUser = Properties.envOrNone("CLEARDB_DATABASE_URL_USER").get
  private val dbPassword = Properties.envOrNone("CLEARDB_DATABASE_URL_PASSWORD").get
  private val dbName = Properties.envOrNone("CLEARDB_DATABASE_URL_DB").get
  private val dbHost = Properties.envOrNone("CLEARDB_DATABASE_URL_HOST").get

  val richClient = Mysql.client
    .withCredentials(dbUser, dbPassword)
    .withDatabase(dbName)
    .configured(DefaultPool.Param(
    low = 0, high = 10,
    idleTime = Duration.Top,
    bufferSize = 0,
    maxWaiters = Int.MaxValue))
    .newRichClient(dbHost)
}
