package controllers

import java.sql.DriverManager

import javax.inject._
import play.api._
import play.api.mvc._
import models._
import models.database.Database_ID
import services.database.ScalaApplicationDatabase

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(
  val controllerComponents: ControllerComponents,
  val userAction: UserInfoAction,
  val sessionGenerator: SessionGenerator,
  // val db: ScalaApplicationDatabase,
)(implicit ec: scala.concurrent.ExecutionContext) extends BaseController {

  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index() = Action { implicit request: Request[AnyContent] => {
    import java.sql.DriverManager
    import java.sql.Connection
    /*var c: Connection = null
    try {
      Class.forName("org.postgresql.Driver")
      c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/nq_speed_dating", "postgres", "nanquanu")
      val stmt = c.createStatement();
      val sql =
        """
          |SELECT *
          |FROM field_of_interest;""".stripMargin
      var query_result = stmt.executeQuery(sql)
      while(query_result.next()) {
        val name = query_result.getString("name")
        println(name)
      }

      stmt.close()
    } catch {
      case e: Exception =>
        e.printStackTrace()
        System.err.println(e.getClass.getName + ": " + e.getMessage)
        System.exit(0)
    }

    System.out.println("Opened database successfully")*/

    Ok(views.html.index())
  }}

  /**
   * Will likely be removed soon
   */
  def field_of_expertise() = Action {
    Ok(views.html.expertise(
      new Field_Of_Expertise("test", 0, List())
    ))
  }

  /**
   * The meat of the application: this page is where people fill in information about their expertise
   */
  def form() = Action {
    Ok(views.html.form(List(
      new Field_Of_Expertise("test_foe_1", 1, List()),
      new Field_Of_Expertise("test_foe_2", 2, List()),
      new Field_Of_Expertise("test_foe_3", 3, List()),
    )))
  }

  /**
   * Updates the level of an expertise for a person to new_level
   */
  def update_expertise(expertise_id: Int, new_level: String): Action[AnyContent] = {
    Action {
      request => {
        // TODO: get data from body instead of URL

        println(expertise_id)
        println(new_level)

        Expertise_Level.from_name(new_level) match {
          case None => {
              BadRequest("new_level is not a valid Expertise_Level")
          }
          case Some(level) => {
            // forward to function in model that updates the database
            User_expertise_data.set_expertise_level(
              User(Database_ID(0)), // TODO get user id from session
              Expertise(Database_ID(expertise_id)),
              level
            )

            Ok("")
          }
        }
      }
    }
  }

  def secret_page = userAction {
    implicit request: UserRequest[_] => {
      request.userInfo match {
        case Some(user_info) => Ok("welcome " + user_info.username)
        case None => Ok("you're not logged in")
      }
    }
  }

  def login_secret = userAction.async { implicit request: UserRequest[AnyContent] =>
    sessionGenerator.createSession(UserInfo("test_user")).map({
      case (session_id, encrypted_cookie) => Ok("logged in!")
        .withSession(request.session + (SESSION_ID -> session_id))
        .withCookies(encrypted_cookie)
    })
  }
}
