package app

import akka.{Done, NotUsed}
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import entity.{RandomEntity, RandomValue, Randomize, ReadLatest}
import org.slf4j.LoggerFactory

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait LagomService extends Service {

  def createRandomData: ServiceCall[NotUsed, UUID]

  def getLatest(id: UUID): ServiceCall[NotUsed, RandomValue]

  def generateRandomData(n: Int): ServiceCall[NotUsed, Done]

  override def descriptor: Descriptor = {
    import Service._
    named("lagom-offset")
      .withCalls(
        restCall(Method.POST, "/random", createRandomData),
        restCall(Method.POST, "/random/generate/:n", generateRandomData _),
        restCall(Method.GET, "/random/:id", getLatest _)
      )
  }
}

class LagomServiceImpl(registry: PersistentEntityRegistry)(implicit ctx: ExecutionContext) extends LagomService {

  private val logger = LoggerFactory.getLogger(classOf[LagomServiceImpl])

  override def createRandomData: ServiceCall[NotUsed, UUID] = ServiceCall { _ =>
    for {
      entityId <- registry.refFor[RandomEntity](UUID.randomUUID().toString)
        .ask(Randomize)
    } yield entityId
  }

  override def getLatest(id: UUID): ServiceCall[NotUsed, RandomValue] = ServiceCall { _ =>
    for {
      latest <- registry.refFor[RandomEntity](id.toString)
        .ask(ReadLatest)
    } yield latest
  }

  override def generateRandomData(n: Int): ServiceCall[NotUsed, Done] = ServiceCall { _ =>for {
    _ <- Future.unit
    _ = (1 to n).foldLeft(Future.unit) { case (f, _) =>
      f.flatMap { _ =>
        for {
          entityId <- registry.refFor[RandomEntity](UUID.randomUUID().toString)
            .ask(Randomize)
          _ = logger.info(s"Created a new random value with id $entityId")
        } yield entityId
      }
    }
  } yield Done

  }

}