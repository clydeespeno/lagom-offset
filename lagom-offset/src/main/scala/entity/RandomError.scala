package entity

import java.util.UUID

sealed trait RandomError extends Throwable {
  def msg: String
}

case class NoRandomValueCreated(uuid: UUID) extends RandomError {
  val msg = s"No value created for random $uuid yet"
}
