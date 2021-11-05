package fofsequa_to_sql

object User_data {
  lazy val create_tables = Create_sql.create_tables(tables)

  val id = Field("id", "SERIAL NOT NULL")
  val user_id = Field("user_id", "INT NOT NULL")

  val tables = List(
    Table("nq_user", List (         // 'user' is not a valid name for a table in PostgreSQL
      id,
      Field("username", "VARCHAR(255) NOT NULL"),
      Field("password_hash", "VARCHAR(255) NOT NULL"),
      Field("current_parent_id", "INT NOT NULL DEFAULT 0"),
      Field("current_depth_in_tree", "INT NOT NULL DEFAULT 0")
    ), Some("id")),
    Table("interest_level", List( // TODO: change to topic_interest_level
      id,
      user_id,
      Field("interest_id", "INT NOT NULL"),
      Field("level_of_interest", "INT NOT NULL"),
    ), Some("id")),
    Table("project_interest_level", List(
      Field("id", "SERIAL NOT NULL"),
      user_id,
      Field("project_id", "INT NOT NULL"),
      Field("level_of_interest", "INT NOT NULL"),
      Field("first_parent_foi", "INT")
    ), Some("id")),
    Table("topic_affinity_history", List(
      id,
      user_id,
      Field("topic_id", "INT NOT NULL"),
      Field("some_expertise", "BOOLEAN NOT NULL"),
      Field("interested", "BOOLEAN NOT NULL"),
      Field("sympathise", "BOOLEAN NOT NULL"),
      Field("no_interest", "BOOLEAN NOT NULL"),
      Field("time", "TIMESTAMPTZ DEFAULT Now()"),
    ), Some("id")),
    Table("project_affinity_history", List(
      id,
      user_id,
      Field("project_id", "INT NOT NULL"),
      Field("some_expertise", "BOOLEAN NOT NULL"),
      Field("interested", "BOOLEAN NOT NULL"),
      Field("sympathise", "BOOLEAN NOT NULL"),
      Field("no_interest", "BOOLEAN NOT NULL"),
      Field("time", "TIMESTAMPTZ DEFAULT Now()"),
    ), Some("id")),
    Table("user_edits_topic", List(
      id,
      user_id,
      Field("topic_id", "INT NOT NULL"),
    ), Some("id")),
    Table("user_edits_project", List(
      id,
      user_id,
      Field("project_id", "INT NOT NULL"),
    ), Some("id"))
  )
}
