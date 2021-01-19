package custom

import akka.actor.ActorSystem
import akka.persistence.cassandra.{SessionProvider, StorePathPasswordConfig}
import com.datastax.driver.core._
import com.datastax.driver.core.policies._
import com.google.common.util.concurrent.ListenableFuture
import com.typesafe.config.{Config, ConfigValueType}

import java.io.{File, FileInputStream}
import java.net.InetSocketAddress
import java.security.{KeyStore, SecureRandom}
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executor, TimeUnit}
import javax.net.ssl._
import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

class ConfigSessionProvider(system: ActorSystem, config: Config) extends SessionProvider {

  def connect()(implicit ec: ExecutionContext): Future[Session] = {
    val clusterId = config.getString("cluster-id")
    clusterBuilder(clusterId).flatMap { b =>
      val cluster = b.build()
      createQueryLogger() match {
        case Some(logger) => cluster.register(logger)
        case None         =>
      }
      toFuture(cluster.connectAsync())
    }
  }

  def toFuture[A](a: ListenableFuture[A])(implicit ec: ExecutionContext): Future[A] = {
    val promise = Promise[A]()
    a.addListener(new Runnable {
      def run(): Unit = promise.complete(Try(a.get()))
    }, ec.asInstanceOf[Executor])
    promise.future
  }

  protected def createQueryLogger(): Option[QueryLogger] = {
    if (config.getBoolean("log-queries"))
      Some(QueryLogger.builder().build())
    else None
  }

  val fetchSize = config.getInt("max-result-size")
  val protocolVersion: Option[ProtocolVersion] =
    config.getString("protocol-version") match {
      case "" => None
      case _  => Some(ProtocolVersion.fromInt(config.getInt("protocol-version")))
    }
  val port: Int = config.getInt("port")

  private[this] val connectionPoolConfig = config.getConfig("connection-pool")

  val poolingOptions = new PoolingOptions()
    .setNewConnectionThreshold(HostDistance.LOCAL, connectionPoolConfig.getInt("new-connection-threshold-local"))
    .setNewConnectionThreshold(HostDistance.REMOTE, connectionPoolConfig.getInt("new-connection-threshold-remote"))
    .setMaxRequestsPerConnection(HostDistance.LOCAL, connectionPoolConfig.getInt("max-requests-per-connection-local"))
    .setMaxRequestsPerConnection(HostDistance.REMOTE, connectionPoolConfig.getInt("max-requests-per-connection-remote"))
    .setConnectionsPerHost(
      HostDistance.LOCAL,
      connectionPoolConfig.getInt("connections-per-host-core-local"),
      connectionPoolConfig.getInt("connections-per-host-max-local"))
    .setConnectionsPerHost(
      HostDistance.REMOTE,
      connectionPoolConfig.getInt("connections-per-host-core-remote"),
      connectionPoolConfig.getInt("connections-per-host-max-remote"))
    .setPoolTimeoutMillis(connectionPoolConfig.getInt("pool-timeout-millis"))
    .setMaxQueueSize(connectionPoolConfig.getInt("max-queue-size"))

  val reconnectMaxDelay: FiniteDuration =
    config.getDuration("reconnect-max-delay", TimeUnit.MILLISECONDS).millis

  val speculativeExecution: Option[SpeculativeExecutionPolicy] =
    config.getInt("speculative-executions") match {
      case 0 => None
      case n =>
        val delayMs =
          config.getDuration("speculative-executions-delay", MILLISECONDS)
        Some(new ConstantSpeculativeExecutionPolicy(delayMs, n))
    }

  val metricsEnabled: Boolean = config.getBoolean("metrics-enabled")
  val jmxReportingEnabled: Boolean = config.getBoolean("jmx-reporting-enabled")

  def clusterBuilder(clusterId: String)(implicit ec: ExecutionContext): Future[Cluster.Builder] = {
    lookupContactPoints(clusterId).map { cp =>
      val b = Cluster.builder
        .withClusterName(s"${system.name}-${ConfigSessionProvider.clusterIdentifier.getAndIncrement()}")
        .addContactPointsWithPorts(cp.asJava)
        .withPoolingOptions(poolingOptions)
        .withReconnectionPolicy(new ExponentialReconnectionPolicy(1000, reconnectMaxDelay.toMillis))
        .withQueryOptions(new QueryOptions().setFetchSize(fetchSize))
        .withPort(port)

      speculativeExecution match {
        case Some(policy) => b.withSpeculativeExecutionPolicy(policy)
        case None         =>
      }

      protocolVersion match {
        case None    => b
        case Some(v) => b.withProtocolVersion(v)
      }

      val username = config.getString("authentication.username")
      if (username != "") {
        b.withCredentials(username, config.getString("authentication.password"))
      }

      val localDatacenter = config.getString("local-datacenter")
      if (localDatacenter != "") {
        val usedHostsPerRemoteDc = config.getInt("used-hosts-per-remote-dc")
        val policy =  DCAwareRoundRobinPolicy.builder
          .withLocalDc(localDatacenter)
          .withUsedHostsPerRemoteDc(usedHostsPerRemoteDc)
          .build()
        val whiteList = getListFromConfig(config, "white-list")
        if (whiteList.nonEmpty) {
          b.withLoadBalancingPolicy(
            WhiteListPolicy.ofHosts(policy, whiteList.asJava)
          )
        } else {
          b.withLoadBalancingPolicy(new TokenAwarePolicy(policy))
        }

      }

      val truststorePath = config.getString("ssl.truststore.path")
      if (truststorePath != "") {
        val trustStore =
          StorePathPasswordConfig(truststorePath, config.getString("ssl.truststore.password"))

        val keystorePath = config.getString("ssl.keystore.path")
        val keyStore: Option[StorePathPasswordConfig] =
          if (keystorePath != "") {
            val keyStore =
              StorePathPasswordConfig(keystorePath, config.getString("ssl.keystore.password"))
            Some(keyStore)
          } else None

        val context = SSLSetup.constructContext(trustStore, keyStore)

        b.withSSL(JdkSSLOptions.builder.withSSLContext(context).build())
      }

      val socketConfig = config.getConfig("socket")
      val socketOptions = new SocketOptions()
      socketOptions.setConnectTimeoutMillis(socketConfig.getInt("connection-timeout-millis"))
      socketOptions.setReadTimeoutMillis(socketConfig.getInt("read-timeout-millis"))

      val sendBufferSize = socketConfig.getInt("send-buffer-size")
      val receiveBufferSize = socketConfig.getInt("receive-buffer-size")

      if (sendBufferSize > 0) {
        socketOptions.setSendBufferSize(sendBufferSize)
      }
      if (receiveBufferSize > 0) {
        socketOptions.setReceiveBufferSize(receiveBufferSize)
      }

      b.withSocketOptions(socketOptions)
      if (!metricsEnabled) b.withoutMetrics()
      if (!jmxReportingEnabled) b.withoutJMXReporting()
      b
    }
  }

