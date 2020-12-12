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
  def index(login_error: Optional_login_error): Action[AnyContent] = userAction { implicit request: UserRequest[_] =>
    request.userInfo match {
      case Some(_: UserInfo) => Redirect("/welcome_page")
      case None => Ok(views.html.index(login_error))
    }
  }

  def welcome_page: Action[AnyContent] = userAction { implicit request: UserRequest[_] =>
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
  def form(): Action[AnyContent] = userAction.async {
    implicit request: UserRequest[_] => {
      request.userInfo match {
        case Some(UserInfo(_, user_id)) =>
          user_expertise_data.get_current_fois(user_id).flatMap(fois =>
            db.get_current_nq_projects(user_id).map(projects => Ok(views.html.form(fois, projects)))
          )
          
        case None => Future { Unauthorized("you're not logged in") }
      }
    }
  }

  def move_to_next_parent(): Action[AnyContent] = userAction.async {
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
  def update_form_item(): Action[JsValue] = {
    userAction.async(parse.json) {
      request: UserRequest[JsValue] => {
        request.userInfo match {
          case Some(user_info) =>
            if (request.hasBody) {
              // Read database_id and level_of_interest from body
              val json = request.body

              (json \ "database_id").get match {
                case JsNumber(value) => {
                  val database_id = value.intValue

                  (json \ "levels_of_interest").asOpt[List[String]] match {
                    case Some(new_level_strings) => {
                      val new_levels = new_level_strings.map(new_level => Interest_level.from_name(new_level) match {
                        case Some(l) => l
                        case None => null
                      })
                      if(new_levels.find(_ == null) != None) {
                        Future { BadRequest("didn't recognise some string in levels_of_interest") }
                      }
                      else {
                        (json \ "form_item_type").get match {
                          case JsString("expertise") => {
                            // forward to function in model that updates the database
                            user_expertise_data.set_expertise_level(
                              user_info.id,
                              Database_ID(database_id),
                              new_levels
                            ).map(_ => Ok(""))
                          }
                          case JsString("project") => {
                            // forward to function in model that updates the database
                            user_expertise_data.set_project_expertise_level(
                              user_info.id,
                              Database_ID(database_id),
                              new_levels
                            ).map(_ => Ok(""))
                          }
                          case _ => Future {
                            BadRequest("form_item_type is invalid")
                          }
                        }
                      }

                    }
                    case _ => Future {
                      BadRequest("levels_of_interest isn't an array")
                    }
                  }
                }
                case _ => Future { BadRequest("expertise_id isn't a number") }
              }
            }
            else {
              Future { BadRequest("response has no body") }
            }
          case None => Future { Unauthorized("you're not logged in") }
        }
      }
    }
  }

  case class Login_info(username: String, password: String)

  val login_form: Form[Login_info] = Form(
    Forms.mapping(
      "username" -> Forms.text,
      "password" -> Forms.text
    )(Login_info.apply)(Login_info.unapply)
  )

  // POST action associated with the login form on the index page
  def login: Action[Login_info] = userAction.async(parse.form(login_form)) { implicit request: UserRequest[Login_info] => {
    val Login_info(username, password) = request.body
    verifier.login(username, password, request.session).map({
      case Incorrect_password() => Redirect("/?login_error=Incorrect+password")
      case Username_not_found() => Redirect("/?login_error=Username+not+found")
      case Login_successful(session_creator) => session_creator(Redirect("/welcome_page"))
    })
  }}

  val register_form: Form[Login_info] = login_form

  // POST action associated with the register form on the index page
  def register: Action[Login_info] = userAction.async(parse.form(register_form)) { implicit request: UserRequest[Login_info] => {
    verifier.register(request.body.username, request.body.password, request.session).map({
      case Username_taken() => Redirect("/?login_error=Username+already+taken")
      case Login_successful(session_creator) => session_creator(Redirect("/welcome_page"))
    })
  }}
}
