package fofsequa_to_sql

object Fofsequa_to_sql {
  lazy val create_tables = tables.map( {
    case Table(name, fields) =>
      s"CREATE TABLE $name" ++
        fields.map({case Field(field_name, data_type) =>
          field_name ++ " " ++ data_type
        }).mkString("(", ",", ");")
  })

  val tables = List(
    Table("field_of_interest", List(
      Field("id", "int"),
      Field("name", "string"),
      Field("parent_id", "int"),
    )),

    Table("nq_project", List(
      Field("id", "int"),
      Field("name", "string"),
    )),

    Table("project_interesting_to", List(
      Field("id", "int"),
      Field("nq_project_id", "int"),
      Field("field_of_interest_id", "int"),
    )),
  )
}

case class Field(name: String, data_type: String)
case class Table(name: String, fields: Seq[Field])