package models

import database.Database_ID
import javax.inject.{Inject, Singleton}

import scala.concurrent.Future

case class User(database_ID: Database_ID, username: String, password_hash: String)

// Querying and updating data about users' expertises.
@Singleton
class User_expertise_data @Inject()(
  db: services.database.ScalaApplicationDatabase,
)(
  implicit ec: scala.concurrent.ExecutionContext,
){
  // Sets the expertise level of `user` in `expertise` to `expertise_level` in the database.
  def set_expertise_level(user: Database_ID, expertise: Database_ID, expertise_level: Interest_level.Interest_level): Unit = {
    println(user.id)
    println(expertise.id)
    println(expertise_level)

    db.set_interesting_to(user, expertise, expertise_level)
  }

  def get_expertise_level(user: User, expertise: Expertise): Interest_level.Interest_level = Interest_level.no_interest // TODO

  def next_foi_parent(user: Database_ID): Future[Option[Database_ID]] = {
    db.next_foi_parent(user).map(next_parent_id => {
      next_parent_id match {
        case Some(new_parent_id) => {
          db.set_foi_parent(user, new_parent_id)
        }
        case None => ()
      }

      next_parent_id
    })
  }

  def get_current_fois(user_id: Database_ID): Future[List[Field_Of_Expertise]] = {
    db.get_current_fois(user_id)
  }
}
