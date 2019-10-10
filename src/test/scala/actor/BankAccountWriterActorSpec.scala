package actor

import akka.actor.ActorSystem
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import bank.AppConfig
import bank.actor.Messages.{AccountAlreadyCreated, Done}
import bank.actor.write.BankAccountWriterActor
import bank.domain.BankAccount
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, Inside, Matchers}

import scala.concurrent.duration._

class BankAccountWriterActorSpec
    extends TestKit(ActorSystem(AppConfig.serviceName))
    with DefaultTimeout
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with Inside {

  val cluster = ClusterSharding(system).start(
    typeName = BankAccountWriterActor.name,
    entityProps = BankAccountWriterActor.props(),
    settings = ClusterShardingSettings(system),
    extractEntityId = BankAccountWriterActor.extractEntityId,
    extractShardId = BankAccountWriterActor.extractShardId
  )

  override def afterAll: Unit = {
    shutdown()
  }

  "A BankAccountWriterActor" must {
    "perform commands" in {
      cluster ! BankAccount.Create("iban1")
      expectMsg(10 seconds, Done)

      cluster ! BankAccount.Deposit("iban1", 2)
      expectMsg(10 seconds, Done)

      cluster ! BankAccount.Withdraw("iban1", 1)
      expectMsg(10 seconds, Done)
    }
  }

  "A deposit command" must {
    "be executed by the correct account" in {
      cluster ! BankAccount.Deposit("inexistent-iban", 2)
      expectMsg(10 seconds, "Wrong IBAN")
    }
  }

  "A withdraw command" must {
    "be refused if the funds are insufficient" in {
      cluster ! BankAccount.Create("iban2")
      expectMsg(10 seconds, Done)

      cluster ! BankAccount.Withdraw("iban2", 2)
      expectMsg(10 seconds, "Insufficient funds")
    }
  }

  "A create command" must {
    "report if the bank account already exists" in {
      cluster ! BankAccount.Create("iban3")
      expectMsg(10 seconds, Done)

      cluster ! BankAccount.Create("iban3")
      expectMsg(10 seconds, AccountAlreadyCreated)
    }
  }

}
