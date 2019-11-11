package bank.domain

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import bank.BankApp
import bank.domain.Domain.{DomainCommand, DomainEntity, DomainEvent}

sealed trait WebsocketConnection extends DomainEntity

case class OpenWebsocketConnection(connectionId: String) extends WebsocketConnection {

  implicit val system: ActorSystem = BankApp.system
  implicit val materializer = ActorMaterializer()

  private val bufferSize = 1000
  val (down, publisher) =
    Source.actorRef[Message](bufferSize, OverflowStrategy.fail).toMat(Sink.asPublisher(fanout = false))(Keep.both).run()
  //Source.fromPublisher(publisher).runWith(StreamRefs.sourceRef())
}

case object EmptyWebsocketConnection extends WebsocketConnection

object WebsocketConnection {
  trait WebsocketEvent extends DomainEvent[WebsocketConnection] {
    val connectionId: String
  }

  trait WebsocketCommand extends DomainCommand[WebsocketConnection, WebsocketEvent] {
    val connectionId: String
  }

  //implicit val dOpenConnection: Decoder[WebsocketConnection.OpenConnection] = deriveDecoder[OpenConnection]
  //implicit val eConnectionOpened: Encoder[WebsocketConnection.ConnectionOpened] = deriveEncoder[ConnectionOpened]

  case class OpenConnection(connectionId: String) extends WebsocketCommand {
    override def applyTo(domainEntity: WebsocketConnection): Either[String, Option[WebsocketEvent]] = {
      domainEntity match {
        case EmptyWebsocketConnection =>
          Right(Some(ConnectionOpened(connectionId)))
        case OpenWebsocketConnection(id) if id == connectionId =>
          Right(None) //Right(Some(ConnectionOpened(connectionId)))
        case _ => Left("error: connection is already initialized")
      }
    }
  }

  case class ConnectionOpened(connectionId: String) extends WebsocketEvent {
    override def applyTo(domainEntity: WebsocketConnection): OpenWebsocketConnection =
      OpenWebsocketConnection(connectionId)
  }
}
