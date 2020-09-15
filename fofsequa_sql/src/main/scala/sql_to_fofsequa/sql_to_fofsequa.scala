package sql_to_fofsequa

import java.sql.DriverManager
import java.sql.Connection

object Sql_to_fofsequa {
  def go() = {
    try {
      Class.forName("org.postgresql.Driver")
      val c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/nq_speed_dating", "postgres", "nanquanu")
      val stmt = c.createStatement();
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
    } catch {
      case e: Exception =>
        e.printStackTrace()
        System.err.println(e.getClass.getName + ": " + e.getMessage)
        System.exit(0)
    }
    System.out.println("Opened database successfully")
  }
}