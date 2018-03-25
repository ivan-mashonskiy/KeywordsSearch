package controllers

import javax.inject.{Inject, Named}

import actors.StatisticsActor.GetStatistics
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import model.TagStatistics
import play.api.Logger
import play.api.libs.json.{JsNumber, JsObject, Json}
import play.api.mvc.{AbstractController, ControllerComponents}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class SearchController @Inject()(cc: ControllerComponents, @Named("statisticsActor") statisticsActor: ActorRef)
                                (implicit ec: ExecutionContext)
    extends AbstractController(cc) {

  val config = ConfigFactory.load()
  implicit val timeout = Timeout(config.getInt("search.timeout") seconds)
  val log = Logger(this.getClass)

  private def makeResultJson(tagStatisticsSeq: Seq[TagStatistics]): JsObject = {
    val resultMap = tagStatisticsSeq.map { case TagStatistics(name, total, answered) =>
      name -> JsObject(Map("total" -> JsNumber(total), "answered" -> JsNumber(answered)))
    }.toMap
    JsObject(resultMap)
  }

  def search(tag: Seq[String]) = Action.async {
    log.info(s"Received new request with tags [${tag.mkString(", ")}]")
    val tagStatistics = (statisticsActor ? GetStatistics(tag)).mapTo[Seq[TagStatistics]]
    val result = tagStatistics.map(makeResultJson(_))
    result.map(res => Ok(Json.prettyPrint(res)))
  }
}
