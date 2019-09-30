package actor

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.sharding.ClusterSharding
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit, TestProbe}
import bank.AppConfig
import bank.actor.Messages
import bank.actor.write.BankAccountWriterActor
import bank.domain.BankAccount
import org.junit.runner.RunWith
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, Inside, Matchers}
import org.scalatestplus.junit.JUnitRunner
import scala.concurrent.duration._
@RunWith(classOf[JUnitRunner])
class BankAccountWriterActorSpec
    extends TestKit(ActorSystem(AppConfig.serviceName))
    with DefaultTimeout
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with Inside {

  private def initTestProductDetailsWriteActor(configuration: TestProbe): ActorRef =
    system.actorOf(Props(new BankAccountWriterActor))

  override def afterAll: Unit = {
    shutdown()
  }

  "A BankAccountWriterActor" must {

    """persist events on cassandra""" in {
      val testProbe = TestProbe()
      val writerActor: ActorRef = initTestProductDetailsWriteActor(testProbe)
      val initialAccount = BankAccount("abc", 0)

      testProbe.send(writerActor, BankAccountWriterActor.Initialize(initialAccount))

      testProbe.expectMsg(10 seconds, Messages.Done)
    }
  }
}
