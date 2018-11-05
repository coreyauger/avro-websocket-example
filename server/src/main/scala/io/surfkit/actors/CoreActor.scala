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
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.producer.KafkaProducer
import io.surfkit.typebus.actors.ProducerActor
import io.surfkit.typebus.event._

object CoreActor {

  sealed trait Socket
  case class Connect(uuid: String, socketId:UUID, subscriber: ActorRef, token: String) extends Socket
  case class Disconnect(uuid: String, socketId:UUID) extends Socket
  case class ReceivedMessage(uuid: String, socketId:UUID, data: SocketEvent) extends Socket {
    def toPublishedEvent:PublishedEvent = PublishedEvent(
      meta = data.meta,
      payload = data.payload)

  }
}

class CoreActor extends Actor with ActorLogging {
  implicit val system = context.system
  var subscriberToUserId = Map.empty[ActorRef, String]
  var socketIdToSubscriber = Map.empty[UUID, ActorRef]  // HACK: way to handle Disconnect faster then "actor.watch"
  import CoreActor._


  val kafka = ConfigFactory.load.getString("bus.kafka")
  import collection.JavaConversions._
  val producer = new KafkaProducer[Array[Byte], Array[Byte]](Map(
    "bootstrap.servers" -> kafka,
    "key.serializer" ->  "org.apache.kafka.common.serialization.ByteArraySerializer",
    "value.serializer" -> "org.apache.kafka.common.serialization.ByteArraySerializer"
  ))


  val bus = system.actorOf(ProducerActor.props(producer))

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
      println(s"TO PUBLISH EVENT: ${produce}")
      try {
        bus ! produce
      }catch{
        case t: Throwable =>
          println(s"ERROR: ${t}")
          t.printStackTrace()
      }
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
