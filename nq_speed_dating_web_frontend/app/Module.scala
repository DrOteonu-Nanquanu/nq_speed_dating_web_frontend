import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport
import services.session.{ ClusterSystem, SessionCache }
import play.core.server.DevServerStart

class Module extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    // throw new Exception("test")
    bind(classOf[ClusterSystem]).asEagerSingleton()
    bindTypedActor(SessionCache(), "replicatedCache")
  }
}
