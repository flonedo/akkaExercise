package bank.actor

import akka.actor.{Actor, ActorLogging, ActorSystem, ReceiveTimeout}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.pattern.pipe
import akka.stream.scaladsl.{Keep, Sink, Source, StreamRefs}
import akka.stream.{ActorMaterializer, OverflowStrategy, SourceRef}
import bank.actor.WebsocketConnectionActor.{Stopped, Update}
import bank.actor.WebsocketHandlerActor.OpenConnection

import scala.concurrent.Future
import scala.concurrent.duration._

class WebsocketConnectionActor(connectionId: String) extends Actor with ActorLogging {

  private val bufferSize = 1000
  implicit val system: ActorSystem = context.system
  implicit val dispatcher = context.dispatcher

  implicit val materializer = ActorMaterializer()

  val (ref, publisher) =
    Source.actorRef[Message](bufferSize, OverflowStrategy.fail).toMat(Sink.asPublisher(fanout = false))(Keep.both).run()

  val sourceRef: Future[SourceRef[Message]] = Source.fromPublisher(publisher).runWith(StreamRefs.sourceRef())

  context.setReceiveTimeout(30.seconds)

  override def receive: Receive = {
    case OpenConnection(_, _) =>
      log.info("({}) Sending source ref {}")
      sourceRef.map(r => Opened(Some(r))).pipeTo(sender())
      ref ! TextMessage.apply("prova")
    case Update(m) =>
      log.info("({}) Received update: {}", m)
      ref ! m
    case ReceiveTimeout =>
      context.parent ! Stopped(connectionId)
      context.stop(self)
  }
}

object WebsocketConnectionActor {

  //def props(): Props = Props(new WebsocketConnectionActor(connectionId))

  case class Stopped(id: String)
  case class Update(m: Message)
}
