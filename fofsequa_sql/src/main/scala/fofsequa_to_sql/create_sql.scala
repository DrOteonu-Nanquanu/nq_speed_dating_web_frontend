package fofsequa_to_sql

object Create_sql {
  def create_tables(tables: List[Table]) = tables.map({
    case Table(table_name, fields, primary_key) =>
      s"CREATE TABLE $table_name" ++
        fields.map({ case Field(field_name, data_type) =>
          field_name ++ " " ++ data_type
        }).mkString("(", ",", "") ++
        (primary_key match {
          case Some(key) => ", PRIMARY KEY (" ++ key ++ ")"
          case None => ""
        }) ++ ");"
  })
}

case class Field(name: String, data_type: String)
case class Table(name: String, fields: Seq[Field], primary_key: Option[String] = Some("id"))