package bank.actor.projector.export

import java.io.{File, FileWriter}

import akka.persistence.query.TimeBasedUUID
import bank.domain.Person.PersonEvent
import com.typesafe.scalalogging.LazyLogging

class PersonLogExporter(eventsFile: String, offsetFile: String)
    extends CommonLogExporter[PersonEvent]
    with LazyLogging {

  override val name: String = "person-export"

  override val eventsTag: String = "person-details"

  override val offsetFilePath = offsetFile

  override def indexEvents(events: Seq[PersonEvent], offset: TimeBasedUUID): Either[Exception, TimeBasedUUID] = {
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

  def project(event: PersonEvent, offset: TimeBasedUUID): Either[Exception, TimeBasedUUID] = {
    val file = new File(eventsFile)
    val eventsFw = new FileWriter(file, true)
    try {
      eventsFw.write(s"${event} ${offset.value}\n")
      Right(offset)
    } catch {
      case e: Exception => Left(e)
    } finally {
      eventsFw.close()
    }
  }
}
