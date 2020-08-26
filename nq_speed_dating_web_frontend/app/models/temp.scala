package models

import play.api.mvc.QueryStringBindable

case class Login_error(login_error: String) {

}
object Login_error {
  implicit def queryStringBindable(implicit intBinder: QueryStringBindable[String]) = new QueryStringBindable[Login_error] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Login_error]] = {
      for {
        login_error <- intBinder.bind("login_error", params)
      } yield {
        login_error match {
          case Right(from) => Right(Login_error(from))
          case _ => Left("Unable to bind a Login_error")
        }
      }
    }

    override def unbind(key: String, ageRange: Login_error): String = {
      intBinder.unbind("login_error", ageRange.login_error)
    }
  }
}

