package models

import models.Interest_level.Interest_level
import models.database.Database_ID

case class Field_Of_Expertise(name: String, id: Database_ID, interest_levels: List[Interest_level]) extends Form_item {
  def optional_description: Option[Nothing] = None

  override def item_type: String = "expertise"
}

case class Nq_project(name: String, id: Database_ID, interest_levels: List[Interest_level], description: String) extends Form_item {
  def optional_description: Option[String] = Some(description)

  override def item_type: String = "project"
}

trait Form_item {
  def name: String
  def id: Database_ID
  def interest_levels: List[Interest_level]
  def optional_description: Option[String]
  def item_type: String
}

object Interest_level extends Enumeration {
  type Interest_level = Value
  val some_expertise,
  interested,
  sympathise,
  no_interest = Value

  // Returns the corresponding Expertise_Level, or None if there isn't one
  def from_name(name: String): Option[Interest_level] = name match {
    case "some_expertise" => Some(some_expertise)
    case "interested" => Some(interested)
    case "sympathise" => Some(sympathise)
    case "no_interest" => Some(no_interest)
    case _ => None
  }
}

abstract sealed class Affinity {
  val name: String
  def history_table: String = s"${name}_affinity_history"
  val ui_table: String
  val ui_table_id_column: String
  val history_table_id_column: String
  def user_edits_table: String = "user_edits_" + name
  def edit_state_table_id_column: String = name + "_id"
  val general_info_table: String
  val has_description: Boolean
}

case class ProjectAffinity() extends Affinity {
  val name = "project"
  val ui_table = "project_interest_level"
  val ui_table_id_column = "project_id"
  val history_table_id_column = ui_table_id_column
  // val edit_state_table_id_column = ui_table_id_column
  val general_info_table = "nq_project"
  val has_description = true;
}

case class TopicAffinity() extends Affinity {
  val name = "topic"
  val ui_table = "interest_level"
  val ui_table_id_column = "interest_id"
  val history_table_id_column = "topic_id"
  // val edit_state_table_id_column = ""
  val general_info_table = "field_of_interest"
  val has_description = false;
}
