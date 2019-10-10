package bank.actor.projector.export

import java.io.FileWriter
import java.util.UUID

import akka.persistence.query.TimeBasedUUID
import bank.actor.projector.ProjectionIndexer

import scala.io.Source
import scala.util.{Failure, Try}

trait CommonLogExporter[T] extends ProjectionIndexer[T] {

  val offsetFilePath: String

  override def readOffset(): Option[TimeBasedUUID] = {
    val offset = Source.fromFile(offsetFilePath)
    offset match {
      case defined if offset.nonEmpty =>
        val offsetString = defined.getLines().mkString
        Try(TimeBasedUUID(UUID.fromString(offsetString))) match {
          case uuid if uuid.isSuccess => Some(uuid.get)
          case Failure(_)             => None
        }
      case _ => None
    }
  }

  def writeOffset(offset: TimeBasedUUID): Either[Exception, TimeBasedUUID] = {
    val offsetFw = new FileWriter(offsetFilePath, false)
    try {
      offsetFw.write(s"${offset.value}")
      Right(offset)
    } catch {
      case e: Exception => Left(e)
    } finally {
      offsetFw.close()
    }
  }
}
