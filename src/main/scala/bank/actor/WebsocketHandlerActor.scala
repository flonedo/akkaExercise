package bank.actor

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.pattern.pipe
import akka.stream.scaladsl.{Keep, Sink, Source, StreamRefs}
import akka.stream.{ActorMaterializer, OverflowStrategy, SourceRef}
import bank.actor.Messages.Done
import bank.actor.WebsocketConnectionActor.{Stopped, Update}
import bank.actor.WebsocketHandlerActor.{CloseConnection, OpenConnection}
import bank.actor.write.ActorSharding
import org.reactivestreams.Publisher

import scala.collection.mutable.Map
import scala.concurrent.Future
import scala.concurrent.duration._

class WebsocketHandlerActor() extends Actor with ActorSharding with ActorLogging {

  override implicit val system: ActorSystem = context.system
  implicit val dispatcher = context.dispatcher

  implicit val materializer = ActorMaterializer()

  var tenantId: Option[String] = None
  private var openConnections: Map[String, ActorRef] = Map.empty

  override def receive: Receive = {
    case newConnection: OpenConnection => {
      if (tenantId.isEmpty) tenantId = Some(newConnection.tenantId)
      val connActor = context.actorOf(Props(new WebsocketConnectionActor(newConnection.connectionId)))
      openConnections += (newConnection.connectionId -> connActor)
      connActor.forward(newConnection)
      log.info("({}) Websocket started! connection id: {}", tenantId, newConnection.connectionId)
    }
    case Notify(id: String, m: String) => {
      log.info("({}) Received update: {}", m)
      openConnections.foreach { conn =>
        conn._2 ! Update(TextMessage.apply(m))
      }
    }
    case CloseConnection(_, id) =>
      val connectionToKill = openConnections.get(id)
      context.stop(connectionToKill.get)
      log.info("({}) Killed websocket actor! Id: {}", tenantId.getOrElse(""))
      sender() ! Done
    case Stopped(id) =>
      openConnections.remove(id)
  }
}

object WebsocketHandlerActor {

  case class OpenConnection(tenantId: String, connectionId: String)

  case class CloseConnection(tenantId: String, id: String)

  val numberOfShards = 100

  val name = "websocket-handler-actor"

  def props(): Props = Props(new WebsocketHandlerActor())

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case m: OpenConnection  => (m.tenantId, m)
    case m: Notify          => (m.tenantId, m)
    case m: CloseConnection => (m.tenantId, m)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    def computeShardId(entityId: ShardRegion.EntityId): ShardRegion.ShardId =
      (math.abs(entityId.hashCode()) % numberOfShards).toString

    {
      case m: OpenConnection           => computeShardId(m.connectionId)
      case m: Notify                   => computeShardId(m.tenantId)
      case ShardRegion.StartEntity(id) => computeShardId(id)
      case m: CloseConnection          => computeShardId(m.tenantId)
    }
  }

}
