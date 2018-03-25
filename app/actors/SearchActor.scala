package actors

import javax.inject.{Inject, Named}

import actors.ConnectionManagerActor.{TagTask, TaskFinished}
import actors.SearchActor._
import akka.actor.{Actor, ActorLogging, ActorRef}
import com.typesafe.config.ConfigFactory
import model.SearchResult
import play.api.libs.json.JsArray
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.{ExecutionContext, Future}

/**
  * This class sends requests to StackOverflow API for search by tags
  *
  * @param ws client which makes request to StackOverflow API
  * @param connectionManager reference to [[actors.ConnectionManagerActor]]
  * @param ec used execution context
  */
class SearchActor @Inject() (ws: WSClient, @Named("conManActor") connectionManager: ActorRef)
                            (implicit ec: ExecutionContext)
    extends Actor
    with ActorLogging {

  private def makeRequest(tag: String): Future[Seq[SearchResult]] = {
    log.info(s"Making search request for tag [${tag}]")
    ws.url(s"${requestURL}&tagged=${tag}").get().map { response =>
      parseResponse(response)
    }
  }

  private def parseResponse(response: WSResponse): Seq[SearchResult] = {
    (response.json \ "items").as[JsArray].value.flatMap { value =>
      val isAnswered = (value \ "is_answered").as[Boolean]
      (value \ "tags").as[Seq[String]].map(tag => SearchResult(tag, isAnswered))
    }
  }

  private def search(tagTasks: Seq[TagTask]) = {
    tagTasks.map { case (tag, promise) =>
      val searchResultsFuture = makeRequest(tag)
      searchResultsFuture.map(promise.success(_)).foreach { _ =>
        log.info(s"Finished search task for tag [${tag}]")
        connectionManager ! TaskFinished
      }
    }
  }

  def receive = {
    case Search(tagTasks) => search(tagTasks)
  }
}

object SearchActor {
  case class Search(tagTasks: Seq[TagTask])

  val requestURL = ConfigFactory.load().getString("search.requestURL")
}