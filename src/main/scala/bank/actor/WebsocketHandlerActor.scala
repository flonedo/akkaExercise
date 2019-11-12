package bank.actor

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.sharding.ShardRegion
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.ActorMaterializer
import bank.actor.Messages.Done
import bank.actor.WebsocketConnectionActor.{Stopped, Update}
import bank.actor.WebsocketHandlerActor.{CloseConnection, OpenConnection}
import bank.actor.write.ActorSharding

import scala.collection.mutable.Map

class WebsocketHandlerActor(var tenantId: String) extends Actor with ActorSharding with ActorLogging {

  override implicit val system: ActorSystem = context.system
  implicit val dispatcher = context.dispatcher

  implicit val materializer = ActorMaterializer()

  private var openConnections: Map[String, ActorRef] = Map.empty

  override def receive: Receive = {
    case newConnection: OpenConnection => {
      if (tenantId == "") tenantId = newConnection.tenantId
      val connActor = context.actorOf(Props(new WebsocketConnectionActor(newConnection.connectionId)))
      println(connActor)
      openConnections += (newConnection.connectionId -> connActor)
      println(openConnections)
      connActor.forward(newConnection)
      log.info("({}) Websocket started! connection id: {}", tenantId, newConnection.connectionId)
    }
    case Notify(_, m: String) => {
      log.info("({}) Received updates for tenant: {}", m, tenantId)
      println(openConnections)
      openConnections.foreach { conn =>
        log.info("({}) updating child actor {}", conn._1)
        conn._2 ! Update(TextMessage.apply(m))
      }
    }
    case CloseConnection(_, id) =>
      val connectionToKill = openConnections.get(id)
      if (connectionToKill.nonEmpty) context.stop(connectionToKill.get)
      log.info("({}) Killed websocket actor! Id: {}", tenantId, id)
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

  def props(): Props = Props(new WebsocketHandlerActor(tenantId = ""))

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case m: OpenConnection  => (m.tenantId, m)
    case m: Notify          => (m.tenantId, m)
    case m: CloseConnection => (m.tenantId, m)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    def computeShardId(entityId: ShardRegion.EntityId): ShardRegion.ShardId =
      (math.abs(entityId.hashCode()) % numberOfShards).toString

    {
      case m: OpenConnection                 => computeShardId(m.tenantId)
      case m: Notify                         => computeShardId(m.tenantId)
      case ShardRegion.StartEntity(tenantId) => computeShardId(tenantId)
      case m: CloseConnection                => computeShardId(m.tenantId)
    }
  }

}
