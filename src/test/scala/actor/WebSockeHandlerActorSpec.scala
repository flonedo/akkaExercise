package actor

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.dispatch.MessageDispatcher
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.testkit.WSProbe
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, StreamRefs}
import akka.stream.{ActorMaterializer, OverflowStrategy, SinkRef}
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import bank.AppConfig
import bank.actor.Messages.Done
import bank.actor.{Notify, WebsocketHandlerActor}
import bank.domain.WebsocketConnection.OpenConnection
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, Inside, Matchers}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success

class WebSockeHandlerActorSpec
    extends TestKit(ActorSystem(AppConfig.serviceName))
    with DefaultTimeout
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with Inside {

  implicit val materializer = ActorMaterializer()

  val cluster = ClusterSharding(system).start(
    typeName = WebsocketHandlerActor.name,
    entityProps = WebsocketHandlerActor.props(),
    settings = ClusterShardingSettings(system),
    extractEntityId = WebsocketHandlerActor.extractEntityId,
    extractShardId = WebsocketHandlerActor.extractShardId
  )

  //private implicit val scheduler: Scheduler = system.scheduler
  private implicit val blockingDispatcher: MessageDispatcher =
    system.dispatchers.lookup(id = "akka-exercise-blocking-dispatcher")

  override def afterAll: Unit = {
    shutdown()
  }

  /*"A WebSocketHandlerActor" must {
    "perform commands" in {
      within(10.seconds) {
        val wsClient = WSProbe()
        val connectionId = "someId"
        val bufferSize = 1000

        val outStream: Source[Message, ActorRef] = Source.actorRef[Message](bufferSize, OverflowStrategy.fail)

        val sink: Sink[Message, NotUsed] = Flow[Message].to(Sink.asPublisher(false))

        val flow = Flow.fromSinkAndSourceCoupledMat(sink, outStream)(Keep.both)

        val ref = StreamRefs.sinkRef[Message]().to(sink).run()

        ref.onComplete {
          case Success(value) => cluster ! OpenConnection(value, connectionId)
          case _              => ()
        }

        //cluster ! OpenConnection(, connectionId)

        expectMsg(Done)

        cluster ! Notify(connectionId, "somemessage")

        wsClient.expectMessage("somemessage")
      }
    }
  }*/
}
