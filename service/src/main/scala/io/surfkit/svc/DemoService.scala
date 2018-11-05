package io.surfkit.svc

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.UUID

import io.surfkit.typebus._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import io.surfkit.typebus.event.{PublishedEvent, EventMeta}
import io.surfkit.typebus.actors.ProducerActor
import akka.actor._
import io.surfkit.typebus.event.{SocketEvent, _}
import org.apache.kafka.clients.consumer.ConsumerConfig
import akka.kafka.ConsumerSettings
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}
import com.typesafe.config.ConfigFactory
import com.sksamuel.avro4s._
import io.surfkit.typebus.module.Service
import org.apache.avro.generic.GenericRecord

class DemoService extends Actor with ActorLogging with Service[m.Model] {
  implicit val system = context.system
  import system.dispatcher

  val cfg = ConfigFactory.load

  val kafka = cfg.getString("bus.kafka")

  val consumerSettings = ConsumerSettings(system, new ByteArrayDeserializer, new ByteArrayDeserializer)
    .withBootstrapServers(kafka)
    .withGroupId("demo")
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")


  implicit val PizzaByteStreamReader = new ByteStreamReader[m.Pizza]{
    val schema = AvroSchema[m.Pizza]

    override def read(bytes: Array[Byte]): m.Pizza = {
      println("PizzaByteStreamReader")
      val input = AvroInputStream.binary[m.Pizza].from(bytes).build(schema)
      println(s"PizzaByteStreamReader input: ${input}")
      val result = input.iterator.toSeq
      println(s"PizzaByteStreamReader: ${result}")
      result.head
    }
  }

  implicit val PizzaByteStreamWriter = new ByteStreamWriter[m.Pizza]{
    val schema = AvroSchema[m.Pizza]
    override def write(value: m.Pizza): Array[Byte] = {
      println("PizzaByteStreamWriter")
      val baos = new ByteArrayOutputStream()
      val output = AvroOutputStream.binary[m.Pizza].to(baos).build(schema)
      output.write(value)
      output.close()
      baos.toByteArray
    }
  }

  def makePizza(x: m.Pizza, meta: EventMeta): Future[m.Pizza] = {
    println("WE GOT A PIZZA HERE sports fans !!!!")
    println(x)
    println(s"meta: ${meta}")
    Future.successful(x)
  }

  println("Running !!!!")

  //perform(funToPF(makePizza))
  registerStream(makePizza _)


  import collection.JavaConversions._
  val producer = new KafkaProducer[Array[Byte], Array[Byte]](Map(
    "bootstrap.servers" -> kafka,
    "key.serializer" ->  "org.apache.kafka.common.serialization.ByteArraySerializer",
    "value.serializer" -> "org.apache.kafka.common.serialization.ByteArraySerializer"
  ))

  val bus = system.actorOf(ProducerActor.props(producer))

  startService(consumerSettings, bus)

  def receive = {
    // CA - USED FOR TESTING ONLY...
    case _ => // Note used for this kind of service...
  }
}

