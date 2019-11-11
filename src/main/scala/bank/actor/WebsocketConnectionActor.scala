package bank.actor

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, ReceiveTimeout}
import akka.http.scaladsl.model.ws.Message
import akka.stream.{ActorMaterializer, OverflowStrategy, SourceRef}
import akka.stream.scaladsl.{Keep, Sink, Source, StreamRefs}
import bank.actor.WebsocketConnectionActor.{Stop, Stopped, Update}
import akka.pattern.pipe
import bank.actor.WebsocketHandlerActor.OpenConnection

import scala.concurrent.duration._
import scala.concurrent.Future

class WebsocketConnectionActor(connectionId: String) extends Actor with ActorLogging {

  private val bufferSize = 1000
  implicit val system: ActorSystem = context.system
  implicit val dispatcher = context.dispatcher

  implicit val materializer = ActorMaterializer()

  val (ref, publisher) =
    Source.actorRef[Message](bufferSize, OverflowStrategy.fail).toMat(Sink.asPublisher(fanout = false))(Keep.both).run()

  val sourceRef: Future[SourceRef[Message]] = Source.fromPublisher(publisher).runWith(StreamRefs.sourceRef())

  context.setReceiveTimeout(60.seconds)

  override def receive: Receive = {
    case OpenConnection(_, _) => sourceRef.map(r => Opened(Some(r))).pipeTo(sender())
    case Stop                 => ()
    case Update(m)            => ref ! m
    case ReceiveTimeout =>
      context.parent ! Stopped(connectionId)
      context.stop(self)
  }
}

object WebsocketConnectionActor {

  //def props(): Props = Props(new WebsocketConnectionActor(connectionId))

  case object Stop
  case class Stopped(id: String)
  case class Update(m: Message)
}
