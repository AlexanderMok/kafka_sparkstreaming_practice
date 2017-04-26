package com.shatacloud.bandstream


import java.util.Properties

import com.alibaba.fastjson.{JSON, JSONArray, JSONObject}
//import kafka.producer.{KeyedMessage, Producer, ProducerConfig}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

import scala.util.Random

/**
  * Created by Alex on 2017/4/25.
  */
object KafkaLogProducer {
  private val uuid = Array("b78c8cd39f1b324ff1eb682bb839aefe", "0247c503b8c06bd0543e4771ecd0d543", "921d94bfcd0636c0939eeb9def062dbb")
  private val random = new Random()
  private var pointer = -1

  def main(args: Array[String]): Unit = {
    val topic = "test_data"
    val brokers = "172.30.40.103:9092"
    val props = new Properties()
    props.put("bootstrap.servers", brokers)
    //props.put("metadata.broker.list", brokers)
    //props.put("serializer.class", "kafka.serializer.StringEncoder")
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("compression.codec", "2")
    //val kafkaConfig = new ProducerConfig(props)
    //val producer = new Producer[String, String](kafkaConfig)
    val producer: KafkaProducer[String, String] = new KafkaProducer(props)
    while (true) {
      val event = new JSONObject()
      event.put("uuid", UUID())
      event.put("sp", sp())
      event.put("bandwidth", bandwidth())
      event.put("location", location())
      println("Message sent --> " + event.toJSONString)
      //producer.send(new KeyedMessage[String, String](topic, event.toJSONString))
      producer.send(new ProducerRecord[String, String](topic, event.toJSONString))

      event.put("uuid", UUID())
      event.put("sp", sp())
      event.put("bandwidth", bandwidth())
      event.put("location", location())
      println("Message sent --> " + event.toJSONString)
      //producer.send(new KeyedMessage[String, String](topic, event.toJSONString))
      producer.send(new ProducerRecord[String, String](topic, event.toJSONString))

      event.put("uuid", UUID())
      event.put("sp", sp())
      event.put("bandwidth", bandwidth())
      event.put("location", location())
      println("Message sent --> " + event.toJSONString)
      //producer.send(new KeyedMessage[String, String](topic, event.toJSONString))
      producer.send(new ProducerRecord[String, String](topic, event.toJSONString))

      Thread.sleep(10000)
    }
  }

  def UUID(): String = {
    pointer = pointer + 1
    if (pointer >= uuid.length) {
      pointer = 0
      uuid(pointer)
    } else {
      uuid(pointer)
    }
  }

  def sp(): String = {
    random.nextInt(1) + ".12.9.396"
  }

  def bandwidth(): Double = {
    random.nextGaussian().abs * 1000
  }

  def location(): JSONArray = {
    JSON.parseArray("[" + random.nextInt(1) + "," + random.nextInt(1) + "]")
  }
}
