package bank.actor

import akka.NotUsed
import akka.actor.{DiagnosticActorLogging, Status}
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.{Offset, PersistenceQuery}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import bank.actor.projector.{AckMessage, CompleteMessage, InitMessage, RestartStream}

import scala.concurrent.duration._

trait ReadJournalStreamManagerActor[T] extends DiagnosticActorLogging {

  implicit val mat: ActorMaterializer = ActorMaterializer()

  /**
    * Defines the set of stream processing steps to reading events on Cassandra journal, resumed
    * from a given offset
    *
    * @param readJournal Cassandra journal
    * @param offset ordered unique identifier of the events, the `offset` is exclusive and it is
    *               used to resume the stream from a given offset
    * @return the set of stream processing steps that has one open output, the materialization
    *         turns a Source into a Reactive Streams
    */
  protected def createSource(readJournal: CassandraReadJournal, offset: Offset): Source[T, NotUsed]

  /**
    * Resumes the stream from a given offset and materializes the Source
    *
    * @param offset ordered unique identifier of the events, the `offset` is exclusive and it is
    *               used to resume the stream from a given offset
    */
  protected def startStream(offset: Offset): Unit = {
    log.info(s"Consumer start stream on Cassandra journal: ${self.path.name}")
    // obtain read journal by plugin id
    val readJournal: CassandraReadJournal =
      PersistenceQuery(context.system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
    // issue query to journal
    val source = createSource(readJournal, offset)
    // create stream
    source.to(Sink.actorRefWithAck(self, InitMessage, AckMessage, CompleteMessage)).run()
    log.info(s"Journal reader started on Cassandra journal - ${self.path.name}")
  }

  /**
    * Internal utility method to manage stream control messages (onInitMessage, onCompleteMessage, ackMessage)
    */
  protected def manageJournalStream(offset: Offset): Receive = {
    case RestartStream =>
      startStream(offset)
    case InitMessage =>
      sender ! AckMessage
      log.info(s"Consumer initialize stream on Cassandra journal - ${self.path.name}")
    case Status.Failure(cause: Throwable) =>
      context.system.scheduler.scheduleOnce(30.seconds, self, RestartStream)(context.system.dispatcher)
      log.error(cause, s"Consumer on Cassandra journal received failure message!")
    case CompleteMessage =>
      log.warning(s"Consumer on Cassandra journal received complete message!")
      context.system.scheduler.scheduleOnce(30.seconds, self, RestartStream)(context.system.dispatcher)
    case AckMessage =>
      log.warning(s"Consumer on Cassandra journal received AckMessage message while in consuming: message ignored!")
    case other =>
      log.error(s"Consumer on Cassandra journal received unknown message: $other")
  }

  /** Complete the stream */
  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.error(reason, s"Actor restarted due to unhandled exception when processing $message")
    super.preRestart(reason, message)
  }

}
