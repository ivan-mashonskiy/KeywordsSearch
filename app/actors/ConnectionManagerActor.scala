package actors

import javax.inject.{Inject, Named}

import actors.ConnectionManagerActor._
import actors.SearchActor.Search
import akka.actor.{Actor, ActorLogging, ActorRef}
import com.typesafe.config.ConfigFactory
import model.SearchResult

import scala.collection.mutable
import scala.concurrent.{Future, Promise}

/**
  * This class maintains the queue of search by tag tasks
  * Tasks are enqueued in this queue in case of excess of allowable HTTP connections number
  *
  * @param searchActor reference to actor which performs search by tag
  */
class ConnectionManagerActor @Inject() (@Named("searchActor") searchActor: ActorRef)
    extends Actor
    with ActorLogging {

  val queue = mutable.Queue[TagTask]()
  var numConnections = 0

  private def sendSearchMessage(tagTasks: Seq[TagTask]) = searchActor ! Search(tagTasks)

  private def logNumConnections() = log.info(s"Number of opened connections is ${numConnections}")

  private def logPerformingSearch(tasks: Seq[TagTask]) =
    log.info(s"Performing search for tags [${getTags(tasks).mkString(", ")}]")

  private def tryDequeue() =
    if (!queue.isEmpty) {
      val tagTask = queue.dequeue()
      numConnections += 1
      logPerformingSearch(Seq(tagTask))
      sendSearchMessage(Seq(tagTask))
    }

  private def processNewTasks(tagTasks: Seq[TagTask]) = {
    if (numConnections < maxConnections) {
      val numNewConnections = Seq(maxConnections - numConnections, tagTasks.size).min
      val (performTasks, enqueueTasks) = tagTasks.splitAt(numNewConnections)
      numConnections += numNewConnections
      log.info(s"Enqueuing tasks for tags [${getTags(enqueueTasks).mkString(", ")}]")
      queue ++= enqueueTasks
      logPerformingSearch(performTasks)
      sendSearchMessage(performTasks)
    } else {
      log.info(s"Enqueuing tasks for tags [${getTags(tagTasks).mkString(", ")}]")
      queue ++= tagTasks
    }
    logNumConnections()
  }

  def receive = {
    case NewTasks(tagTasks) => processNewTasks(tagTasks)
    case TaskFinished =>
      numConnections -= 1
      logNumConnections()
      log.info("Trying to pull out task from queue")
      tryDequeue()
      logNumConnections()
  }
}

object ConnectionManagerActor {
  import scala.concurrent.ExecutionContext.Implicits.global

  case class NewTasks(tagTasks: Seq[TagTask])
  case object TaskFinished

  val maxConnections = ConfigFactory.load().getInt("search.maxHttpConnections")
  type TagTask = (String, Promise[Seq[SearchResult]])

  /**
    * Extracts tags from tag tasks
    *
    * @param tagTasks tag tasks from which tags should be extracted
    * @return tags corresponding input tag tasks
    */
  def getTags(tagTasks: Seq[TagTask]) = tagTasks.map(_._1)

  /**
    * Creates `TagTask` object for each tag, sends these tasks to `ConnectionManagerActor`
    * and returns `Future` which contains results of these search by tag tasks
    *
    * @param tags sequence of tag words on which the search is to be carried out
    * @param connectionManager reference to [[actors.ConnectionManagerActor]]
    * @return `Future` object which contains results of search by input tags
    */
  def getSearchResultsFuture(tags: Seq[String])(implicit connectionManager: ActorRef): Future[Seq[SearchResult]] = {
    val (futureSearchResults, promises) = tags.map { _ =>
      val promise = Promise[Seq[SearchResult]]()
      (promise.future, promise)
    }.unzip
    val tagTasks = tags.zip(promises)
    val searchResultsFuture = Future.sequence(futureSearchResults).map(_.flatten)
    connectionManager ! NewTasks(tagTasks)
    searchResultsFuture
  }
}