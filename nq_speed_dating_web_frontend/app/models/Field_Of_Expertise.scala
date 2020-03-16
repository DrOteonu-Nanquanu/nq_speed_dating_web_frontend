package models

class Field_Of_Expertise(new_name: String, new_id: Int, new_children: Seq[Field_Of_Expertise]) {
  val name = new_name
  val id = new_id
}

case class Expertise_ID(id: Int)

case class Person_ID(id: Int)

object Expertise_Level extends Enumeration {
  type Expertise_Level = Value
  val some_expertise,
  interested,
  sympathise,
  no_interest = Value

  // Returns the corresponding Expertise_Level, or None if there isn't one
  def from_name(name: String): Option[Expertise_Level] = name match {
    case "some_expertise" => Some(some_expertise)
    case "interested" => Some(interested)
    case "sympathise" => Some(sympathise)
    case "no_interest" => Some(no_interest)
    case _ => None
  }
}