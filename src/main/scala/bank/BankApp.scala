package bank

import akka.actor.{ActorSystem, PoisonPill, Scheduler}
import akka.cluster.Cluster
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.dispatch.MessageDispatcher
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.pattern.ask
import akka.util.Timeout
import bank.actor.Messages.Done
import bank.actor.projector.BankAccountEventProjectorActor
import bank.actor.projector.export.BankAccountLogExporter
import bank.actor.write.{ActorSharding, BankAccountWriterActor, PersonWriterActor}
import bank.domain.BankAccount
import bank.domain.BankAccount.BankAccountCommand
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import scala.concurrent.Await
import scala.concurrent.duration._

object BankApp extends HttpApp with ActorSharding with App {

  override implicit val system: ActorSystem = ActorSystem(AppConfig.serviceName, ConfigFactory.load())

  private implicit val scheduler: Scheduler = system.scheduler

  private lazy val cluster = Cluster(system)
  private implicit lazy val logger: LoggingAdapter = system.log
  private implicit val timeout: Timeout = Timeout(AppConfig.askTimeout)

  val dbFilePath = AppConfig.dbFilePath
  val offsetFilePath = AppConfig.offsetFilePath

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
    //createClusterShardingActors()
  })

  cluster.registerOnMemberRemoved({
    logger.info(s"Member removed: ${cluster.selfAddress}")
    cluster.leave(cluster.selfAddress)
  })

  private def startSystem(): Unit = {
    createClusterSingletonActors()
    // This will start the server until the return key is pressed
    createClusterShardingActors()
    startServer(AppConfig.serviceInterface, AppConfig.servicePort, system)

    stopSystem()
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
    /*ClusterSharding(system).start(
      typeName = PersonWriterActor.name,
      entityProps = PersonWriterActor.props(),
      settings = ClusterShardingSettings(system),
      extractEntityId = PersonWriterActor.extractEntityId,
      extractShardId = PersonWriterActor.extractShardId
    )*/
  }

  private def createClusterSingletonActors(): Unit = {
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = BankAccountEventProjectorActor.props(new BankAccountLogExporter(dbFilePath, offsetFilePath)),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system)
      ),
      BankAccountEventProjectorActor.name
    )
  }

  def routes: Route = {
    path("createAccount") {
      post {
        entity(as[BankAccount.Create])(forwardRequest)
      }
    } ~
      path("deposit") {
        post {
          entity(as[BankAccount.Deposit])(forwardRequest)
        }
      } ~
      path("withdraw") {
        post {
          entity(as[BankAccount.Withdraw])(forwardRequest)
        }
      }
  }

  def forwardRequest[R <: BankAccountCommand]: R => Route =
    (request: R) => {
      onSuccess(accountRegion ? request) {
        case Done => complete(StatusCodes.OK -> s"${request}")
        case e    => complete(StatusCodes.BadRequest -> e.toString)
      }
    }

}
