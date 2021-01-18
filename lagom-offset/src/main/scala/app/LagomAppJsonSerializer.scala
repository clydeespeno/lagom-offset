package app

import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import entity.{RandomState, RandomValue, RandomValueAdded, Randomize, ReadLatest}

import scala.collection.immutable

object LagomAppJsonSerializer extends JsonSerializerRegistry {
  override def serializers: immutable.Seq[JsonSerializer[_]] = List(
    JsonSerializer[Randomize.type],
    JsonSerializer[ReadLatest.type],
    JsonSerializer[RandomValueAdded],
    JsonSerializer[RandomState],
    JsonSerializer[RandomValue]
  )
}
