package actors

import javax.inject.{Inject, Named}

import actors.StatisticsActor.GetStatistics
import akka.actor.{Actor, ActorRef}
import akka.pattern.pipe
import model.TagStatistics

import scala.concurrent.{ExecutionContext, Future}

/**
  * This class computes statistics for all tags obtained by search task
  *
  * @param ec used execution context
  * @param connectionManager reference to [[actors.ConnectionManagerActor]]
  */
class StatisticsActor @Inject() (implicit ec: ExecutionContext,
                                 @Named("conManActor") connectionManager: ActorRef)
    extends Actor {

  private def computeStatistics(tags: Seq[String]): Future[Seq[TagStatistics]] = {
    val searchResultsFuture = ConnectionManagerActor.getSearchResultsFuture(tags)
    searchResultsFuture.map { searchResults =>
      val grouped = searchResults.groupBy(_.tag).toSeq
      grouped.map { case (tag, sr) =>
        val isAnsweredCount = sr.count(_.isAnswered)
        TagStatistics(tag, sr.size, isAnsweredCount)
      }
    }
  }

  def receive = {
    case GetStatistics(tags) => computeStatistics(tags) pipeTo sender()
  }
}

object StatisticsActor {
  case class GetStatistics(tags: Seq[String])
}