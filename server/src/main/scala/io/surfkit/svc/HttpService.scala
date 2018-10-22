package io.surfkit.svc

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.UUID

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import akka.http.scaladsl.server.Route
import org.squbs.unicomplex.RouteDefinition
import akka.actor._
import akka.util.{ByteString, Timeout}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl._
import io.surfkit.typebus.event.{SocketEvent, _}
import com.typesafe.config.ConfigFactory
import java.nio.file._

import headers._
import HttpMethods._
import com.sksamuel.avro4s._
import io.surfkit.actors.CoreActor
import io.surfkit.typebus.module.Service
import org.apache.avro.generic.GenericRecord

object ActorPaths {
  // actor path = /user/ + cube-shortname + / + actor name
  val coreActorPath = "/user/avro-websocket-example/core"
}

class CoreHttpService extends RouteDefinition with Service {
  implicit val system = context.system
  import system.dispatcher

  val decider: Supervision.Decider = {
    case _ => Supervision.Resume  // Never give up !
  }

  val config = ConfigFactory.load()

  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))


  implicit val timeout = Timeout(10 seconds)
  val coreActor = Await.result(system.actorSelection(ActorPaths.coreActorPath).resolveOne(), timeout.duration)


  val wwwPath = config.getString("www.base-url")
  // If wwwPath not found on fs, we force useResourceDir to true
  val useZipFs = config.getBoolean("www.use-zipfs") || Files.notExists(Paths.get(wwwPath))
  val useResourceDir = useZipFs && config.getBoolean("www.use-resource-dir")

  println("CoreHttp is up and running !")

  println(s"schema: \n${m.schema}")

  def route: Route =
      options {
        complete(HttpResponse(200).withHeaders(`Access-Control-Allow-Origin`.*, `Access-Control-Allow-Methods`(OPTIONS, POST, PUT, GET, DELETE)))
      } ~
      get {
        pathSingleSlash {
          getFromDirectory(s"${wwwPath}/index.html")
        }
      } ~
      path("bundle.js") {
        get {
          getFromDirectory(s"${wwwPath}/bundle.js")
        }
      } ~
      path("v1" / "ws" / JavaUUID) { token =>
        get{
          println("Handling WS connection")
          handleWebSocketMessages(websocketCoreFlow(sender = token.toString, token="" ))
        }
      }




  def coreInSink(userid: String, unique:UUID) = Sink.actorRef[CoreActor.ReceivedMessage](coreActor, CoreActor.Disconnect(userid,unique))

  def coreFlow(userid: String, token: String, unique:UUID ): Flow[ByteString, SocketEvent[m.Model], akka.NotUsed] = {
    val uid = UUID.fromString(userid)
    val in =
      Flow[ByteString]
        .map{x =>
          println(s"Get the message: ${x}")


          try{
            implicit object SocketEventFromRecord extends FromRecord[SocketEvent[m.Model]] {
              override def apply(record: GenericRecord): SocketEvent[m.Model] = {
                println("SocketEventFromRecord *****************")
                println(s"record: ${record}")
                println("")
                null
              }
            }
            /*implicit object PizzaFromRecord extends FromRecord[m.Pizza] {
              override def apply(record: GenericRecord): m.Pizza = {
                println("PizzaFromRecord *****************")
                println(s"record: ${record}")
                println("")
                null
              }
            }*/

            val schemax = AvroSchema[m.SEvent]
            implicit val formatx = RecordFormat[m.SEvent]
            val inx = new ByteArrayInputStream(x.toArray)
            println(s"inx: ${inx}")
            val inputx = AvroInputStream.binary[m.SEvent](inx)
            println(s"inputx: ${inputx}")
            println(s"inputx: ${inputx.iterator}")
            val sevent = inputx.iterator.toSeq.head
            println(s"input.iterator.toSeq: ${sevent}")


            CoreActor.ReceivedMessage(userid, unique, SocketEvent[m.Model](correlationId = sevent.correlationId, occurredAt = org.joda.time.DateTime.now, payload = sevent.payload) )
            /*


            val schema = AvroSchema[SocketEvent[m.Model]]
            implicit val format = RecordFormat[SocketEvent[m.Model]]
            val in = new ByteArrayInputStream(x.toArray)
            val input = AvroInputStream.binary[SocketEvent[m.Model]](in)
            val result = input.iterator.toSeq
            val event = result.head
            //logger.info(s"obj: ${obj}")
            CoreActor.ReceivedMessage(userid, unique, event)*/
          }catch{
            case t: Throwable =>
              t.printStackTrace()
              CoreActor.ReceivedMessage(userid, unique,SocketEvent(
                correlationId = UUID.randomUUID().toString,
                occurredAt = new org.joda.time.DateTime(),
                payload =  null//m.Error(t.getMessage)
              ))
          }

        }
        .to(coreInSink(userid, unique))
    // The counter-part which is a source that will create a target ActorRef per
    // materialization where the coreActor will send its messages to.
    // This source will only buffer n element and will fail if the client doesn't read
    // messages fast enough.
    val out = Source.actorRef[SocketEvent[m.Model]](32, OverflowStrategy.fail)
      .mapMaterializedValue(coreActor ! CoreActor.Connect(userid, unique, _, token))
    Flow.fromSinkAndSource(in, out)//(Keep.none)
  }


  def websocketCoreFlow(sender: String, token: String): Flow[Message, Message, akka.NotUsed] = {
    Flow[Message]
      .collect {
        case TextMessage.Strict(msg) ⇒
          println(s"WebSocket got text: ${msg} ??")
          Future.successful(ByteString.empty)
        case TextMessage.Streamed(stream) =>
          stream.runWith(Sink.ignore)
          Future.successful(ByteString.empty)
        case BinaryMessage.Strict(msg) =>
          println(s"WebSocket Got binary")
          //other.dataStream.runWith(Sink.ignore)
          Future.successful(msg)
        /*case BinaryMessage.Streamed(stream) =>
          stream
            .limit(100) // Max frames we are willing to wait for
            .completionTimeout(5 seconds) // Max time until last frame
            .runFold(ByteString.empty)(_ + _) // Merges the frames
            .flatMap(msg => Future.successful(msg))*/
      }.mapAsync(parallelism = 3)(identity)
      .via(coreFlow(sender, token, UUID.randomUUID())) // ... and route them through the chatFlow ...
      // client now sends this..
      /*.keepAlive(45 seconds, () =>
        SocketEvent(
          correlationId = UUID.randomUUID().toString,
          occurredAt = new org.joda.time.DateTime(),
          payload = m.KeepAlive
        )
      )*/
      .map {
        case x: SocketEvent[_] => {
          //logger.info(s"DOWN THE SOCKET: ${x.payload.getClass.getName}")
          try {
            //BinaryMessage.Strict(ByteString(pickler.writeValue(x)) )
            // FIX:ME
            val baos = new ByteArrayOutputStream()
            val output = AvroOutputStream.binary[SocketEvent[m.Model]](baos)
            output.write(x)
            output.close()
            BinaryMessage.Strict(ByteString(baos.toByteArray))
          } catch {
            case t: Throwable =>
              println(s"ERROR: trying to unpickle type: : ${x}", t)
              throw t
          }
        }
    }
  }

}

