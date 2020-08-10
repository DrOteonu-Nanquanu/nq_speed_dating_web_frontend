package models

import models.Interest_level.Interest_level
import models.database.Database_ID

case class Field_Of_Expertise(name: String, id: Database_ID, interest_level: Option[Interest_level])

case class Expertise_ID(id: Int)

case class Person_ID(id: Int)

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