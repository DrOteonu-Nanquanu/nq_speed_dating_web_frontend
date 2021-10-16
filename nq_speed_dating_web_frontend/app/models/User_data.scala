package models

import controllers.{SESSION_ID, SessionGenerator, UserInfo}
import database.Database_ID
import javax.inject.{Inject, Singleton}
import org.mindrot.jbcrypt.BCrypt
import play.api.mvc.{Result, Session}
import play.api.mvc.Results._

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
  def set_expertise_level(user: Database_ID, expertise: Database_ID, expertise_level: List[Interest_level.Interest_level]): Future[Unit] =
    db.set_topic_interesting_to(user, expertise, expertise_level)

  def set_project_expertise_level(user: Database_ID, project: Database_ID, expertise_level: List[Interest_level.Interest_level]): Future[Unit] =
    db.set_project_interesting_to(user, project, expertise_level)

  // Moves to the next topic whose children will be filled in by the user
  def next_topic_parent(user: Database_ID): Future[Option[Database_ID]] =
    db.next_topic_parent(user).flatMap(next_parent_id => {
      val future = next_parent_id match {
        case Some(new_parent_id) => {
          println("next_parent_id = some " + new_parent_id)
          db.set_topic_parent(user, new_parent_id)
        }
        case None => Future {}
      }

      future.map(_ => next_parent_id)
    })

  // Find the topics that the user currently must fill in, and their assigned level_of interest.
  def get_current_topics(user_id: Database_ID): Future[List[Field_Of_Expertise]] = {
    db.get_current_topics(user_id).map(topic_records_to_topics)
  }
  // TODO: This method and `get_current_topics` are almost a copy-paste. Could use some nice abstraction.
  def get_current_projects(user_id: Database_ID): Future[List[Nq_project]] = {
    db.get_current_nq_projects(user_id).map(project_records_to_projects)
  }

  def project_records_to_projects(projects: List[db.ProjectRecord]) = {
    val ids = projects.map(t => t.id).distinct

    ids.map(id => {
      val records = projects.filter(_.id == id)

      Nq_project(records.head.name, records.head.id, records.flatMap(_.interest_level), records.head.description)
    })
  }
  def topic_records_to_topics(topics: List[db.TopicRecord]) = {
    val ids = topics.map(t => t.id).distinct

    ids.map(id => {
      val records = topics.filter(_.id == id)

      Field_Of_Expertise(records.head.name, records.head.id, records.flatMap(_.interest_level))
    })
  }

  // TODO: update ui state as well
  // TODO: filter unchanged values
  def submit_forms(user_id: Database_ID, levels_of_interest: List[models.Interest_level.Interest_level], project_id: Database_ID, form_item_type: Affinity): Future[Unit] = {
    println(f"submit_forms($user_id, $levels_of_interest, $project_id, $form_item_type)")
    db.submit_affinities(user_id, levels_of_interest, project_id, form_item_type)
  }
}

sealed trait Login_result
case class Username_not_found() extends Login_result
case class Incorrect_password() extends Login_result

// session_creator will update the user's session to contain the new username and corresponding database_id.
case class Login_successful(session_creator: Verification.Session_creator) extends Login_result with Register_result

sealed trait Register_result
case class Username_taken() extends  Register_result
// Don't forget: Login_successful also extends Register_result

@Singleton
class Verification @Inject()(
  db: services.database.ScalaApplicationDatabase,
  val sessionGenerator: SessionGenerator,
)(
  implicit ec: scala.concurrent.ExecutionContext,
) {
  // Returns a function that will transform a Result into a Result with an updated Session containing the username and the user_id
  def set_login_cookie(username: String, user_id: Database_ID, session: Session): Future[Verification.Session_creator] = {
    sessionGenerator.createSession(UserInfo(username, user_id)).map({
      case (session_id, encrypted_cookie) =>
        _.withSession(session + (SESSION_ID -> session_id))
         .withCookies(encrypted_cookie)
    })
  }

  // Verify username and password. If correct, returns Login_successful(session_creator)
  def login(username: String, password: String, session: Session): Future[Login_result] = {
    db.get_user_verification_data(username).flatMap({
      case List() => Future{ Username_not_found() }
      case List(user: User) =>
        if (BCrypt.checkpw(password, user.password_hash)) {
          set_login_cookie(username, user.database_ID, session).map(Login_successful)
        }
        else {
          Future { Incorrect_password() }
        }
      case _ => throw new Exception("There were multiple accounts with the same username")
    })
  }

  // Create new user if the username isn't already taken
  def register(username: String, password: String, session: Session): Future[Register_result] = {
    val hashed_password = BCrypt.hashpw(password, BCrypt.gensalt())

    db.create_new_user(username, hashed_password).flatMap(successful =>
      if(!successful) {
        Future { Username_taken() }
      }
      else {
        db.get_user_id(username).flatMap {
          case Some(id) => set_login_cookie(username, id, session).map(Login_successful)
          case None => throw new Exception("Account creation failed: username isn't actually present in the database")
        }
      }
    )
  }
}

object Verification {
  type Session_creator = Result => Result;
}
