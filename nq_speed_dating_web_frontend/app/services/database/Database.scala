package services.database

import akka.actor.ActorSystem
import javax.inject._
import play.api.db.Database

import scala.concurrent.Future
import play.api.libs.concurrent.CustomExecutionContext

@Singleton
class DatabaseExecutionContext @Inject()(system: ActorSystem) extends CustomExecutionContext(system, "database.dispatcher")

@Singleton
class ScalaApplicationDatabase @Inject() (db: Database)(implicit databaseExecutionContext: DatabaseExecutionContext) {
  def print_project_names(): Unit = {
    Future {
      db.withConnection { connection =>

        val stmt = connection.createStatement();
        val sql =
          """
            |SELECT *
            |FROM field_of_interest;""".stripMargin
        var query_result = stmt.executeQuery(sql)

        while(query_result.next()) {
          val name = query_result.getString("name")
          println(name)
        }

        stmt.close()
      }
    }(databaseExecutionContext)
  }
}