package controllers

import javax.inject._
import play.api.mvc._
import models.{Database_ID, _}
import play.api.libs.json.{JsNumber, JsPath, JsString, JsValue}
import services.database.ScalaApplicationDatabase
import play.api.data._

import scala.concurrent.Future
import org.mindrot.jbcrypt.BCrypt

import scala.util.{Failure, Success, Try}

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(
  val controllerComponents: ControllerComponents,
  val userAction: UserInfoAction,
  val user_expertise_data: User_expertise_data,
  val db: ScalaApplicationDatabase,
  val verifier: Verification,
)(
  implicit ec: scala.concurrent.ExecutionContext,
) extends BaseController {
  def index(login_error: Optional_login_error): Action[AnyContent] = userAction { implicit request: UserRequest[_] =>
    request.userInfo match {
      case Some(_: UserInfo) => Redirect("/welcome_page")
      case None => Ok(views.html.index(login_error.login_error.map(idx => login_errors(idx))))
    }
  }

  def welcome_page: Action[AnyContent] = userAction { implicit request: UserRequest[_] =>
    request.userInfo match {
      case Some(user_info: UserInfo) => {
        Ok(views.html.welcome_page(user_info.username + "!"))
      }
      case None => Redirect("/")
    }
  }

  /**
   * The core of the application: this page is where people fill in information about their expertise
   */
  def main_form(): Action[AnyContent] = userAction.async {
    implicit request: UserRequest[_] => {
      request.userInfo match {
        case Some(UserInfo(_, user_id)) =>
          user_expertise_data.get_current_topics_and_projects(user_id).map({ case (topics, projects) => Ok(views.html.form(topics, projects)) } )
          
        case None => Future { Unauthorized("you're not logged in") }
      }
    }
  }

  def move_to_next_parent(): Action[AnyContent] = userAction.async {
    implicit request: UserRequest[_] => {
      request.userInfo match {
        case Some(UserInfo(_, user_id)) =>
          user_expertise_data.next_topic_parent(user_id)
            .map(_ => Redirect("form"))

        case None => Future { Unauthorized("you're not logged in") }
      }
    }
  }

  /**
   * Updates the level of an expertise for a person to new_level
   */
  def update_form_item(): Action[JsValue] = userAction.async(parse.json) {
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
                          println(f"update expertise: user_id ${user_info.id}, database_id $database_id, new_levels $new_levels")
                          user_expertise_data.set_expertise_level(
                            user_info.id,
                            Database_ID(database_id),
                            new_levels
                          ).map(_ => Ok(""))
                        }
                        case JsString("project") => {
                          // forward to function in model that updates the database
                          println(f"update project: user_id ${user_info.id}, database_id $database_id, new_levels $new_levels")
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


  case class Login_info(username: String, password: String)

  val login_form: Form[Login_info] = Form(
    Forms.mapping(
      "username" -> Forms.text,
      "password" -> Forms.text
    )(Login_info.apply)(Login_info.unapply)
  )

  val login_errors = Array(
    "Incorrect password",
    "Username not found",
    "Username already taken",
    "You've tried logging out while you were already logged out"
  )

  // POST action associated with the login form on the index page
  def login: Action[Login_info] = userAction.async(parse.form(login_form)) { implicit request: UserRequest[Login_info] => {
    val Login_info(username, password) = request.body
    verifier.login(username, password, request.session).map({
      case Incorrect_password() => Redirect("/?login_error=0")
      case Username_not_found() => Redirect("/?login_error=1")
      case Login_successful(session_creator) => session_creator(Redirect("/welcome_page"))
    })
  }}

  val register_form: Form[Login_info] = login_form

  // POST action associated with the register form on the index page
  def register: Action[Login_info] = userAction.async(parse.form(register_form)) { implicit request: UserRequest[Login_info] => {
    verifier.register(request.body.username, request.body.password, request.session).map({
      case Username_taken() => Redirect("/?login_error=2")
      case Login_successful(session_creator) => session_creator(Redirect("/welcome_page"))
    })
  }}

  def logout = userAction { implicit request: UserRequest[_] =>
    request.userInfo match {
      case Some(_: UserInfo) => Redirect("/").withNewSession.discardingCookies()
      case None => Ok(views.html.index(Some(login_errors(3))))
    }
  }

  def submit_affinities(affinity_type: TopicProject)(topic_project_id: Int): Action[AnyContent] = userAction.async { implicit request: UserRequest[_] => {
    println("HomeController.submit_affinities")
    request.userInfo match {
      case Some(UserInfo(_, user_id)) => db.submit_affinities(user_id, Database_ID(topic_project_id), affinity_type).map(_ => Ok(""))
      case None => Future { Redirect("/") }
  }}}

  def submit_topic_affinities = submit_affinities(Topic())(_)
  def submit_project_affinities = submit_affinities(Project())(_)

  def change_affinities_page: Action[AnyContent] = userAction.async {
    implicit request: UserRequest[_] => {
      request.userInfo match {
        case Some(UserInfo(_, user_id)) =>
          user_expertise_data.get_current_affinity_states(user_id).map({ case (topics, projects) =>
            Ok(views.html.changed_submitted_affinities(topics, projects))
          })
          
        case None => Future { Unauthorized("you're not logged in") }
      }
    }
  }

  def list_of_try_to_try_of_list[A](list: List[Try[A]]): Try[List[A]] = list match {
    case Nil => Success(Nil)
    case Success(head) :: tail => list_of_try_to_try_of_list(tail).map(head :: _)
    case Failure(e) :: tail => Failure(e)
  }

  implicit class RicherTry[+T](wrapped: Try[T]) {
  def zip[That](that: => Try[That]): Try[(T, That)] = 
    for (a <- wrapped; b <- that) yield (a, b)
  }

  def submit_forms: Action[JsValue] = userAction.async(parse.json) {
    def item_misses_exception(field: String) = new Exception(f"item has no ${field} field")

    def parse_levels_of_interest(elem: JsValue): Try[List[Interest_level.Interest_level]] =
      (elem \ "levels_of_interest").asOpt[List[String]].toRight(item_misses_exception("levels_of_interest")).toTry
        .flatMap(levels => list_of_try_to_try_of_list(
          levels.map(name => Interest_level.from_name(name).toRight(new Exception(f"could not parse $name into Interest_level")).toTry)
        )) match {
          case f: Failure[_] => f
          case Success(List()) => Failure(new Exception(f"cannot submit empty list of interest levels"))
          case other => other
        }

    def parse_database_id(elem: JsValue) =
      (elem \ "database_id").asOpt[Int].toRight(item_misses_exception("database_id")).toTry.map(Database_ID(_)) // The database_id of the project or topic

    def parse_form_item_type(elem: JsValue) = 
      (elem \ "form_item_type").asOpt[String].toRight(item_misses_exception("form_item_type")).toTry.flatMap({
        case "expertise" => Success(Topic())
        case "project" => Success(Project())
        case other => Failure(new Exception(f"Did not recognise $other as affinity type"))
      })

    request: UserRequest[JsValue] => {
        request.userInfo.map(userInfo => {
          val maybe_body =
            if(!request.hasBody)
              Failure(new Exception("request has no body"))
            else
              request.body.asOpt[List[JsValue]].toRight(new Exception("expected body to be an array")).toTry

          val result = maybe_body.flatMap(body => list_of_try_to_try_of_list(body.map(elem =>
            parse_levels_of_interest(elem).zip(parse_database_id(elem)).zip(parse_form_item_type(elem))
          )).map(_.map(
           { case ((levels_of_interest, database_id), affinity_type) => user_expertise_data.submit_forms(userInfo.id, levels_of_interest, database_id, affinity_type)}
         )))
          
          result match {
            case Success(unit) => Future { Ok }
            case Failure(exception) => Future { BadRequest(exception.getMessage) }
          }
        }).getOrElse(Future { Unauthorized })
    }
  }

  def test: Action[AnyContent] = userAction.async {
    throw new Exception("test")
    Future{ Ok }
  }
}
