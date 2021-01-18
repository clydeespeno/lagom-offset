package entity

import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventShards, AggregateEventTag}
import play.api.libs.json.{Format, Json}

sealed trait RandomEvent extends AggregateEvent[RandomEvent] {
  override def aggregateTag: AggregateEventShards[RandomEvent] = RandomEvent.Tag
}

object RandomEvent {
  val NumShards = 4
  val Tag: AggregateEventShards[RandomEvent] = AggregateEventTag.sharded[RandomEvent](NumShards)
}

case class RandomValueAdded(value: RandomValue) extends RandomEvent

object RandomValueAdded {
  implicit val format: Format[RandomValueAdded] = Json.format
}
