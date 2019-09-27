package bank.actor

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}
import akka.cluster.sharding.ShardRegion
import akka.persistence.{PersistentActor, SnapshotOffer}
import bank.domain.Person.PersonCommand
import bank.domain.{BankAccount, Person}

import scala.concurrent.duration._

class PersonWriterActor() extends Actor with ActorSharding with PersistentActor with ActorLogging {

  override implicit val system: ActorSystem = context.system

  override def persistenceId: String = s"${PersonWriterActor.name}-${self.path.name}"

  context.setReceiveTimeout(120.seconds)

  var state: Person = Person.empty

  override def receiveCommand: Receive = {
    case personOperation: Person.PersonCommand => {
      personOperation.applyTo(state) match {

        case Right(Some(event: Person.OpenedBankAccount)) => {
          persist(event) { _ =>
            state = update(state, event)
            log.info(event.toString)
            system.actorOf(
              BankAccountWriterActor.props(BankAccount(state.bankAccounts.last.iban, state.bankAccounts.last.balance))
            )
            if (lastSequenceNr % snapShotInterval == 0 && lastSequenceNr != 0) {
              saveSnapshot(state)
              sender() ! "Done"
            }
          }
        }

        case Right(Some(event: Person.ClosedBankAccount)) => {
          persist(event) { _ =>
            state = update(state, event)
            log.info(event.toString)
            if (lastSequenceNr % snapShotInterval == 0 && lastSequenceNr != 0) {
              saveSnapshot(state)
              sender() ! "Done"
            }
          }
        }

        case Right(None) => {
          sender() ! "Done"
        }

        case Left(_) => {
          sender() ! _
        }
      }
    }
  }

  protected def update(person: Person, event: Person.PersonEvent): Person = event.applyTo(person)

  val snapShotInterval = 10

  override val receiveRecover: Receive = {

    case event: Person.PersonEvent => update(state, event)

    case SnapshotOffer(_, snapshot: Person) => state = snapshot
  }
}

object PersonWriterActor {

  val numberOfShards = 100

  val name = "person-writer-actor"

  def props(person: Person): Props = Props(new PersonWriterActor(person))

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