package bank

import akka.actor.{ActorSystem, PoisonPill, Scheduler}
import akka.cluster.Cluster
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.event.LoggingAdapter
import bank.actor.projector.export.BankAccountLogExporter
import bank.actor.projector.{BankAccountEventProjectorActor, ProjectionIndexer}
import bank.actor.write.{ActorSharding, BankAccountWriterActor, PersonWriterActor}
import akka.management.scaladsl.AkkaManagement
import java.io.File

import akka.dispatch.MessageDispatcher
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directive, ExceptionHandler, HttpApp, RejectionHandler, Route}
import akka.http.scaladsl.settings.ServerSettings
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._

object BankApp extends HttpApp with ActorSharding with App {

  override implicit val system: ActorSystem = ActorSystem(AppConfig.serviceName)

  private implicit val scheduler: Scheduler = system.scheduler

  private lazy val cluster = Cluster(system)
  private implicit lazy val logger: LoggingAdapter = system.log
  private implicit val timeout: Timeout = Timeout(AppConfig.askTimeout)

  val dbFile = new File(AppConfig.dbFilePath)
  if (!dbFile.exists()) {
    dbFile.createNewFile()
  }
  val offsetFile = new File(AppConfig.offsetFilePath)
  if (!offsetFile.exists()) {
    offsetFile.createNewFile()
  }

  private implicit val blockingDispatcher: MessageDispatcher =
    system.dispatchers.lookup(id = "akka-exercise-blocking-dispatcher")

  startSystem()

  if (AppConfig.akkaClusterBootstrapKubernetes) {
    // Akka Management hosts the HTTP routes used by bootstrap
    AkkaManagement(system).start()
    // Starting the bootstrap process needs to be done explicitly
    ClusterBootstrap(system).start()
  }

  cluster.registerOnMemberUp({
    logger.info(s"Member up: ${cluster.selfAddress}")
    createClusterShardingActors()
  })

  cluster.registerOnMemberRemoved({
    logger.info(s"Member removed: ${cluster.selfAddress}")
    cluster.leave(cluster.selfAddress)
  })

  private def startSystem(): Unit = {
    createClusterSingletonActors()
    // This will start the server until the return key is pressed
    //startServer(AppConfig.serviceInterface, AppConfig.servicePort, system)

    //stopSystem()
  }

  private def stopSystem(): Unit = {
    logger.info(s"Terminating member: ${cluster.selfAddress}")
    system.terminate()
    Await.result(system.whenTerminated, 60.seconds)
  }

  private def createClusterShardingActors(): Unit = {
    ClusterSharding(system).start(
      typeName = BankAccountWriterActor.name,
      entityProps = BankAccountWriterActor.props(),
      settings = ClusterShardingSettings(system),
      extractEntityId = BankAccountWriterActor.extractEntityId,
      extractShardId = BankAccountWriterActor.extractShardId
    )
    ClusterSharding(system).start(
      typeName = PersonWriterActor.name,
      entityProps = PersonWriterActor.props(),
      settings = ClusterShardingSettings(system),
      extractEntityId = PersonWriterActor.extractEntityId,
      extractShardId = PersonWriterActor.extractShardId
    )
  }

  private def createClusterSingletonActors(): Unit = {
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = BankAccountEventProjectorActor.props(new BankAccountLogExporter(dbFile, offsetFile)),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system)
      ),
      BankAccountEventProjectorActor.name
    )
  }

  def routes: Route = {
    path("createAccount") {
      get {
        complete(StatusCodes.OK)
      }
    }
  }

}
