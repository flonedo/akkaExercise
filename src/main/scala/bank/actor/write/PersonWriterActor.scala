package bank.actor.write

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.cluster.sharding.ShardRegion
import akka.persistence.{PersistentActor, SnapshotOffer}
import bank.actor.Messages.Done
import bank.domain.Person
import bank.domain.Person.PersonCommand

import scala.concurrent.duration._

class PersonWriterActor() extends Actor with ActorSharding with PersistentActor with ActorLogging {

  override implicit val system: ActorSystem = context.system

  override def persistenceId: String = s"${PersonWriterActor.name}-${self.path.name}"

  context.setReceiveTimeout(120.seconds)

  var state: Person = Person.empty

  override def receiveCommand: Receive = {
    case personOperation: Person.PersonCommand => {
      personOperation.applyTo(state) match {

        case Right(Some(event: Person.PersonEvent)) => {
          persist(event) { _ =>
            state = update(state, event)
            log.info(event.toString)
            if (lastSequenceNr % snapShotInterval == 0 && lastSequenceNr != 0) {
              saveSnapshot(state)
              sender() ! Done
            }
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

  override val receiveRecover: Receive = {

    case event: Person.PersonEvent => update(state, event)

    case SnapshotOffer(_, snapshot: Person) => state = snapshot
  }
}

object PersonWriterActor {

  val numberOfShards = 100

  val name = "person-writer-actor"

  def props(): Props = Props(new PersonWriterActor())

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case m: PersonCommand => (m.fullName, m)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    def computeShardId(entityId: ShardRegion.EntityId): ShardRegion.ShardId =
      (math.abs(entityId.hashCode()) % numberOfShards).toString

    {
      case m: PersonCommand            => computeShardId(m.fullName)
      case ShardRegion.StartEntity(id) => computeShardId(id)
    }
  }

}
