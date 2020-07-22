package services.database

import java.sql.ResultSet

import akka.actor.ActorSystem
import javax.inject._
import models.User
import models.database.Database_ID
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

  def get_user_verification_data(username: String): Future[List[User]] = {
    Future {
      db.withConnection(connection => {
        val sql =
          """
            |SELECT *
            |FROM nq_user
            |WHERE username = ?""".stripMargin

        val statement = connection.prepareStatement(sql)
        statement.setString(1, username)

        val query_result = statement.executeQuery()
        var result_data = List[User]()

        while(query_result.next()) {
          val user = User(
            Database_ID(query_result.getInt("id")),
            query_result.getString("username"),
            query_result.getString("password_hash"),
          )

          result_data = user :: result_data
        }

        result_data
      })
    }
  }

  def create_new_user(username: String, password_hash: String) = {
    Future {
      db.withConnection(connection => {
        val sql =
          """
            |INSERT INTO nq_user(username, password_hash)
            |VALUES (?, ?)""".stripMargin


        val statement = connection.prepareStatement(sql)
        statement.setString(1, username)
        statement.setString(2, password_hash)

        statement.executeUpdate()

        ()
      })
    }
  }

/*  def query[R](sql: String, result_processor: ResultSet => R): Future[List[R]] = Future {
    db.withConnection(connection => {

      val statement = connection.prepareStatement(sql)



      val query_result = statement.executeQuery()

      var result = List[R]()

      while(query_result.next()) {
        result = result_processor(query_result) :: result
      }

      result
    })
  }*/
}