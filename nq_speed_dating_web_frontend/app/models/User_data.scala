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
  def set_expertise_level(user: Database_ID, expertise: Database_ID, expertise_level: Interest_level.Interest_level): Future[Unit] = {
    println("expertise")
    println(user.id)
    println(expertise.id)
    println(expertise_level)

    db.set_foi_interesting_to(user, expertise, expertise_level)
  }

  def set_project_expertise_level(user: Database_ID, project: Database_ID, expertise_level: Interest_level.Interest_level): Future[Unit] = {
    println("project")
    println(user.id)
    println(project.id)
    println(expertise_level)

    db.set_project_interesting_to(user, project, expertise_level)
  }

  def next_foi_parent(user: Database_ID): Future[Option[Database_ID]] = {
    db.next_foi_parent(user).flatMap(next_parent_id => {
      val future = next_parent_id match {
        case Some(new_parent_id) => {
          println("next_parent_id = some " + new_parent_id)
          db.set_foi_parent(user, new_parent_id)
        }
        case None => Future {}
      }

      future.map(_ => next_parent_id)
    })
  }

  def get_current_fois(user_id: Database_ID): Future[List[Field_Of_Expertise]] = {
    db.get_current_fois(user_id)
  }
}

@Singleton
class Verification @Inject()(
  db: services.database.ScalaApplicationDatabase,
  val sessionGenerator: SessionGenerator,
)(
  implicit ec: scala.concurrent.ExecutionContext,
) {
  def set_login_cookie(username: String, user_id: Database_ID, session: Session) = {
    sessionGenerator.createSession(UserInfo(username, user_id)).map({
      case (session_id, encrypted_cookie) => Redirect("/")
        .withSession(session + (SESSION_ID -> session_id))
        .withCookies(encrypted_cookie)
    })
  }

  def login(username: String, password: String, session: Session): Future[Result] = {
    db.get_user_verification_data(username).flatMap({
      case List() => Future{ Redirect("/?login_error=Username+not+found") }
      case (user: User) :: List() => {
        if (BCrypt.checkpw(password, user.password_hash)) {
          set_login_cookie(username, user.database_ID, session)
        }
        else {
          Future { Redirect("/?login_error=Incorrect+password") }
        }
      }
      case _ => {
        println("multiple accounts found with username = " + username)
        Future { InternalServerError("There were multiple accounts with the same username") }
      }
    })
  }

  def register(username: String, password: String, session: Session) = {
    val hashed_password = BCrypt.hashpw(password, BCrypt.gensalt())

    db.create_new_user(username, hashed_password).flatMap(successful =>
      if(!successful) {
        Future { Redirect("/?login_error=Username+already+taken") }
      }
      else {
        db.get_user_id(username).flatMap {
          case Some(id) => set_login_cookie(username, id, session)
          case None => throw new Exception("Account creation failed: username isn't actually present in the database")
        }
      }
    )
  }
}