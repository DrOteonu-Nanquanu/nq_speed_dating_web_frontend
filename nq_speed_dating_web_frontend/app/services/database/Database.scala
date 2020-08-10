package services.database

import java.sql.ResultSet

import akka.actor.ActorSystem
import javax.inject._
import models.Interest_level.Interest_level
import models.{Field_Of_Expertise, Interest_level, User}
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
        // Check if username is already used
        val username_exists = {

        }

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

  def set_interesting_to(user_id: Database_ID, interest_id: Database_ID, level_of_interest: Interest_level): Unit = {
    Future {
      db.withConnection(connection => {
        val update_sql =
          """
            |UPDATE interest_level SET level_of_interest = ? WHERE user_id = ? AND interest_id = ?;
            |""".stripMargin

        val update_stmt = connection.prepareStatement(update_sql)
        update_stmt.setInt(1, level_of_interest.id)
        update_stmt.setInt(2, user_id.id)
        update_stmt.setInt(3, interest_id.id)

        val updated_rows = update_stmt.executeUpdate()

        updated_rows match {
          case 0 => {
            // Execute INSERT statement
            val insert_sql =
              """
                |INSERT INTO interest_level(user_id, interest_id, level_of_interest) VALUES (?, ?, ?);
                |""".stripMargin

            val insert_stmt = connection.prepareStatement(insert_sql)

            insert_stmt.setInt(1, user_id.id)
            insert_stmt.setInt(2, interest_id.id)
            insert_stmt.setInt(3, level_of_interest.id)

            insert_stmt.executeUpdate()
          }
          case 1 => ()
          case _ => () // TODO ERROR: there should be only one
        }
      })
    }
  }

  def next_foi_parent(username_id: Database_ID) = {
    Future {
      db.withConnection(connection => {
        val sql =
          """
            |SELECT parent.id AS parent_id
            |FROM field_of_interest parent, field_of_interest child
            |WHERE
              |child.parent_id = parent.id AND
              |NOT EXISTS(
                |SELECT *
                |FROM interest_level
                |WHERE interest_level.user_id = ?
              |);
            |""".stripMargin

        val stmt = connection.prepareStatement(sql)
        stmt.setInt(1, username_id.id)

        val query_result = stmt.executeQuery()

        if (query_result.next()) {
          Some(Database_ID(query_result.getInt("parent_id")))
        }
        else {
          // TODO: No results
          None
        }
      })
    }
  }

  def set_foi_parent(user_id: Database_ID, foi_parent_id: Database_ID) = {
    Future {
      db.withConnection(connection => {
        val sql =
          """
            |UPDATE nq_user WHERE id = ? SET current_parent_id = ?
            |""".stripMargin

        val stmt = connection.prepareStatement(sql)
        stmt.setInt(1, user_id.id)
        stmt.setInt(2, foi_parent_id.id)

        stmt.executeUpdate()
      })
    }
  }

  def get_current_fois(user_id: Database_ID): Future[List[Field_Of_Expertise]] = {
    Future {
      db.withConnection(connection => {
        val sql =
          """
            |SELECT field_of_interest.name AS name, field_of_interest.id AS id, interest_level.level_of_interest AS level_of_interest
            |FROM field_of_interest
            |INNER JOIN nq_user ON field_of_interest.parent_id = nq_user.current_parent_id
            |LEFT JOIN interest_level ON nq_user.id = interest_level.user_id AND field_of_interest.id = interest_level.interest_id
            |WHERE nq_user.id = ?;
            |""".stripMargin

        val stmt = connection.prepareStatement(sql)
        stmt.setInt(1, user_id.id)

        val query_result = stmt.executeQuery()

        var result = List[Field_Of_Expertise]()

        while(query_result.next()) {
          val maybe_level_of_interest = query_result.getString("level_of_interest") match { // Using getString because getInt doesn't return null but 0 when it isn't present
            case null => None
            case value => Some(
              Interest_level(value.toInt)
            )
          }
          result ::= Field_Of_Expertise(query_result.getString("name"), Database_ID(query_result.getInt("id")), maybe_level_of_interest)
        }

        result
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