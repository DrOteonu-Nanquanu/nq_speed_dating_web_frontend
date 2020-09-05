package services.database

import java.sql.{Connection, ResultSet}

import akka.actor.ActorSystem
import javax.inject._
import models.Interest_level.Interest_level
import models.{Field_Of_Expertise, Interest_level, Nq_project, User}
import models.database.Database_ID
import play.api.db.Database

import scala.concurrent.Future
import play.api.libs.concurrent.CustomExecutionContext

@Singleton
class DatabaseExecutionContext @Inject()(system: ActorSystem) extends CustomExecutionContext(system, "database.dispatcher")

@Singleton
class ScalaApplicationDatabase @Inject() (db: Database)(implicit databaseExecutionContext: DatabaseExecutionContext) {
  // Returns the username, id and password hash of a user
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

  // Creates a new user and returns true if the username is unique. Otherwise, no user is created and false is returned
  def create_new_user(username: String, password_hash: String) = {
    Future {
      db.withConnection(connection => {
        // TODO: Check if username is already used
        val username_exists = {
          val sql =
            """
              |SELECT id
              |FROM nq_user
              |WHERE username = ?
              |""".stripMargin

          val stmt = connection.prepareStatement(sql)
          stmt.setString(1, username)

          val query_result = stmt.executeQuery()

          query_result.next() // Whether there is at least one user found with the same username
        }

        if(username_exists) {
          false
        }
        else {
          val sql =
            """
              |INSERT INTO nq_user(username, password_hash)
              |VALUES (?, ?)""".stripMargin

          val statement = connection.prepareStatement(sql)
          statement.setString(1, username)
          statement.setString(2, password_hash)

          statement.executeUpdate()

          true
        }
      })
    }
  }

  // Returns the id of a user given a username, or None if there is no user with that username
  def get_user_id(username: String): Future[Option[Database_ID]] = {
    Future {
      db.withConnection(connection => {
        val sql =
          """
            |SELECT id
            |FROM nq_user
            |WHERE username = ?""".stripMargin

        val statement = connection.prepareStatement(sql)
        statement.setString(1, username)

        val query_result = statement.executeQuery()

        if (query_result.next()) {
          Some(Database_ID(query_result.getInt(1)))
        }
        else {
          None
        }
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

  private def next_foi_parent_inner(user_id: Database_ID, connection: Connection) = {
    val sql =
      s"""
        |SELECT parent.id AS parent_id
        |FROM field_of_interest parent, field_of_interest child
        |WHERE
          |child.parent_id = parent.id AND
          |NOT EXISTS(
            |SELECT *
            |FROM interest_level
            |WHERE
              |interest_level.user_id = ? AND
              |interest_level.interest_id = child.id
          |) AND
          |parent.depth_in_tree IN(
            |SELECT current_depth_in_tree FROM nq_user WHERE id = ?
          |)
          |AND (parent.depth_in_tree = 0 OR parent.id IN(
            |SELECT interest_id
            |FROM interest_level
            |WHERE
              |user_id = ? AND
              |NOT level_of_interest = ${Interest_level.no_interest.id}
          |));
        |""".stripMargin

    val stmt = connection.prepareStatement(sql)
    stmt.setInt(1, user_id.id)
    stmt.setInt(2, user_id.id)
    stmt.setInt(3, user_id.id)

    val query_result = stmt.executeQuery()

    if (query_result.next()) {
      Some(Database_ID(query_result.getInt("parent_id")))
    }
    else {
      None
    }
  }

  def next_foi_parent(user_id: Database_ID): Future[Option[Database_ID]] = {
    Future {
      db.withConnection(connection => {
        next_foi_parent_inner(user_id, connection) match {
          case s: Some[Database_ID] => s
          case None => {
            // Update the current_depth_in_tree of the user
            val update_sql =
              """
                |UPDATE nq_user SET current_depth_in_tree = current_depth_in_tree + 1 WHERE id = ?;
                |""".stripMargin
            val update_statement = connection.prepareStatement(update_sql)
            update_statement.setInt(1, user_id.id)

            update_statement.executeUpdate()

            // Query again, but with updated current_depth_in_tree
            next_foi_parent_inner(user_id, connection) match {
              case s: Some[Database_ID] => s
              case None => None // The user filled in everything
            }
          }
        }
      })
    }
  }

  def set_foi_parent(user_id: Database_ID, foi_parent_id: Database_ID) = {
    Future {
      db.withConnection(connection => {

        println(user_id.id)
        println(foi_parent_id.id)

        val sql =
          """
            |UPDATE nq_user SET current_parent_id = ? WHERE id = ? ;
            |""".stripMargin

        val stmt = connection.prepareStatement(sql)
        stmt.setInt(1, foi_parent_id.id)
        stmt.setInt(2, user_id.id)

        stmt.executeUpdate()

        ()
      })
    }
  }

  def get_current_fois(user_id: Database_ID): Future[List[Field_Of_Expertise]] = Future {
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

  def get_current_nq_projects(user_id: Database_ID): Future[List[Nq_project]] = Future {
    db.withConnection(connection => {
      val sql =
        """
          |SELECT project.name, project.id, project.description
          |FROM nq_project project
          |INNER JOIN project_interesting_to pio
          |ON project.id = pio.nq_project_id
          |INNER JOIN nq_user
          |ON nq_user.current_parent_id = pio.field_of_interest_id
          |WHERE nq_user.id = ?;
          |""".stripMargin

      val stmt = connection.prepareStatement(sql)
      stmt.setInt(1, user_id.id)

      val query_result = stmt.executeQuery()

      var result = List[Nq_project]()

      while(query_result.next()) {
        result ::= Nq_project(query_result.getString(1), Database_ID(query_result.getInt(2)), None, query_result.getString(3))
      }

      result
    })
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