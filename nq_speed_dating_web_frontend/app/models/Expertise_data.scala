package models
import database.Database_ID

case class Expertise(database_id: Database_ID) {
  def id = database_id.id
}