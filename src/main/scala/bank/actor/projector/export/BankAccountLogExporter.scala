package bank.actor.projector.export

import java.io.{File, FileWriter}
import java.util.UUID

import akka.persistence.query.TimeBasedUUID
import bank.actor.projector.ProjectionIndexer
import bank.domain.BankAccount.BankAccountEvent
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

class BankAccountLogExporter(file: File) extends ProjectionIndexer[BankAccountEvent] with LazyLogging {

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  override val name: String = "bank-account-export"

  override val eventsTag: String = "bank-account-details"

  override def readOffset(): Option[TimeBasedUUID] = {
    val uuidLenght = 36
    val lines = Source.fromFile(file).getLines().toVector
    lines.size match {
      case 0 => None
      case i => Some(TimeBasedUUID(UUID.fromString(lines(i).takeRight(uuidLenght))))
    }
  }

  override def indexEvents(events: Seq[BankAccountEvent], offset: TimeBasedUUID): Future[Unit] = {
    Future {
      events.foreach { event =>
        project(event, offset)
        logger.info(s"${event} ${offset}")
      }
    }
  }

  def project(event: BankAccountEvent, offset: TimeBasedUUID): Unit = {
    val fw = new FileWriter(file)
    try {
      fw.append(s"${event} ${offset}")
    } finally {
      fw.close()
    }
  }

}
