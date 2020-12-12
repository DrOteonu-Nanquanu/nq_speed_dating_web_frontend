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