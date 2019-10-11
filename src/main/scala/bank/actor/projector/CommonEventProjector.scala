package bank.actor.projector

import akka.NotUsed
import akka.event.LoggingReceive
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.{NoOffset, Offset, TimeBasedUUID}
import akka.stream.scaladsl.Source
import bank.AppConfig.{readBatchSize, readDelay, readWindow}
import bank.actor.ReadJournalStreamManagerActor
import bank.actor.projector.EventEnvelopeSeqHandler.EventEnvelopeSeq
import bank.actor.projector.export.CommonLogExporter
import bank.actor.write.ActorSharding

case object ReadOffset

trait CommonEventProjector[T]
    extends ReadJournalStreamManagerActor[EventEnvelopeSeq]
    with EventEnvelopeSeqHandler[T]
    with ActorSharding {

  val indexerInside: CommonLogExporter[T]
  val detailsTag: String

  override def preStart(): Unit = {
    context.system.scheduler.scheduleOnce(readDelay, self, ReadOffset)(system.dispatcher)
  }

  override protected def createSource(
      readJournal: CassandraReadJournal,
      offset: Offset
  ): Source[EventEnvelopeSeq, NotUsed] = {
    readJournal.eventsByTag(detailsTag, offset).groupedWithin(readBatchSize, readWindow).map(EventEnvelopeSeq(_))
  }

  override def receive: Receive = LoggingReceive {
    case ReadOffset =>
      val x = indexerInside.readOffset() match {
        case None         => NoOffset
        case Some(offset) => offset
      }
      context.become(eventStreamStarted(x))
      startStream(x)
      log.info("({}) Stream started! Offset: {}", indexerInside.name, x)

    case unknown =>
      log.error("({}) Received unknown message in receiveCommand (sender: {} - message: {})",
                indexerInside.name,
                sender,
                unknown)
  }

  def eventStreamStarted(offset: Offset): Receive = LoggingReceive {
    ({
      case groupedEvents: EventEnvelopeSeq =>
        val offset2event: Seq[(TimeBasedUUID, T)] = extractOffsetsAndEvents(groupedEvents)
        val eventStreamMaxOffset: TimeBasedUUID = getMaxOffset(offset2event)
        val events = offset2event map (_._2)
        val originalSender = sender()

        log.info("({}) Processing batch of {} events", indexerInside.name, events.size)
        indexerInside.indexEvents(events, eventStreamMaxOffset) match {
          case Right(_) =>
            log.info("({}) Indexing operation successfully completed! Offset: {}",
                     indexerInside.name,
                     eventStreamMaxOffset)
            context.become(eventStreamStarted(eventStreamMaxOffset))
            originalSender ! AckMessage

          case Left(exception) =>
            log.error(exception, "({}) Indexing operation failed", indexerInside.name)
            throw exception // effect: restart actor
        }
    }: Receive) orElse manageJournalStream(offset)
  }
}
