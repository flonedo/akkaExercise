package bank

import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions

object AppConfig {

  implicit def asFiniteDuration(d: java.time.Duration): FiniteDuration =
    scala.concurrent.duration.Duration.fromNanos(d.toNanos)

  /** Generic configs */
  private val config: Config = ConfigFactory.load
  lazy val environment: String = config.getString("env")
  lazy val serviceName: String = config.getString("service")
  lazy val askTimeout: FiniteDuration = config getDuration "ask.timeout"
  lazy val serviceInterface: String = config.getString("http.interface")
  lazy val servicePort: Int = config.getInt("http.port")

  /** read stream-consumer configs */
  private val readSideCfg: Config = config getConfig "read-side"
  lazy val readBatchSize: Int = readSideCfg getInt "batch.size"
  lazy val readWindow: FiniteDuration = readSideCfg getDuration "batch.window"
  lazy val readDelay: FiniteDuration = readSideCfg getDuration "delay"

  /** akka management k8s */
  private val discoveryCfg: Config = config getConfig "akka.management.cluster.bootstrap.contact-point-discovery"
  lazy val akkaClusterBootstrapDiscoveryMethod: String = discoveryCfg getString "discovery-method"
  lazy val akkaClusterBootstrapServiceName: String = discoveryCfg getString "service-name"
  lazy val akkaClusterBootstrapKubernetes: Boolean = akkaClusterBootstrapDiscoveryMethod == "kubernetes-api"

  val filePath = "/project/logdb.txt"

}
