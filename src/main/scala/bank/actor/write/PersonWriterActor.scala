package bank.actor.write

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.util.Timeout
import bank.AppConfig
import bank.actor.Messages.{AccountAlreadyCreated, Done}
import bank.domain.Person.{OpenedBankAccount, PersonCommand}
import bank.domain.{BankAccount, Person}

import scala.concurrent.Await
import scala.concurrent.duration._

class PersonWriterActor extends Actor with ActorSharding with PersistentActor with ActorLogging {

  override implicit val system: ActorSystem = context.system
  private implicit val timeout: Timeout = Timeout(AppConfig.askTimeout)

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
          event match {
            case OpenedBankAccount(_, iban) => {
              val future = accountRegion ? BankAccount.Create(iban)
              val result = Await.result(future, timeout.duration)
              result match {
                case Done                  => persistEvent(event)
                case AccountAlreadyCreated => sender ! Done
                case error                 => sender ! error
              }
            }
            case _ => persistEvent(event)
          }
        }
        case Right(None) => sender() ! Done
        case Left(error) => sender() ! error
      }
    }
  }

  private def persistEvent(event: Person.PersonEvent): Unit = {
    persist(event) { _ =>
      state = update(state, event)
      log.info(event.toString)
      if (lastSequenceNr % snapShotInterval == 0 && lastSequenceNr != 0) {
        saveSnapshot(state)
      }
      sender ! Done
    }
  }

  protected def update(state: Person, event: Person.PersonEvent): Person = event.applyTo(state)

  val snapShotInterval = 10

  override def receiveRecover: Receive = receivePassivate orElse LoggingReceive {
    case RecoveryCompleted => log.info("Recovery completed!")
    case event: Person.PersonEvent =>
      state = update(state, event)
    case SnapshotOffer(_, snapshot: Person) => state = snapshot
    case unknown                            => log.error(s"Received unknown message in receiveRecover:$unknown")
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
      case m: PersonCommand            => computeShardId(m.fullName)
      case ShardRegion.StartEntity(id) => computeShardId(id)
    }
  }

}
