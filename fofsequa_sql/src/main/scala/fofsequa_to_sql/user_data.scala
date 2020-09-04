package fofsequa_to_sql

object User_data {
  lazy val create_tables = Create_sql.create_tables(tables)

  val tables = List(
    Table("nq_user", List (         // 'user' is not a valid name for a table in PostgreSQL
      Field("id", "SERIAL NOT NULL"),
      Field("username", "VARCHAR(255) NOT NULL"),
      Field("password_hash", "VARCHAR(255) NOT NULL"),
      Field("current_parent_id", "INT NOT NULL DEFAULT 0"),
      Field("current_depth_in_tree", "INT NOT NULL DEFAULT 0")
    ), Some("id")),
    Table("interest_level", List(
      Field("id", "SERIAL NOT NULL"),
      Field("user_id", "INT NOT NULL"),
      Field("interest_id", "INT NOT NULL"),
      Field("level_of_interest", "INT NOT NULL"),
    ), Some("id")),

    Table("project_interest_level", List(
      Field("id", "SERIAL NOT NULL"),
      Field("user_id", "INT NOT NULL"),
      Field("project_id", "INT NOT NULL"),
      Field("level_of_interest", "INT NOT NULL"),
    ), Some("id"))
  )
}
