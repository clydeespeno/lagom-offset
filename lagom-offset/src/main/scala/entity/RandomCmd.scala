package entity

import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import play.api.libs.json.{Format, JsString, Reads, Writes}

import java.util.UUID

sealed trait RandomCmd

case object Randomize extends RandomCmd with ReplyType[UUID] {
  implicit val format: Format[Randomize.type] = Format(
    Reads(_.validate[String].map({
      case "Randomize" => Randomize
      case s => throw new IllegalArgumentException(s"Invalid command name $s")
    })),
    Writes(_ => JsString("Randomize"))
  )
}

case object ReadLatest extends RandomCmd with ReplyType[RandomValue] {
  implicit val format: Format[ReadLatest.type] = Format(
    Reads(_.validate[String].map({
      case "ReadLatest" => ReadLatest
      case s => throw new IllegalArgumentException(s"Invalid command name $s")
    })),
    Writes(_ => JsString("ReadLatest"))
  )
}
