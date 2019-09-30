package bank.actor.projector

import akka.NotUsed
import akka.actor.{Actor, ActorSystem, DiagnosticActorLogging, Props}
import akka.event.LoggingReceive
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.{EventEnvelope, NoOffset, Offset, PersistenceQuery, TimeBasedUUID}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import bank.actor.ReadJournalStreamManagerActor
import bank.actor.projector.BankAccountEventProjectorActor.{OffsetRead, ReadOffset}
import bank.actor.write.{ActorSharding, BankAccountWriterActor}
import bank.actor.projector.EventEnvelopeSeqHandler.EventEnvelopeSeq
import bank.AppConfig._
import bank.actor.projector.export.BankAccountLogExporter
import bank.domain.BankAccount.BankAccountEvent

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class BankAccountEventProjectorActor(indexer: BankAccountLogExporter)
    extends ReadJournalStreamManagerActor[EventEnvelopeSeq]
    with EventEnvelopeSeqHandler[BankAccountEvent]
    with ActorSharding {

  // aprire stream in lettura sulla coda defli eventi persistiti su cassandra

  override implicit val system: ActorSystem = context.system

  override implicit val mat: ActorMaterializer = ActorMaterializer()

  implicit val blockingDispatcher: ExecutionContext =
    context.system.dispatchers.lookup(id = "akka-exercise-blocking-dispatcher")

  override def preStart(): Unit = context.system.scheduler.scheduleOnce(readDelay, self, ReadOffset)(system.dispatcher)

  override protected def createSource(
      readJournal: CassandraReadJournal,
      offset: Offset
  ): Source[EventEnvelopeSeq, NotUsed] =
    readJournal
      .eventsByTag(BankAccountWriterActor.bankAccountDetailsTag, offset)
      .groupedWithin(readBatchSize, readWindow)
      .map(EventEnvelopeSeq(_))

  override def receive: Receive = LoggingReceive {
    case ReadOffset =>
      indexer.readOffset() match {
        case None         => ()
        case Some(offset) => self ! offset
      }

    case OffsetRead(offset) => {
      context.become(eventStreamStarted(offset))
    }
  }

  def eventStreamStarted(offset: Offset, retries: Int = 0): Receive = LoggingReceive {
    ({
      case groupedEvents: EventEnvelopeSeq =>
        val offset2event: Seq[(TimeBasedUUID, BankAccountEvent)] = extractOffsetsAndEvents(groupedEvents)
        val eventStreamMaxOffset: TimeBasedUUID = getMaxOffset(offset2event)
        val events = offset2event map (_._2)
        val originalSender = sender()

        log.info("({}) Processing batch of {} events", indexer.name, events.size)
        indexer
          .indexEvents(events, eventStreamMaxOffset)
          .onComplete {
            case Success(_) =>
              log.info("({}) Indexing operation successfully completed! Offset: {}", indexer.name, eventStreamMaxOffset)
              context.become(eventStreamStarted(eventStreamMaxOffset))
            case Failure(exception) =>
              log.error(exception, "({}) Indexing operation failed", indexer.name)
          }(blockingDispatcher)
    }: Receive) orElse manageJournalStream(offset)
  }
}

object BankAccountEventProjectorActor {

  val name = "bank-account-event-projector-actor"

  def props(indexer: BankAccountLogExporter): Props =
    Props(new BankAccountEventProjectorActor(indexer))

  case object ReadOffset
  case class OffsetRead(offset: Offset)
}
