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

import models.{Affinity, ProjectAffinity, TopicAffinity}

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
  def set_topic_interesting_to(user_id: Database_ID, interest_id: Database_ID, affinities: List[Interest_level]): Future[Unit] = {
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

        println(affinities)

        if(!affinities.isEmpty) {
          // Insert the new affinities
          val insert_patterns = affinities.map(_ => "(?, ?, ?)").mkString(" ", ", ", ";")

          val insert_sql =
            "INSERT INTO interest_level(user_id, interest_id, level_of_interest) VALUES" ++ insert_patterns;

          println(insert_sql)

          val insert_stmt = connection.prepareStatement(insert_sql)

          for((level_of_interest, i) <- affinities.zipWithIndex) {
            println(f"$level_of_interest, index: $i")
            insert_stmt.setInt(1 + i * 3, user_id.id)
            insert_stmt.setInt(2 + i * 3, interest_id.id)
            insert_stmt.setInt(3 + i * 3, level_of_interest.id)
          }

          insert_stmt.executeUpdate()

          println("executed update")
        }
      })
    }
  }

  def set_project_interesting_to(user_id: Database_ID, project_id: Database_ID, affinities: List[Interest_level]): Future[Unit] = {
    Future {
      db.withConnection(connection => {
        // First, remove existing records for this user and project
        val update_sql =
          s"""
             |DELETE FROM project_interest_level WHERE user_id = ? AND project_id = ?;
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

  // Used in next_topic_parent
  private def next_topic_parent_inner(user_id: Database_ID, connection: Connection) = {
    // TODO: look at submitted level_of_affinity's rather than those selected in the UI
    // TODO: maybe add a SORT BY to ensure the first result is always the same, or do something with GROUP BY instead?
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
            |EXISTS (                                                         -- Test if the parent has any child topic's
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
          |TRUE IN (                                                          -- Make sure the user descends the hierarchy in a breadth-first manner
            |--TODO: This is somehow wrong. TRUE IN (...) seems to always evaluate to TRUE?
            |SELECT current_depth_in_tree >= parent.depth_in_tree FROM nq_user WHERE id = ?
          |)
          |AND (parent.depth_in_tree = 0 OR parent.id IN (                   -- The parent should have a level_of_interest specified other than no_interest
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

  def argmax_sql(table: String, select_columns: String, max_column: String): String = argmax_sql(table, List(select_columns), max_column)

  // Constructs a query that returns the values of the select_columns of the records where max_column = MAX(max_column). If table is a subquery, it's executed twice (if not optimized by the RDMS).
  def argmax_sql(table: String, select_columns: List[String], max_column: String): String = 
  {
    val selections = select_columns.map("t1." + _).mkString(",")
    s"""
    |SELECT $selections
    |FROM $table t1
    |WHERE t1.$max_column = (SELECT MAX(t2.$max_column) FROM $table t2)
    """.stripMargin
  }

  // Updates which topics or projects the user is editing
  def update_editing_topics_projects(user_id: Database_ID, affinity_type: Affinity) = Future {
    db.withConnection(connection => {
      println("update_editing_topics_projects")

      val clear_sql = s"""
      |DELETE FROM ${affinity_type.user_edits_table}
      |WHERE user_id = ?
      """.stripMargin

      println(clear_sql)
      val clear_stmt = connection.prepareStatement(clear_sql)
      clear_stmt.setInt(1, user_id.id);
      
      println("executing clear_stmt")
      clear_stmt.executeUpdate()
      println("done executing clear_stmt")

      // TODO: the argmax(possible_parents) part can optimized to only be calculated once when this function is called twice with a different affinity_type argument
      
      val insert_part = if(affinity_type.name == "topic") {
        f"""
        INSERT INTO ${affinity_type.user_edits_table}(user_id, ${affinity_type.edit_state_table_id_column})
        SELECT ?, next.id
        FROM ${affinity_type.general_info_table} next
        WHERE next.parent_id = (
          SELECT MIN(possible_parent.parent_id)
          FROM (
            ${argmax_sql("possible_parents", "parent_id", "parent_depth_in_tree")}             -- Select the topics that are valid topics and have the lowest depth_in_tree
          ) AS possible_parent
        )
        """.stripMargin
      } else {
        f"""
        INSERT INTO ${affinity_type.user_edits_table}(user_id, ${affinity_type.edit_state_table_id_column})
        SELECT ?, pit.nq_project_id
        FROM project_interesting_to AS pit
        WHERE pit.field_of_interest_id = (
          SELECT MIN(possible_parent.parent_id)
          FROM (
            ${argmax_sql("possible_parents", "parent_id", "parent_depth_in_tree")}             -- Select the topics that are valid topics and have the lowest depth_in_tree
          ) AS possible_parent
        )
        """.stripMargin
      }

      val project_affinity = ProjectAffinity()
      val topic_affinity = TopicAffinity()
      

      val insert_sql = s"""
      WITH possible_parents AS (
        SELECT parent.id AS parent_id, parent.depth_in_tree AS parent_depth_in_tree
        FROM ${topic_affinity.general_info_table} AS parent
        WHERE EXISTS ( -- Make sure that the parent has been assigned a level of affinity that is not no_interest
          SELECT *
          FROM (
            ${newest_submitted_affnities_sql(topic_affinity, "?")}
          )as newest
          WHERE newest.id = parent.id AND NOT newest.no_interest
        )
        AND (
          EXISTS (    -- Make sure that there exists a child that has not been assigned a level of interest yet
            SELECT *
            FROM ${topic_affinity.general_info_table} AS child
            WHERE NOT EXISTS (
              SELECT *
              FROM ${topic_affinity.history_table} AS history
              WHERE history.${topic_affinity.history_table_id_column} = child.id AND history.user_id = ?
            )
            AND
            child.parent_id = parent.id
          )
          OR
          EXISTS (    -- Alternatively, there can exist a related project that has not been assigned a level of interest yet
            SELECT *
            FROM ${project_affinity.general_info_table} AS p
            INNER JOIN project_interesting_to pit ON pit.nq_project_id = p.id AND pit.field_of_interest_id = parent.id
            WHERE NOT EXISTS (
              SELECT *
              FROM ${project_affinity.history_table} AS history
              WHERE history.${project_affinity.history_table_id_column} = p.id AND history.user_id = ?
            )
          )
        )
      )
      """.stripMargin + insert_part

      println("printing inser_sql")
      println(insert_sql)

      val insert_stmt = connection.prepareStatement(insert_sql)
      insert_stmt.setInt(1, user_id.id);
      insert_stmt.setInt(2, user_id.id);
      insert_stmt.setInt(3, user_id.id);
      insert_stmt.setInt(4, user_id.id);
      
      insert_stmt.executeUpdate()
    })
  }

  // Moves to the next parent topic whose children and related projects will be filled in by the user. Returns the database_id of the new parent, or None if there is no next parent. (Which means that the user is done filling in the form)
  def next_topic_parent(user_id: Database_ID): Future[Option[Database_ID]] = {
    Future {
      db.withConnection(connection => {
        // Try to find a next parent at the current level of the hierarchy
        next_topic_parent_inner(user_id, connection) match {
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
            next_topic_parent_inner(user_id, connection) match {
              case s: Some[Database_ID] => s
              case None => None // If still no parent was found, the user must have filled in everything
            }
          }
        }
      })
    }
  }

  // Sets the next parent whose children will be filled in by the user.
  def set_topic_parent(user_id: Database_ID, topic_parent_id: Database_ID): Future[Unit] = {
    Future {
      db.withConnection(connection => {
        val sql =
          """
            |UPDATE nq_user SET current_parent_id = ? WHERE id = ? ;
            |""".stripMargin

        val stmt = connection.prepareStatement(sql)
        stmt.setInt(1, topic_parent_id.id)
        stmt.setInt(2, user_id.id)

        stmt.executeUpdate()

        ()
      })
    }
  }

  case class TopicRecord(name: String, id: Database_ID, interest_level: Option[Interest_level])

  // Find the topics that the user currently must fill in, and their assigned affinity.
  def get_current_topics(user_id: Database_ID): Future[List[TopicRecord]] = Future {
    db.withConnection(connection => {
      // val sql =
      //   """
      //     |SELECT field_of_interest.name AS name, field_of_interest.id AS id, interest_level.level_of_interest AS level_of_interest
      //     |FROM field_of_interest
      //     |INNER JOIN nq_user ON field_of_interest.parent_id = nq_user.current_parent_id
      //     |LEFT JOIN interest_level ON nq_user.id = interest_level.user_id AND field_of_interest.id = interest_level.interest_id
      //     |WHERE nq_user.id = ?;
      //     |""".stripMargin
      
      val sql =
        """
        SELECT t.name AS name, t.id AS id, il.level_of_interest AS level_of_interest
        FROM user_edits_topic uet
        INNER JOIN field_of_interest AS t ON t.id = uet.topic_id
        LEFT JOIN interest_level AS il ON uet.user_id = il.user_id AND t.id = il.interest_id
        WHERE uet.user_id = ?
        """.stripMargin

      val stmt = connection.prepareStatement(sql)
      stmt.setInt(1, user_id.id)

      val query_result = stmt.executeQuery()

      var result = List[TopicRecord]()

      while(query_result.next()) {
        val maybe_level_of_interest = nullable_string_to_optional_interest_level(query_result.getString("level_of_interest"))
        result ::= TopicRecord(query_result.getString("name"), Database_ID(query_result.getInt("id")), maybe_level_of_interest)
      }

      result
    })
  }

  def newest_submitted_affinities_sql(affinity_type: Affinity, user_id_unsafe: String): String = {
        f"""
        SELECT a.${affinity_type.history_table_id_column} AS id, a.some_expertise, a.interested, a.sympathise, a.no_interest AS no_interest
        FROM ${affinity_type.history_table} AS a
        INNER JOIN (
          SELECT ${affinity_type.history_table_id_column} AS aid, MAX(time) AS max_time FROM ${affinity_type.history_table}
          WHERE user_id = $user_id_unsafe
          GROUP BY ${affinity_type.history_table_id_column}
        ) AS newest ON newest.aid = a.${affinity_type.history_table_id_column} AND newest.max_time = a.time
        """.stripMargin
  }
  // TODO: Better documentation
  // user_id_unsafe should not be a user inputted value. If it is, use "?" instead.
  def newest_submitted_affnities_sql(affinity_type: Affinity, user_id_unsafe: String): String = {
        val select_description = if(affinity_type.has_description) ", info_table.description" else ""

        f"""
        |SELECT info_table.name, a.${affinity_type.history_table_id_column} AS id, a.some_expertise, a.interested, a.sympathise, a.no_interest AS no_interest ${select_description}
        |FROM ${affinity_type.history_table} AS a
        |INNER JOIN (
          |SELECT ${affinity_type.history_table_id_column} AS aid, MAX(time) AS max_time FROM ${affinity_type.history_table}
          |WHERE user_id = $user_id_unsafe
          |GROUP BY ${affinity_type.history_table_id_column}
        |) AS newest ON newest.aid = a.${affinity_type.history_table_id_column} AND newest.max_time = a.time
        |INNER JOIN ${affinity_type.general_info_table} AS info_table ON info_table.id = a.${affinity_type.history_table_id_column}
        |""".stripMargin
  }

  def get_newest_submitted_affinites(user_id: Database_ID, affinity_type: Affinity): Future[List[models.Form_item]] = {
    Future {
      db.withConnection(connection => {
        val sql = newest_submitted_affnities_sql(affinity_type, "?")

        val stmt = connection.prepareStatement(sql)
        stmt.setInt(1, user_id.id)

        val query_result = stmt.executeQuery()
        var result = List[models.Form_item]()

        while(query_result.next()) {
          val name = query_result.getString(1)
          val id = query_result.getInt(2)
          println("id = " + id)
          val some_expertise = query_result.getBoolean(3)
          val interested = query_result.getBoolean(4)
          val sympathise = query_result.getBoolean(5)
          val no_interest = query_result.getBoolean(6)

          val interest_levels = List(
            if(some_expertise) Some(Interest_level.some_expertise) else None,
            if(interested)     Some(Interest_level.interested)     else None,
            if(sympathise)     Some(Interest_level.sympathise)     else None,
            if(no_interest)    Some(Interest_level.no_interest)    else None,
          ).flatten

          if(affinity_type.has_description) {
            val description = query_result.getString(7)
            result ::= Nq_project(name, Database_ID(id), interest_levels, description)
          }
          else {
            result ::= Field_Of_Expertise(name, Database_ID(id), interest_levels)
          }
        }

        result
      })
    }
  }

  // nullable_string must either be null or be parsable into an integer corresponding to some Interest_level
  def nullable_string_to_optional_interest_level(nullable_string: String): Option[models.Interest_level.Value] = nullable_string match {
    case null => None
    case value => Some(
      Interest_level(value.toInt)
    )
  }

  case class ProjectRecord(name: String, id: Database_ID, interest_level: Option[Interest_level], description: String)
  // Find the projects that the user currently must fill in, and their assigned level_of interest.
  def get_current_nq_projects(user_id: Database_ID): Future[List[ProjectRecord]] = Future {
    db.withConnection(connection => {
      // val sql =
      //   """
      //     |SELECT project.name, project.id, project.description, pil.level_of_interest
      //     |FROM nq_project project
      //     |INNER JOIN project_interesting_to pio
      //     |ON project.id = pio.nq_project_id
      //     |INNER JOIN nq_user
      //     |ON nq_user.current_parent_id = pio.field_of_interest_id
      //     |LEFT JOIN project_interest_level pil ON project.id = pil.project_id AND pil.user_id = nq_user.id
      //     |WHERE nq_user.id = ? AND (pil.first_parent_foi IS NULL OR pil.first_parent_foi = nq_user.current_parent_id);
      //     |""".stripMargin


      val sql =
        """
        SELECT p.name, p.id, p.description, il.level_of_interest
        FROM user_edits_project uep
        INNER JOIN nq_project AS p ON p.id = uep.project_id
        LEFT JOIN project_interest_level AS il ON uep.user_id = il.user_id AND p.id = il.project_id
        WHERE uep.user_id = ?
        """.stripMargin


      val stmt = connection.prepareStatement(sql)
      stmt.setInt(1, user_id.id)

      val query_result = stmt.executeQuery()

      var result = List[ProjectRecord]()

      while(query_result.next()) {
        val maybe_level_of_interest = nullable_string_to_optional_interest_level(query_result.getString(4))

        result ::= ProjectRecord(query_result.getString(1), Database_ID(query_result.getInt(2)), maybe_level_of_interest, query_result.getString(3))
      }

      result
    })
  }

  def submit_affinities(user_id: Database_ID, project_topic_id: Database_ID, affintiy_type: Affinity) : Future[Unit] = Future {
    println(s"submit_affinities: ($user_id, $project_topic_id, ${affintiy_type.ui_table}, ${affintiy_type.history_table})")
    db.withConnection(connection => {
      val query_sql =
        s"""
          |SELECT level_of_interest
          |FROM ${affintiy_type.ui_table}
          |WHERE user_id = ? AND ${affintiy_type.ui_table_id_column} = ?;
          |""".stripMargin

      val query_stmt = connection.prepareStatement(query_sql)
      query_stmt.setInt(1, user_id.id)
      query_stmt.setInt(2, project_topic_id.id)

      val query_result = query_stmt.executeQuery()

      var some_expertise = false
      var interested = false
      var sympathise = false
      var no_interest = false

      while(query_result.next()) {
        val il = Interest_level(query_result.getInt(1))
        println(il)
        il match {
          case Interest_level.some_expertise => some_expertise = true
          case Interest_level.interested => interested = true
          case Interest_level.sympathise => sympathise = true
          case Interest_level.no_interest => no_interest = true
        }
      }

      val update_sql =
        s"""
          |INSERT INTO ${affintiy_type.history_table}(user_id, ${affintiy_type.history_table_id_column}, some_expertise, interested, sympathise, no_interest)
          |VALUES(?, ?, ?, ?, ?, ?);
          """.stripMargin

      val update_stmt = connection.prepareStatement(update_sql)

      update_stmt.setInt(1, user_id.id)
      update_stmt.setInt(2, project_topic_id.id)
      update_stmt.setBoolean(3, some_expertise)
      update_stmt.setBoolean(4, interested)
      update_stmt.setBoolean(5, sympathise)
      update_stmt.setBoolean(6, no_interest)

      update_stmt.executeUpdate()
    })
  }

  def submit_topic_affinities = submit_affinities(_: Database_ID, _: Database_ID, TopicAffinity())
  def submit_project_affinities = submit_affinities(_: Database_ID, _: Database_ID, ProjectAffinity())

  def submit_affinities(user_id: Database_ID, levels_of_affinity: List[Interest_level], project_topic_id: Database_ID, affintiy_type: Affinity): Future[Unit] = Future {
    db.withConnection(connection => {
      val sql = s"""
      |INSERT INTO ${affintiy_type.history_table}(user_id, ${affintiy_type.history_table_id_column}, some_expertise, interested, sympathise, no_interest)
      |VALUES(?, ?, ?, ?, ?, ?);
      """.stripMargin

      val stmt = connection.prepareStatement(sql)

      stmt.setInt(1, user_id.id)
      stmt.setInt(2, project_topic_id.id)
      stmt.setBoolean(3, levels_of_affinity.contains(Interest_level.some_expertise))
      stmt.setBoolean(4, levels_of_affinity.contains(Interest_level.interested))
      stmt.setBoolean(5, levels_of_affinity.contains(Interest_level.sympathise))
      stmt.setBoolean(6, levels_of_affinity.contains(Interest_level.no_interest))

      stmt.executeUpdate()
    })
  }
}
