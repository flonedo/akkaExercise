package bank.actor.projector

import akka.persistence.query.TimeBasedUUID

import scala.concurrent.Future

trait ProjectionIndexer[T] {

  /**
    * Name of the projection, used for logging purposes
    */
  val name: String

  /**
    * Tag used by akka-persistence to persist the relevant domain events. In other words, defines the stream of domain
    * events that will be used to produce the projection.
    */
  val eventsTag: String

  /**
    * Obtain the offset of the last projected domain event (if any).
    */
  def readOffset(): Option[TimeBasedUUID]

  /**
    * Index a batch of domain events, and persist the given offset (so that the projection will restart from the correct
    * offset even after a restart).
    *
    * @param events Batch of events
    * @param offset Offset of the last event of the batch
    */
  def indexEvents(events: Seq[T], offset: TimeBasedUUID): Either[Exception, TimeBasedUUID]
}
