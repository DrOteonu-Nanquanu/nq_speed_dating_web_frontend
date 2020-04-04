package models
import database.Database_ID

case class Expertise(database_id: Database_ID) {
  def id = database_id.id
}

// Querying data about expertises from the database. It doesn't allow changing the data.
object Expertise_data {
  def get_children(expertise: Expertise): Iterable[Expertise] = {
    // TODO: query database for children of this expertise

    // Placeholder
    List(
      Expertise(Database_ID(0)),
      Expertise(Database_ID(1))
    )
  }

  def get_parent(expertise: Expertise): Option[Expertise] = {
    // TODO: query database for parent of this expertise

    // Placeholder
    Some(Expertise(Database_ID(2)))

    // TODO: return None if there is no parent
  }
}
