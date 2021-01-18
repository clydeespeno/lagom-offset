package readside

import com.datastax.driver.core.BoundStatement
import com.lightbend.lagom.scaladsl.persistence.{AggregateEventTag, EventStreamElement, ReadSideProcessor}
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraReadSide
import entity.{RandomEvent, RandomValueAdded}
import org.slf4j.LoggerFactory
import readside.RandomReadSideProc.ReadSideId

import scala.concurrent.{ExecutionContext, Future}

class RandomReadSideProc(readSide: CassandraReadSide)(implicit ctx: ExecutionContext) extends ReadSideProcessor[RandomEvent] {

  private val logger = LoggerFactory.getLogger(classOf[RandomReadSideProc])

  override def buildHandler(): ReadSideProcessor.ReadSideHandler[RandomEvent] = readSide
    .builder[RandomEvent](ReadSideId)
    .setEventHandler[RandomValueAdded](writeEvent)
    .build()

  override def aggregateTags: Set[AggregateEventTag[RandomEvent]] = RandomEvent.Tag.allTags

  private def writeEvent(element: EventStreamElement[RandomValueAdded]): Future[List[BoundStatement]] = {
    Future {
      logger.info(s"Writing event e ${element.event}, with offset ${element.offset}, uuid ${element.entityId}")
      List()
    }
  }
}

object RandomReadSideProc {
  val ReadSideId = "random-handler-v1"
}
