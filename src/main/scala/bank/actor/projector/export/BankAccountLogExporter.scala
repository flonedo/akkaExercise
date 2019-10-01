package bank.actor.projector.export

import java.io.{File, FileWriter}
import java.util.UUID

import akka.persistence.query.TimeBasedUUID
import bank.actor.projector.ProjectionIndexer
import bank.domain.BankAccount.BankAccountEvent
import com.typesafe.scalalogging.LazyLogging

import scala.io.Source
import scala.util.{Failure, Success, Try}

class BankAccountLogExporter(eventsFile: File, offsetFile: File)
    extends ProjectionIndexer[BankAccountEvent]
    with LazyLogging {

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  override val name: String = "bank-account-export"

  override val eventsTag: String = "bank-account-details"

  override def readOffset(): Option[TimeBasedUUID] = {
    val lines = Source.fromFile(offsetFile).getLines().toString()
    Try(UUID.fromString(lines)).flatMap(x => Try(TimeBasedUUID(x))) match {
      case Success(value) => Some(value)
      case Failure(_)     => None

    }
  }

  override def indexEvents(events: Seq[BankAccountEvent], offset: TimeBasedUUID): Either[Exception, TimeBasedUUID] = {
    val inserts = events.map { event =>
      logger.info(offset.value.toString)
      project(event, offset)
    }
    val res: Either[Exception, TimeBasedUUID] = inserts.reduceLeft((x, y) => if (x.isLeft) x else y)
    res match {
      case Right(value) => writeOffset(offset)
      case Left(error)  => ()
    }
    res
  }

  def writeOffset(offset: TimeBasedUUID): Unit = {
    val offsetFw = new FileWriter(offsetFile)
    try {
      offsetFw.append(s"${offset}")
    } finally {
      offsetFw.close()
    }
  }

  def project(event: BankAccountEvent, offset: TimeBasedUUID): Either[Exception, TimeBasedUUID] = {
    val eventsFw = new FileWriter(eventsFile)
    try {
      eventsFw.append(s"${event} ${offset}")
      Right(offset)
    } catch {
      case e: Exception => Left(e)
    } finally {
      eventsFw.close()
    }
  }

}
