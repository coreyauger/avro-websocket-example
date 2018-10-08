package io.surfkit.actors

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import akka.actor._
import java.util.UUID

import io.surfkit.typebus.event._
import org.joda.time.DateTime
import akka.util.Timeout

import scala.concurrent.duration._
import com.sksamuel.avro4s._
import io.surfkit.typebus.{ByteStreamReader, ByteStreamWriter}
import m._

object CoreActor {

  sealed trait Socket
  case class Connect(uuid: String, socketId:UUID, subscriber: ActorRef, token: String) extends Socket
  case class Disconnect(uuid: String, socketId:UUID) extends Socket
  case class ReceivedMessage(uuid: String, socketId:UUID, data: SocketEvent[m.Model]) extends Socket {
    def toPublishedEvent:PublishedEvent[m.Model] = PublishedEvent(
      eventId = UUID.randomUUID().toString,
      eventType = data.payload.getClass.getCanonicalName,
      source = "",
      userIdentifier = Some(uuid.toString),
      socketId = Some(socketId.toString),
      correlationId = Some(data.correlationId.toString),
      occurredAt = new DateTime,
      publishedAt = new DateTime,
      payload = data.payload)

  }

  import m.format


  implicit val typeBusIngrediantWriter = new ByteStreamWriter[PublishedEvent[Ingredient]]{
    override def write(value: PublishedEvent[Ingredient]): Array[Byte] = {
      val baos = new ByteArrayOutputStream()
      val output = AvroOutputStream.binary[PublishedEvent[Ingredient]](baos)
      output.write(value)
      output.close()
      baos.toByteArray
    }
  }


  implicit val typeBusPizaWriter = new ByteStreamWriter[PublishedEvent[Pizza]]{
    override def write(value: PublishedEvent[Pizza]): Array[Byte] = {
      val baos = new ByteArrayOutputStream()
      val output = AvroOutputStream.binary[PublishedEvent[Pizza]](baos)
      output.write(value)
      output.close()
      baos.toByteArray
    }
  }

  /*


  object avroMapper extends io.surfkit.typebus.Mapper {
    def writeValue[T : SchemaFor : ToRecord ](value: T): Array[Byte] = {
      val baos = new ByteArrayOutputStream()
      val output = AvroOutputStream.binary[T](baos)
      output.write(value)
      output.close()
      baos.toByteArray
    }

    def readValue[T : SchemaFor : FromRecord](content: Array[Byte]): T = {
      //mapper.readValue(content, scala.reflect.classTag[T].runtimeClass.asInstanceOf[Class[T]])
      val in = new ByteArrayInputStream(content)
      val input = AvroInputStream.binary[T](in)
      val result = input.iterator.toSeq
      result.head
    }
  }*/
}

class CoreActor extends Actor with ActorLogging {
  implicit val system = context.system
  var subscriberToUserId = Map.empty[ActorRef, String]
  var socketIdToSubscriber = Map.empty[UUID, ActorRef]  // HACK: way to handle Disconnect faster then "actor.watch"
  import CoreActor._

  implicit val timeout = Timeout(10 seconds)

  def receive: Receive = {
    case Connect(userid, socketId, subscriber, token) =>
      context.watch(subscriber)
      subscriberToUserId += subscriber -> userid
      socketIdToSubscriber += socketId -> subscriber
      //userRegion ! UserActor.ShardMessage(UUID.fromString(userid), UserActor.Connect(socketId, subscriber))
      log.info(s"$userid joined!")

    case msg: ReceivedMessage =>
      //println(s"@@@@ MSG: ${msg}")
      // CA - record stats
      val produce = msg.toPublishedEvent
      //ClusterStats.inc(ClusterStats.StatCategory.Socket, coreMessage.data.getClass.getName)
      //if(!produce.payload.isInstanceOf[m.Hb]) { // don't log heartbeat..
      //  log.info(s"ReceivedMessage for user(${msg.uuid}):  ${produce}")
      //}
      //userRegion ! UserActor.ShardMessage(UUID.fromString(msg.uuid), produce)

    case Disconnect(uuid, socketId) =>
      log.info(s"user($uuid) socketId(${socketId}) left!")
      for {
        s <- socketIdToSubscriber.get(socketId)
      } yield{
        //ClusterStats.inc(ClusterStats.StatCategory.Socket, m.Socket.Closed("").getClass.getName)    // record connect to stats
        log.info(s"Sending disconnect to user actor($uuid)")
        //userRegion ! UserActor.ShardMessage(UUID.fromString(uuid), UserActor.DisConnect(socketId, s))
        subscriberToUserId -= s
        socketIdToSubscriber -= socketId
      }
    case Terminated(sub) =>
      log.info("Terminated")
      subscriberToUserId.get(sub).foreach{ uuid =>
        socketIdToSubscriber.find{ case (id, s) => s == sub }.foreach{ case (socketId, ref) =>
          log.info(s"Terminated :: Sending disconnect to user actor($uuid)")
          //userRegion ! UserActor.ShardMessage(UUID.fromString(uuid), UserActor.DisConnect(socketId, sub))
        }
        subscriberToUserId -= sub
        socketIdToSubscriber = socketIdToSubscriber.filterNot(_._2 == sub)
      }
      subscriberToUserId -= sub // clean up dead subscribers
  }
}
