package bank.actor.projector

import akka.persistence.query.{EventEnvelope, TimeBasedUUID}
import bank.actor.projector.EventEnvelopeSeqHandler.EventEnvelopeSeq

trait EventEnvelopeSeqHandler[T] {

  def extractOffsetsAndEvents(groupedEvents: EventEnvelopeSeq): Seq[(TimeBasedUUID, T)] = groupedEvents.seq map {
    ee: EventEnvelope =>
      ee.offset.asInstanceOf[TimeBasedUUID] -> ee.event.asInstanceOf[T]
  }

  def getMaxOffset(offsetsAndEvents: Seq[(TimeBasedUUID, T)]): TimeBasedUUID = offsetsAndEvents.maxBy(_._1)._1
}

object EventEnvelopeSeqHandler {

  /**
    * Class that collects a sequence of events read in batch mode from the persistence stream
    *
    * @param seq sequence of events read from the persistence stream
    * @see [[akka.persistence.query.EventEnvelope]]
    */
  case class EventEnvelopeSeq(seq: Seq[EventEnvelope])

}
