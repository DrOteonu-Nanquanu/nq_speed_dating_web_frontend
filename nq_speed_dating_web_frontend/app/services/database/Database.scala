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

  // Sets the level_of_interest of the specified user to the specified level. If a level of interest was already specified, it will update it. If there isn't one, it will create a new level_of_interest record.
  def set_foi_interesting_to(user_id: Database_ID, interest_id: Database_ID, affinities: List[Interest_level]): Future[Unit] = {
    Future {
      db.withConnection(connection => {
        // First, remove existing records for this user and interest
        val update_sql =
          s"""
            |DELETE FROM interest_level WHERE user_id = ? AND interest_id = ?;
            |""".stripMargin

        val update_stmt = connection.prepareStatement(update_sql)
        update_stmt.setInt(1, user_id.id)
        update_stmt.setInt(2, interest_id.id)

        update_stmt.executeUpdate()

        // Insert the new affinities
        val insert_patterns = affinities.map(_ => "(?, ?, ?)").mkString(" ", ", ", ";")

        val insert_sql =
          "INSERT INTO interest_level(user_id, interest_id, level_of_interest) VALUES" ++ insert_patterns;

        val insert_stmt = connection.prepareStatement(insert_sql)

        for((level_of_interest, i) <- affinities.zipWithIndex) {
          insert_stmt.setInt(1 + i * 3, user_id.id)
          insert_stmt.setInt(2 + i * 3, interest_id.id)
          insert_stmt.setInt(3 + i * 3, level_of_interest.id)
        }

        insert_stmt.executeUpdate()
      })
    }
  }

  def set_project_interesting_to(user_id: Database_ID, project_id: Database_ID, affinities: List[Interest_level]): Future[Unit] = {
    Future {
      db.withConnection(connection => {
        // First, remove existing records for this user and project
        val update_sql =
          s"""
             |REMOVE FROM project_interest_level WHERE user_id = ? AND project_id = ?;
             |""".stripMargin

        val update_stmt = connection.prepareStatement(update_sql)
        update_stmt.setInt(1, user_id.id)
        update_stmt.setInt(2, project_id.id)

        update_stmt.executeUpdate()
        // Insert new records

        for(affinity <- affinities) {
          val insert_sql =
            s"""
               |INSERT INTO project_interest_level(user_id, project_id, level_of_interest, first_parent_foi)
               |SELECT id, ?, ?, current_parent_id
               |FROM nq_user
               |WHERE id = ?;
               |""".stripMargin

          val insert_stmt = connection.prepareStatement(insert_sql)

          insert_stmt.setInt(1, project_id.id)
          insert_stmt.setInt(2, affinity.id)
          insert_stmt.setInt(3, user_id.id)

          insert_stmt.executeUpdate()
        }
      })
    }
  }

  // Used in next_foi_parent
  private def next_foi_parent_inner(user_id: Database_ID, connection: Connection) = {
    val sql =
      s"""
        |SELECT parent.id
        |FROM field_of_interest parent
        |WHERE
          |(
            |EXISTS (                                                          -- Test if there are any nq_projects related to parent
              |SELECT *
              |FROM project_interesting_to pit
              |INNER JOIN nq_project ON nq_project.id = pit.nq_project_id
              |WHERE
                |pit.field_of_interest_id = parent.id AND
                |NOT EXISTS (                                                 -- The nq_project should not have an assigned level of interest yet
                  |SELECT *
                  |FROM project_interest_level pil
                  |WHERE pil.user_id = ? AND pil.project_id = nq_project.id
                |)
            |)
            |OR
            |EXISTS (                                                         -- Test if the parent has any child FOI's
              |SELECT *
              |FROM field_of_interest child
              |WHERE
                |child.parent_id = parent.id AND
                |NOT EXISTS (                                                 -- The children should not have an assigned level of interest yet
                  |SELECT *
                  |FROM interest_level il
                  |WHERE il.interest_id = child.id AND il.user_id = ?
                |)
            |)
          |) AND
          |parent.depth_in_tree IN (                                           -- Make sure the user descends the hierarchy in a breadth-first manner
            |SELECT current_depth_in_tree FROM nq_user WHERE id = ?
          |)
          |AND (parent.depth_in_tree = 0 OR parent.id IN (                     -- The parent should have a level_of_interest specified other than no_interest
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
    stmt.setInt(4, user_id.id)

    val query_result = stmt.executeQuery()

    if (query_result.next()) {
      Some(Database_ID(query_result.getInt(1)))
    }
    else {
      None
    }
  }

  // Moves to the next parent FOI whose children and related projects will be filled in by the user. Returns the database_id of the new parent, or None if there is no next parent. (Which means that the user is done filling in the form)
  def next_foi_parent(user_id: Database_ID): Future[Option[Database_ID]] = {
    Future {
      db.withConnection(connection => {
        // Try to find a next parent at the current level of the hierarchy
        next_foi_parent_inner(user_id, connection) match {
          case s: Some[Database_ID] => s
          case None => {
            // If no parent is found at the current level, search one level deeper
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
              case None => None // If still no parent was found, the user must have filled in everything
            }
          }
        }
      })
    }
  }

  // Sets the next parent whose children will be filled in by the user.
  def set_foi_parent(user_id: Database_ID, foi_parent_id: Database_ID): Future[Unit] = {
    Future {
      db.withConnection(connection => {
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

  // Find the FOI's that the user currently must fill in, and their assigned level_of interest.
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
        val maybe_level_of_interest = nullable_string_to_optional_interest_level(query_result.getString("level_of_interest"))
        result ::= Field_Of_Expertise(query_result.getString("name"), Database_ID(query_result.getInt("id")), maybe_level_of_interest)
      }

      result
    })
  }

  // nullable_string must either be null or be parsable into an integer corresponding to some Interest_level
  def nullable_string_to_optional_interest_level(nullable_string: String): Option[models.Interest_level.Value] = nullable_string match {
    case null => None
    case value => Some(
      Interest_level(value.toInt)
    )
  }

  // Find the projects that the user currently must fill in, and their assigned level_of interest.
  def get_current_nq_projects(user_id: Database_ID): Future[List[Nq_project]] = Future {
    db.withConnection(connection => {
      val sql =
        """
          |SELECT project.name, project.id, project.description, pil.level_of_interest
          |FROM nq_project project
          |INNER JOIN project_interesting_to pio
          |ON project.id = pio.nq_project_id
          |INNER JOIN nq_user
          |ON nq_user.current_parent_id = pio.field_of_interest_id
          |LEFT JOIN project_interest_level pil ON project.id = pil.project_id AND pil.user_id = nq_user.id
          |WHERE nq_user.id = ? AND (pil.first_parent_foi IS NULL OR pil.first_parent_foi = nq_user.current_parent_id);
          |""".stripMargin

      val stmt = connection.prepareStatement(sql)
      stmt.setInt(1, user_id.id)

      val query_result = stmt.executeQuery()

      var result = List[Nq_project]()

      while(query_result.next()) {
        val maybe_level_of_interest = nullable_string_to_optional_interest_level(query_result.getString(4))

        result ::= Nq_project(query_result.getString(1), Database_ID(query_result.getInt(2)), maybe_level_of_interest, query_result.getString(3))
      }

      result
    })
  }
}