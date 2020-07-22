package models.database

import play.api.libs.json.{Format, Json}

case class Database_ID(id: Int)
object Database_ID {

  implicit val format: Format[Database_ID] = Json.format[Database_ID]
}