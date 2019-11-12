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
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.util.Timeout
import bank.actor.Messages.Done
import bank.actor.WebsocketHandlerActor.{CloseConnection, OpenConnection}
import bank.actor.projector.BankAccountEventProjectorActor
import bank.actor.projector.export.BankAccountLogExporter
import bank.actor.write.{ActorSharding, BankAccountWriterActor}
import bank.actor.{Opened, WebsocketHandlerActor}
import bank.domain.BankAccount
import bank.domain.BankAccount.BankAccountCommand
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/*** sbt -Denv=local run ***/
object BankApp extends HttpApp with ActorSharding with App {

  override implicit val system: ActorSystem = ActorSystem(AppConfig.serviceName, ConfigFactory.load())
  implicit val materializer = ActorMaterializer()

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
    ClusterSharding(system).start(
      typeName = WebsocketHandlerActor.name,
      entityProps = WebsocketHandlerActor.props(),
      settings = ClusterShardingSettings(system),
      extractEntityId = WebsocketHandlerActor.extractEntityId,
      extractShardId = WebsocketHandlerActor.extractShardId
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
    path("create") {
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
      } ~
      path("socket" / connectionId) { connectionId =>
        val sink = Sink.onComplete(_ => killActor("Tenant", connectionId.toString))
        onSuccess(websocketRegion ? OpenConnection("Tenant", connectionId.toString)) {
          case Opened(Some(ref)) =>
            val source = ref.source
            println(source)
            val flow = Flow.fromSinkAndSourceCoupledMat(sink, source)(Keep.both)
            handleWebSocketMessages(flow)
          case Failure(exception: Exception) => complete(StatusCodes.BadRequest -> exception.toString)
        }
      }
  }

  def connectionId: BankApp.Segment.type = Segment

  def forwardRequest[R <: BankAccountCommand]: R => Route =
    (request: R) => {
      onSuccess(accountRegion ? request) {
        case Done => complete(StatusCodes.OK -> s"${request}")
        case e    => complete(StatusCodes.BadRequest -> e.toString)
      }
    }

  def killActor(tenantId: String, id: String): Unit = {
    val close = websocketRegion ? CloseConnection(tenantId, id)
    close.onComplete {
      case Success(_) => complete(StatusCodes.OK)
      case Failure(_) => complete(StatusCodes.ImATeapot)
    }
  }
}
