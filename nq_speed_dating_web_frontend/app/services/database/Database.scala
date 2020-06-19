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
  def print_project_names() = {
    println("print_project_names")

    Future {
      println("print_project_names Future")
      db.withConnection(connection => {
        println("print_project_names Future Connection")

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

        1 + 1
      })
    }(databaseExecutionContext)
  }
}