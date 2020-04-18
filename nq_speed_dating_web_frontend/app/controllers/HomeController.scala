package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import models._
import models.database.Database_ID

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(val controllerComponents: ControllerComponents) extends BaseController {

  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }

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
    // TODO: forward to function in model that updates the database

    println(expertise_id)
    println(new_level)

    val level = Expertise_Level.from_name(new_level) match {
      case Some(l) => l
      case None => {
        return Action {
            BadRequest("new_level is not a valid Expertise_Level")
          }
      }
    };

    User_expertise_data.set_expertise_level(
      User(Database_ID(0)), // TODO get user id from session
      Expertise(Database_ID(expertise_id)),
      level
    )

    Action {
        Ok("")
    }
  }
}