  /**
   * Subclass may override this method to perform lookup the contact points
   * of the Cassandra cluster asynchronously instead of reading them from the
   * configuration.
   *
   * @param clusterId the configured `cluster-id` to lookup
   */
  def lookupContactPoints(clusterId: String)(
    implicit ec: ExecutionContext): Future[immutable.Seq[InetSocketAddress]] = {
    val contactPoints = getListFromConfig(config, "contact-points")
    Future.successful(buildContactPoints(contactPoints, port))
  }

  def getListFromConfig(config: Config, key: String): List[String] = {
    config.getValue(key).valueType() match {
      case ConfigValueType.LIST => config.getStringList(key).asScala.toList
      // case ConfigValueType.OBJECT is needed to handle dot notation (x.0=y x.1=z) due to Typesafe Config implementation quirk.
      // https://github.com/lightbend/config/blob/master/config/src/main/java/com/typesafe/config/impl/DefaultTransformer.java#L83
      case ConfigValueType.OBJECT => config.getStringList(key).asScala.toList
      case ConfigValueType.STRING => config.getString(key).split(",").toList
      case _                      => throw new IllegalArgumentException(s"$key should be a List, Object or String")
    }
  }

  /**
   * Builds list of InetSocketAddress out of host:port pairs or host entries + given port parameter.
   */
  protected def buildContactPoints(
                                    contactPoints: immutable.Seq[String],
                                    port: Int): immutable.Seq[InetSocketAddress] = {
    contactPoints match {
      case null | Nil =>
        throw new IllegalArgumentException("A contact point list cannot be empty.")
      case hosts =>
        hosts.map { ipWithPort =>
          ipWithPort.split(":") match {
            case Array(host, port) => new InetSocketAddress(host, port.toInt)
            case Array(host)       => new InetSocketAddress(host, port)
            case msg =>
              throw new IllegalArgumentException(
                s"A contact point should have the form [host:port] or [host] but was: $msg.")
          }
        }
    }
  }
}

object ConfigSessionProvider {
  private val clusterIdentifier = new AtomicInteger()
}

object SSLSetup {

  /**
   * creates a new SSLContext
   */
  def constructContext(trustStore: StorePathPasswordConfig, keyStore: Option[StorePathPasswordConfig]): SSLContext = {

    val tmf = loadTrustManagerFactory(trustStore.path, trustStore.password)

    val trustManagers: Array[TrustManager] = tmf.getTrustManagers

    val keyManagers: Array[KeyManager] = keyStore
      .map {
        case StorePathPasswordConfig(path, password) =>
          val kmf = loadKeyManagerFactory(path, password)
          kmf.getKeyManagers
      }
      .getOrElse(Array.empty[KeyManager])

    val ctx = SSLContext.getInstance("SSL")

    ctx.init(keyManagers, trustManagers, new SecureRandom())

    ctx
  }

  def loadKeyStore(storePath: String, storePassword: String): KeyStore = {
    val ks = KeyStore.getInstance("JKS")
    val f = new File(storePath)
    if (!f.isFile)
      throw new IllegalArgumentException(s"JKSs path $storePath not found.")
    val is = new FileInputStream(f)

    try {
      ks.load(is, storePassword.toCharArray)
    } finally is.close()

    ks
  }

  def loadTrustManagerFactory(trustStorePath: String, trustStorePassword: String): TrustManagerFactory = {

    val ts = loadKeyStore(trustStorePath, trustStorePassword)
    val tmf =
      TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    tmf.init(ts)
    tmf
  }

  def loadKeyManagerFactory(keyStorePath: String, keyStorePassword: String): KeyManagerFactory = {

    val ks = loadKeyStore(keyStorePath, keyStorePassword)
    val kmf =
      KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    val keyPassword = keyStorePassword.toCharArray
    kmf.init(ks, keyPassword)
    kmf
  }
}
