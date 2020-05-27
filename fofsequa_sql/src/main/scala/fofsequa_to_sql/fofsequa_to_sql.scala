package fofsequa_to_sql

object Fofsequa_to_sql {
  def create_tables = Tables.Names.tables.map(
    table_name =>
      s"""CREATE TABLE $table_name
        |
        |""".stripMargin
  )
}

object Tables {
  object Names {
    val field_of_interest = "field_of_interest"
    val nq_project = "nq_project"
    val project_foi_relation = "project_interesting_to"

    val tables = List(
      field_of_interest,
      nq_project,
      project_foi_relation,
    )
  }

}

case class Table()