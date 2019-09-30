package it.warda.seecommerce

import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.JavaConverters._
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


  /** akka management k8s */
  private val discoveryCfg: Config = config getConfig "akka.management.cluster.bootstrap.contact-point-discovery"
  lazy val akkaClusterBootstrapDiscoveryMethod: String = discoveryCfg getString "discovery-method"
  lazy val akkaClusterBootstrapServiceName: String = discoveryCfg getString "service-name"
  lazy val akkaClusterBootstrapKubernetes: Boolean = akkaClusterBootstrapDiscoveryMethod == "kubernetes-api"

  /**file logging*/
}
