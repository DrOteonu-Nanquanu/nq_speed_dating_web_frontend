package controllers

import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._
import play.api.test.Helpers._

import models.Optional_login_error

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 *
 * For more information, see https://www.playframework.com/documentation/latest/ScalaTestingWithScalaTest
 */
class HomeControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {
  val rnd = new scala.util.Random()

  "HomeController" should {
    // "render the index page from a new instance of controller" in {
    //   val controller = new HomeController(stubControllerComponents())
    //   val home = controller.index().apply(FakeRequest(GET, "/"))

    //   status(home) mustBe OK
    //   contentType(home) mustBe Some("text/html")
    //   contentAsString(home) must include ("Welcome to Play")
    // }

    // "render the index page from the application" in {
    //   val controller = inject[HomeController]
    //   val home = controller.index().apply(FakeRequest(GET, "/"))

    //   status(home) mustBe OK
    //   contentType(home) mustBe Some("text/html")
    //   contentAsString(home) must include ("Welcome to Play")
    // }

    // "render the index page from the router" in {
    //   val request = FakeRequest(GET, "/")
    //   val home = route(app, request).get

    //   status(home) mustBe OK
    //   contentType(home) mustBe Some("text/html")
    //   contentAsString(home) must include ("Welcome to Play")
    // }
    
    "not crash when requesting home page" in {
      val controller = inject[HomeController]
      val home = controller.index(Optional_login_error(None)).apply(FakeRequest(GET, "/"))
      status(home) mustBe OK
    }
  }

  val new_user_name = rnd.nextString(8)
  val new_user_password = "test_password"

  "New User" should {
    // "be created" in {
    //   val home_controller = inject[HomeController]
    // }
  }

  "Database" should {
    // "Submit topic affinities" in {
    //   val db = inject[services.database.ScalaApplicationDatabase]

    //   db.submit_topic_affinities(models.database.Database_ID(1), models.database.Database_ID(1))
    // }
  }
}
