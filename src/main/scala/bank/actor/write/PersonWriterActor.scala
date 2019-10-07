package bank.actor.write

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.event.LoggingReceive
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import bank.actor.Messages.Done
import bank.domain.Person
import bank.domain.Person.PersonCommand

import scala.concurrent.duration._

class PersonWriterActor extends Actor with ActorSharding with PersistentActor with ActorLogging {

  override implicit val system: ActorSystem = context.system

  override def persistenceId: String = s"${PersonWriterActor.name}-${self.path.name}"

  var state: Person = Person.empty

  context.setReceiveTimeout(120.seconds)
  private def receivePassivate: Receive = {
    case ReceiveTimeout        => context.parent ! ShardRegion.Passivate
    case ShardRegion.Passivate => context.stop(self)
  }

  override def receiveCommand: Receive = {
    case personOperation: Person.PersonCommand => {
      personOperation.applyTo(state) match {

        case Right(Some(event)) => {
          persist(event) { _ =>
            state = update(state, event)
            log.info(event.toString)
            if (lastSequenceNr % snapShotInterval == 0 && lastSequenceNr != 0) {
              saveSnapshot(state)
            }
            sender() ! Done
          }
        }

        case Right(None) => {
          sender() ! Done
        }
        case Left(error) => {
          sender() ! error
        }
      }
    }
  }

  protected def update(state: Person, event: Person.PersonEvent): Person = event.applyTo(state)

  val snapShotInterval = 10

  override def receiveRecover: Receive = receivePassivate orElse LoggingReceive {
    case RecoveryCompleted => log.info("Recovery completed!")
    case event: Person.PersonEvent =>
      state = update(state, event)
    case SnapshotOffer(_, snapshot: Person) => state = snapshot
    case unknown                                 => log.error(s"Received unknown message in receiveRecover:$unknown")
  }

}

object PersonWriterActor {

  val numberOfShards = 100

  val name = "person-writer-actor"

  val personDetailsTag: String = "person"

  def props(): Props = Props(new PersonWriterActor())

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case m: PersonCommand => (m.fullName, m)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    def computeShardId(entityId: ShardRegion.EntityId): ShardRegion.ShardId =
      (math.abs(entityId.hashCode()) % numberOfShards).toString

    {
      case m: PersonCommand       => computeShardId(m.fullName)
      case ShardRegion.StartEntity(id) => computeShardId(id)
    }
  }

}
