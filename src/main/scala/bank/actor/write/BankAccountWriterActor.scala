package bank.actor.write

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.cluster.sharding.ShardRegion
import akka.persistence.{PersistentActor, SnapshotOffer}
import bank.actor.Messages.Done
import bank.domain.BankAccount
import bank.domain.BankAccount.BankAccountCommand

import scala.concurrent.duration._

class BankAccountWriterActor() extends Actor with ActorSharding with PersistentActor with ActorLogging {

  override implicit val system: ActorSystem = context.system

  override def persistenceId: String = s"${BankAccountWriterActor.name}-${self.path.name}"

  var state: BankAccount = BankAccount.empty

  context.setReceiveTimeout(120.seconds)

  override def receiveCommand: Receive = {
    case bankOperation: BankAccount.BankAccountCommand => {
      bankOperation.applyTo(state) match {

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

  protected def update(state: BankAccount, event: BankAccount.BankAccountEvent): BankAccount = event.applyTo(state)

  val snapShotInterval = 10

  override val receiveRecover: Receive = {

    case event: BankAccount.BankAccountEvent => update(state, event)

    case SnapshotOffer(_, snapshot: BankAccount) => state = snapshot
  }

}

object BankAccountWriterActor {

  val numberOfShards = 100

  val name = "bank-account-writer-actor"

  val bankAccountDetailsTag: String = "bank-account-details"

  def props(): Props = Props(new BankAccountWriterActor())

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case m: BankAccountCommand => (m.iban, m)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    def computeShardId(entityId: ShardRegion.EntityId): ShardRegion.ShardId =
      (math.abs(entityId.hashCode()) % numberOfShards).toString

    {
      case m: BankAccountCommand       => computeShardId(m.iban.toString)
      case ShardRegion.StartEntity(id) => computeShardId(id)
    }
  }

}
