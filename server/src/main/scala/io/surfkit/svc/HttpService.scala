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
import m._
import com.typesafe.config.ConfigFactory
import java.nio.file._

import headers._
import HttpMethods._
import com.sksamuel.avro4s._
import io.surfkit.actors.CoreActor
import io.surfkit.typebus.module.Service
import io.surfkit.typebus.event._
import org.apache.avro.generic.GenericRecord

object ActorPaths {
  // actor path = /user/ + cube-shortname + / + actor name
  val coreActorPath = "/user/avro-websocket-example/core"
}

class HttpService extends RouteDefinition {
  implicit val system = context.system
  import system.dispatcher

  val decider: Supervision.Decider = {
    case _ => Supervision.Resume  // Never give up !
  }

  val schema = AvroSchema[SocketEvent]
  println(s"schema: \n${schema}")

  val config = ConfigFactory.load()

  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))

  implicit val timeout = Timeout(10 seconds)
  val coreActor = Await.result(system.actorSelection(ActorPaths.coreActorPath).resolveOne(), timeout.duration)

  val wwwPath = config.getString("www.base-url")
  // If wwwPath not found on fs, we force useResourceDir to true
  val useZipFs = config.getBoolean("www.use-zipfs") || Files.notExists(Paths.get(wwwPath))
  val useResourceDir = useZipFs && config.getBoolean("www.use-resource-dir")

  println("CoreHttp is up and running !")

  //println(s"schema: \n${schema}")

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

  def coreFlow(userid: String, token: String, unique:UUID ): Flow[ByteString, SocketEvent, akka.NotUsed] = {
    val uid = UUID.fromString(userid)
    val in =
      Flow[ByteString]
        .map{x =>
          println(s"Get the message: ${x}")
          try{
            val schema = AvroSchema[SocketEvent]
            val input = AvroInputStream.binary[SocketEvent].from(x.toArray).build(schema)
            println(s"coreFlow input: ${input}")
            println(s"coreFlow inputx: ${input.iterator}")
            val result = input.iterator.toSeq
            val socketEvent = result.head
            println(s"coreFlow Event Type: ${socketEvent.meta.eventType}")

            CoreActor.ReceivedMessage(userid, unique, socketEvent)
          }catch{
            case t: Throwable =>
              t.printStackTrace()
              throw t
              /*CoreActor.ReceivedMessage(userid, unique,SocketEvent(
                correlationId = UUID.randomUUID().toString,
                occurredAt = new org.joda.time.DateTime(),
                payload =  null//m.Error(t.getMessage)
              ))*/
          }

        }
        .to(coreInSink(userid, unique))
    // The counter-part which is a source that will create a target ActorRef per
    // materialization where the coreActor will send its messages to.
    // This source will only buffer n element and will fail if the client doesn't read
    // messages fast enough.
    val out = Source.actorRef[SocketEvent](32, OverflowStrategy.fail)
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
        case BinaryMessage.Streamed(stream) =>
          stream
            .limit(100) // Max frames we are willing to wait for
            .completionTimeout(5 seconds) // Max time until last frame
            .runFold(ByteString.empty)(_ ++ _) // Merges the frames
            .flatMap(msg => Future.successful(msg))
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
        case x: SocketEvent => {
          //logger.info(s"DOWN THE SOCKET: ${x.payload.getClass.getName}")
          try {
            val schema = AvroSchema[SocketEvent]
            val baos = new ByteArrayOutputStream()
            val output = AvroOutputStream.binary[SocketEvent].to(baos).build(schema)
            output.write(x)
            output.close()
            println(s"payload bytes: ${baos.toByteArray}")
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

