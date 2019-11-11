package bank.actor

import akka.http.scaladsl.model.ws.Message
import akka.stream.SourceRef

object Messages {

  case object Done
}

case class Notify(id: String, message: String)

case class Opened(ref: Option[SourceRef[Message]])
