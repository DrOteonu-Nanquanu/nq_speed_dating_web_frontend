package sql_to_fofsequa

import java.sql.DriverManager
import java.sql.Connection

case class Affinity(some_expertise: Boolean, interested: Boolean, sympathise: Boolean, no_interest: Boolean)


object Sql_to_fofsequa {
  def go() = {
    var c: Connection = null

    try {
      Class.forName("org.postgresql.Driver")
      c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/nq_speed_dating", "nanquanu", "nanquanu")
    } catch {
      case e: Exception =>
        e.printStackTrace()
        System.err.println(e.getClass.getName + ": " + e.getMessage)
        System.exit(0)
    }

    System.out.println("Opened database successfully")

    val stmt = c.createStatement();
    val sql =
      """
        |SELECT u.username, ah.some_expertise, ah.interested, ah.sympathise, ah.no_interest, ah.time, foi.fofsequa_constant
        |FROM nq_user u
        |INNER JOIN topic_affinity_history ah ON u.id = ah.user_id
        |INNER JOIN field_of_interest foi ON ah.topic_id = foi.id;""".stripMargin
    var query_result = stmt.executeQuery(sql)

    var result = scala.collection.mutable.Map.empty[(String, String), List[(Affinity, Long)]] // Maps (username, topic_name) -> (affinity, timestamp)

    while(query_result.next()) {
      val username = query_result.getString(1)
      val some_expertise = query_result.getBoolean(2)
      val interested = query_result.getBoolean(3)
      val sympathise = query_result.getBoolean(4)
      val no_interest = query_result.getBoolean(5)
      val date_time = (query_result.getTimestamp(6))
      val foi_constant = query_result.getString(7) // TODO: this should be the fofsequa constant, not the display name

      println(username.toString ++ " " ++ some_expertise.toString ++ " " ++ interested.toString ++ " " ++ sympathise.toString ++ " " ++ no_interest.toString ++ " " ++ foi_constant.toString + " " + date_time.toString)

      val key = (username, foi_constant)
      val old_list = result.getOrElse(key, List())
      result(key) = (Affinity(some_expertise, interested, sympathise, no_interest), date_time.getTime) :: old_list
    }

    stmt.close()

    result.mapValuesInPlace((key, values) => values.sortBy(_._2))

    for(((username, project_topic_name), values) <- result){
        for((affinity_level_name, property) <- List(
            ("some_expertise", (_:Affinity).some_expertise),
            ("interested", (_:Affinity).interested),
            ("sympathise", (_:Affinity).sympathise),
            ("no_interest", (_:Affinity).no_interest),
        )) {
            val ranges = truth_ranges(values, property)
            val statements = ranges.map({case (truth_value, from_time, to_time) => truth_range_to_statement(truth_value, from_time, to_time, project_topic_name, affinity_level_name, username)})

            val concat_statements = statements.mkString("", ";\n", ";\n")
            
            println(concat_statements)
        }
    }

    ()
  }

  def zipWithPrevious[A](list: List[A]): List[(A, Option[A])] = list.zip(None :: list.map(Some[A](_)))

  def truth_range_to_statement(truth_value: Boolean, from_time: Long, to_time: Option[Long], project_topic_name: String, affinity_level_name: String, user_id: String): String = {
      val possible_negation = if(truth_value) {""} else {"not "}
      val inner_stmt = possible_negation ++ f"$affinity_level_name('nq_speed_dating_user_$user_id', '$project_topic_name')"

      to_time match {
          case Some(to_time) => f"ValidBetween($from_time, $to_time, $inner_stmt)"
          case None => f"ValidFrom($from_time, $inner_stmt)"
      }
  }

  def truth_ranges(affinity_history: List[(Affinity, Long)], property: Affinity => Boolean) : List[(Boolean, Long, Option[Long])] = affinity_history match {
      case Nil => Nil
      case (first_affinity, first_time) :: next => truth_ranges(next, property, property(first_affinity), first_time)
  }

  def truth_ranges(affinity_history: List[(Affinity, Long)], property: Affinity => Boolean, old_property_value: Boolean, time: Long) : List[(Boolean, Long, Option[Long])] = {
      affinity_history match {
          case Nil => List((old_property_value, time, None))
          case (affinity, timestamp) :: next => if(property(affinity) == old_property_value) {
              truth_ranges(next, property, old_property_value, time)
          } else {
              (old_property_value, time, Some(timestamp - 1)) :: truth_ranges(next, property, !old_property_value, timestamp)
          }
      }
      // var property_value = property(affinity_history.head._1)
      // var time = affinity_history.head._2
  }


  def level_of_interest_constant(database_id: Int): String = database_id match {
      case 0 => "some_expertise"
      case 1 => "interested"
      case 2 => "sympathise"
      case 3 => "no_interest"
  }
}
