package controllers

import javax.inject._
import play.api.mvc._
import models._
import models.database.Database_ID
import play.api.libs.json.{JsNumber, JsPath, JsString, JsValue}
import services.database.ScalaApplicationDatabase
import play.api.data._

import scala.concurrent.Future
import org.mindrot.jbcrypt.BCrypt

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(
  val controllerComponents: ControllerComponents,
  val userAction: UserInfoAction,
  val user_expertise_data: User_expertise_data,
  //val db: ScalaApplicationDatabase,
  val db: ScalaApplicationDatabase,
  val verifier: Verification,
)(
  implicit ec: scala.concurrent.ExecutionContext,
) extends BaseController {
  def index() = userAction { implicit request: UserRequest[_] =>
    request.userInfo match {
      case Some(user_info: UserInfo) => Redirect("/welcome_page")
      case None => Ok(views.html.index())
    }
  }

  def welcome_page = userAction { implicit request: UserRequest[_] =>
    request.userInfo match {
      case Some(user_info: UserInfo) => {
        Ok(views.html.welcome_page(user_info.username))
      }
      case None => Redirect("/")
    }
  }

  /**
   * The meat of the application: this page is where people fill in information about their expertise
   */
  def form() = userAction.async {
    implicit request: UserRequest[_] => {
      request.userInfo match {
        case Some(UserInfo(_, user_id)) =>
          user_expertise_data.get_current_fois(user_id).map(fois => Ok(views.html.form(fois)))

        case None => Future { Unauthorized("you're not logged in") }
      }
    }
  }

  def move_to_next_parent() = userAction.async {
    implicit request: UserRequest[_] => {
      request.userInfo match {
        case Some(UserInfo(_, user_id)) =>
          user_expertise_data.next_foi_parent(user_id)
            .map(_ => Redirect("form"))

        case None => Future { Unauthorized("you're not logged in") }
      }
    }
  }

  /**
   * Updates the level of an expertise for a person to new_level
   */
  def update_expertise() = {
    userAction(parse.json) {
      request: UserRequest[JsValue] => {

        request.userInfo match {
          case None => Unauthorized("you're not logged in")
          case Some(user_info) =>
            if (request.hasBody) {
              // Read expertise_id and level_of_interest from body
              val json = request.body

              (json \ "expertise_id").get match {
                case JsNumber(value) => {
                  val expertise_id = value.intValue

                  (json \ "level_of_interest").get match {
                    case JsString(new_level) => {
                      Interest_level.from_name(new_level) match {
                        case None => {
                          BadRequest("new_level is not a valid Expertise_Level")
                        }
                        case Some(level) => {
                          // forward to function in model that updates the database
                          user_expertise_data.set_expertise_level(
                            user_info.id,
                            Database_ID(expertise_id),
                            level
                          )

                          Ok("")
                        }
                      }
                    }
                    case _ => BadRequest("level_of_interest isn't a string")
                  }
                }
                case _ => BadRequest("expertise_id isn't a number")
              }
            }
            else {
              BadRequest("response has no body")
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

  case class Login_info(username: String, password: String)

  val login_form = Form(
    Forms.mapping(
      "username" -> Forms.text,
      "password" -> Forms.text
    )(Login_info.apply)(Login_info.unapply)
  )

  def login = userAction.async(parse.form(login_form)) { implicit request: UserRequest[Login_info] => {
    val Login_info(username, password) = request.body
    verifier.login(username, password, request.session)
  }}



  val register_form = login_form

  def register = userAction.async(parse.form(register_form)) { implicit request: UserRequest[Login_info] => {
    verifier.register(request.body.username, request.body.password, request.session)
  }}
}
