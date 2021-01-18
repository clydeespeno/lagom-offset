package entity

import play.api.libs.json.{Format, Json}

import java.time.OffsetDateTime
import java.util.{Random, UUID}

case class RandomState(uuid: UUID, latest: Option[RandomValue] = None)

object RandomState {
  implicit val format: Format[RandomState] = Json.format
}

case class RandomValue(date: OffsetDateTime, random: String)

object RandomValue {
  implicit val format: Format[RandomValue] = Json.format

  def apply(): RandomValue = RandomValue(
    date = OffsetDateTime.now(),
    random = getSaltString
  )

  private def getSaltString: String = {
    val SALTCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"
    val salt = new StringBuilder
    val rnd = new Random
    while ({
      salt.length < 18
    }) { // length of the random string.
      val index = (rnd.nextFloat * SALTCHARS.length).toInt
      salt.append(SALTCHARS.charAt(index))
    }
    val saltStr = salt.toString
    saltStr
  }
}
