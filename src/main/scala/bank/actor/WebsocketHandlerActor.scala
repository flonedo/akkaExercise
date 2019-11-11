package bank.actor

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorSystem, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.{ActorMaterializer, OverflowStrategy, SourceRef}
import akka.stream.scaladsl.{Keep, Sink, Source, StreamRefs}
import bank.BankApp
import bank.actor.Messages.Done
import bank.actor.write.ActorSharding
import bank.domain.WebsocketConnection.WebsocketCommand
import bank.domain.{EmptyWebsocketConnection, OpenWebsocketConnection, WebsocketConnection}
import akka.pattern.pipe

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class WebsocketHandlerActor extends Actor with ActorSharding with ActorLogging {

  override implicit val system: ActorSystem = context.system
  implicit val dispatcher = context.dispatcher

  var state: WebsocketConnection = EmptyWebsocketConnection
  implicit val materializer = ActorMaterializer()

  context.setReceiveTimeout(120.seconds)
  private def receivePassivate: Receive = {
    case ReceiveTimeout        => context.parent ! ShardRegion.Passivate
    case ShardRegion.Passivate => context.stop(self)
  }

  override def receive: Receive = {
    case operation: WebsocketCommand => {
      operation.applyTo(state) match {

        case Right(Some(event)) => {
          state = update(state, event)
          val pub = state.asInstanceOf[OpenWebsocketConnection].publisher
          val ref: Future[SourceRef[Message]] = Source.fromPublisher(pub).runWith(StreamRefs.sourceRef())
          ref.map(r => Opened(Some(r))).pipeTo(sender())
          log.info("({}) Websocket started! Id: {}", event.connectionId)
        }

        case Right(None) => {
          sender() ! Opened(None)
        }
        case Left(error) => {
          sender ! error
        }
      }
    }
    case Notify(id: String, m: String) => {
      state match {
        case open: OpenWebsocketConnection =>
          log.info("({}) Received update: {}", m)
          open.down ! (TextMessage.apply(m))

        case _ => ()
      }
    }
  }

  protected def update(state: WebsocketConnection, event: WebsocketConnection.WebsocketEvent): WebsocketConnection =
    event.applyTo(state)

}

object WebsocketHandlerActor {

  val numberOfShards = 100

  val name = "websocket-handler-actor"

  def props(): Props = Props(new WebsocketHandlerActor())

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case m: WebsocketCommand => (m.connectionId, m)
    case m: Notify           => (m.id, m)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    def computeShardId(entityId: ShardRegion.EntityId): ShardRegion.ShardId =
      (math.abs(entityId.hashCode()) % numberOfShards).toString

    {
      case m: WebsocketCommand         => computeShardId(m.connectionId)
      case m: Notify                   => computeShardId(m.id)
      case ShardRegion.StartEntity(id) => computeShardId(id)
    }
  }

}
