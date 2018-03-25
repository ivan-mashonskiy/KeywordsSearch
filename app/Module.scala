import actors.{ConnectionManagerActor, SearchActor, StatisticsActor}
import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

/**
 * This class is a Guice module that tells Guice how to bind several
 * different types. This Guice module is created when the Play
 * application starts.
 */
class Module extends AbstractModule with AkkaGuiceSupport  {

  override def configure() = {
    bindActor[SearchActor]("searchActor")
    bindActor[StatisticsActor]("statisticsActor")
    bindActor[ConnectionManagerActor]("conManActor")
  }

}
