package models

class Field_Of_Expertise(new_name: String, new_id: Int, new_children: Seq[Field_Of_Expertise]) {
  val name = new_name
  val id = new_id
}
