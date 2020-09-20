package sql_to_fofsequa

import java.sql.DriverManager
import java.sql.Connection

object Sql_to_fofsequa {
  def go() = {
    var c: Connection = null

    try {
      Class.forName("org.postgresql.Driver")
      c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/nq_speed_dating", "postgres", "nanquanu")
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
        |SELECT u.username, il.level_of_interest, foi.fofsequa_constant
        |FROM nq_user u
        |INNER JOIN interest_level il ON u.id = il.user_id
        |INNER JOIN field_of_interest foi ON il.interest_id = foi.id;""".stripMargin
    var query_result = stmt.executeQuery(sql)

    while(query_result.next()) {
      val username = query_result.getString(1)
      val level_of_interest = query_result.getInt(2)
      val foi_name = query_result.getString(3)

      println(username.toString ++ " " ++ level_of_interest_constant(level_of_interest) ++ " " ++ foi_name.toString)
    }
    stmt.close()

  }

  def level_of_interest_constant(database_id: Int): String = database_id match {
      case 0 => "some_expertise"
      case 1 => "interested"
      case 2 => "sympathise"
      case 3 => "no_interest"
  }
}