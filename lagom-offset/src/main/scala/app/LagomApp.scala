package app

import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.lightbend.lagom.scaladsl.server.{LagomApplication, LagomApplicationContext, LagomApplicationLoader, LagomServer, LagomServerComponents}
import com.softwaremill.macwire.wire
import entity.RandomEntity
import play.api.libs.ws.ahc.AhcWSComponents
import readside.RandomReadSideProc

abstract class LagomApp (context: LagomApplicationContext) extends LagomApplication(context)
  with LagomServerComponents
  with CassandraPersistenceComponents
  with AhcWSComponents {

  lazy val lagomServer: LagomServer = serverFor[LagomService](wire[LagomServiceImpl])
  lazy val jsonSerializerRegistry: JsonSerializerRegistry = LagomAppJsonSerializer

  persistentEntityRegistry.register(wire[RandomEntity])
  readSide.register(wire[RandomReadSideProc])
}

class LagomAppLoader extends LagomApplicationLoader {
  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new LagomApp(context) with LagomDevModeComponents

  override def load(context: LagomApplicationContext): LagomApplication =
    new LagomApp(context) {
      override def serviceLocator: ServiceLocator = NoServiceLocator
    }
}