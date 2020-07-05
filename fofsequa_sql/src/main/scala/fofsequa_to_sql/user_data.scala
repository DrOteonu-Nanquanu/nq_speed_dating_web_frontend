package fofsequa_to_sql

object user_data {
  lazy val create_tables = Create_sql.create_tables(tables)

  val tables = List(
    Table("user", List (
      Field("id", "INT NOT NULL"),
      Field("user_name", "VARCHAR(255) NOT NULL"),
      Field("password_hash", "VARCHAR(255) NOT NULL"),
      Field("password_salt", "VARCHAR(255) NOT NULL"),
    ), Some("id")),
    Table("interest_level", List(
      Field("id", "INT NOT NULL"),
      Field("user_id", "INT NOT NULL"),
      Field("interest_id", "INT NOT NULL"),
      Field("level_of_interest", "INT NOT NULL"),
    ), Some("id"))
  )
}