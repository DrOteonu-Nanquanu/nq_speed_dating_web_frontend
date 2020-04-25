package controllers

import javax.inject.Inject
import play.api.mvc.Results._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class AuthenticatedUserAction @Inject() (parser: BodyParsers.Default)(implicit ec: ExecutionContext)
  extends ActionBuilderImpl(parser) {

  override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] = {
    // Get session ID
    val maybe_session_id = request.session.get(models.Configuration.session_uuid_name)

    maybe_session_id match {
      case None => {
        Future.successful(Forbidden("Not logged in"))
      }
      case Some(u) => {
        // TODO: check for valid session uuid

        val res: Future[Result] = block(request)
        res
      }
    }
  }
}