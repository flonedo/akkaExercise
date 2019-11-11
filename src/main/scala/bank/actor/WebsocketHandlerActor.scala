package bank.actor

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.pattern.pipe
import akka.stream.scaladsl.{Keep, Sink, Source, StreamRefs}
import akka.stream.{ActorMaterializer, OverflowStrategy, SourceRef}
import bank.actor.Messages.Done
import bank.actor.WebsocketHandlerActor.{CloseConnection, OpenConnection}
import bank.actor.write.ActorSharding
import org.reactivestreams.Publisher

import scala.concurrent.Future
import scala.concurrent.duration._

class WebsocketHandlerActor() extends Actor with ActorSharding with ActorLogging {

  override implicit val system: ActorSystem = context.system
  implicit val dispatcher = context.dispatcher

  implicit val materializer = ActorMaterializer()

  private val bufferSize = 1000

  var id: Option[String] = None
  private var stream: Option[(ActorRef, Publisher[Message])] = None

  context.setReceiveTimeout(60.seconds)

  override def receive: Receive = {
    case OpenConnection(connId) => {
      if (id.isEmpty) {
        id = Some(connId)
        stream = Some(
          Source
            .actorRef[Message](bufferSize, OverflowStrategy.fail)
            .toMat(Sink.asPublisher(fanout = false))(Keep.both)
            .run()
        )
        val ref: Future[SourceRef[Message]] = Source.fromPublisher(stream.get._2).runWith(StreamRefs.sourceRef())
        ref.map(r => Opened(Some(r))).pipeTo(sender())
        log.info("({}) Websocket started! Id: {}", id)
      } else {
        sender() ! Opened(None)
      }
    }
    case Notify(id: String, m: String) => {
      log.info("({}) Received update: {}", m)
      if (stream.nonEmpty) stream.get._1 ! TextMessage.apply(m)
    }
    case CloseConnection(_) =>
      sender() ! Done
      log.info("({}) Killed websocket actor! Id: {}", id.getOrElse(""))
      context.stop(self)
    case ReceiveTimeout =>
      log.info("({}) Killed websocket actor! Id: {}", id)
      if (stream.nonEmpty) {
        stream.get._1 ! TextMessage.apply("killed")
        context.stop(stream.get._1)
      }
      context.stop(self)
  }
}

object WebsocketHandlerActor {

  case class OpenConnection(connectionId: String)

  case class CloseConnection(id: String)

  val numberOfShards = 100

  val name = "websocket-handler-actor"

  def props(): Props = Props(new WebsocketHandlerActor())

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case m: OpenConnection  => (m.connectionId, m)
    case m: Notify          => (m.id, m)
    case m: CloseConnection => (m.id, m)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    def computeShardId(entityId: ShardRegion.EntityId): ShardRegion.ShardId =
      (math.abs(entityId.hashCode()) % numberOfShards).toString

    {
      case m: OpenConnection           => computeShardId(m.connectionId)
      case m: Notify                   => computeShardId(m.id)
      case ShardRegion.StartEntity(id) => computeShardId(id)
      case CloseConnection(id)         => computeShardId(id)
    }
  }

}
