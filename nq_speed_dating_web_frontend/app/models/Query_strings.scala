package models

import play.api.mvc.QueryStringBindable

import scala.util.Try

case class Optional_login_error(login_error: Option[Int])

object Optional_login_error {
  implicit def queryStringBindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[Optional_login_error] = new QueryStringBindable[Optional_login_error] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Optional_login_error]] =
      Some(Right(Optional_login_error(
        params.get("login_error") match {
          case Some(from) if from.nonEmpty => Try(from.head.toInt).toOption
          case _ => None
        }
      )))

    override def unbind(key: String, ageRange: Optional_login_error): String = {
      stringBinder.unbind("login_error", ageRange.login_error.get.toString)
    }
  }
}