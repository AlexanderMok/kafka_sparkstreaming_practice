package com.shatacloud.bandstream


import com.alibaba.fastjson.{JSON, JSONObject}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, KafkaUtils, LocationStrategies}
import org.apache.spark.streaming.{Minutes, StreamingContext}

/**
  * Created by Alex on 2017/4/25.
  */
object KafkaLogConsumer {

  def main(args: Array[String]): Unit = {
    //init context
    val sparkConf: SparkConf = new SparkConf()
      .setAppName("KafkaLogTest")
      .setMaster("local[2]")
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    val ssc = new StreamingContext(sparkConf, Minutes(1))
    ssc.checkpoint("checkpoint")
    //ssc.remember(Minutes(5))

    //init kafka
    val topics = Set("test_data")
    val brokers = "172.30.40.103:9092"
    val kafkaParams: Map[String, String] = Map[String, String](
      "bootstrap.servers" -> brokers,
      //"serializer.class" -> "kafka.serializer.StringDecoder",
      "value.deserializer" -> "org.apache.kafka.common.serialization.StringDeserializer",
      "key.deserializer" -> "org.apache.kafka.common.serialization.StringDeserializer",
      "group.id" -> "streaming",
      "compression.codec" -> "2"
    )

    //low level API to pull
    //val kafkaStream: InputDStream[(String, String)] = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](ssc, kafkaParams, topics)

    val kafkaStream: InputDStream[ConsumerRecord[String, String]] = KafkaUtils.createDirectStream(ssc, LocationStrategies.PreferConsistent, ConsumerStrategies.Subscribe(topics, kafkaParams))

    val events: DStream[JSONObject] = kafkaStream.flatMap(line => Some(JSON.parseObject(line.value())))

    //process data
    events.foreachRDD(rdd => {
      val sparkSession = SparkSession.builder.config(rdd.sparkContext.getConf).getOrCreate()
      import sparkSession.implicits._
      val eventDataFrame = rdd.map(f => Record(f.getString("uuid"), f.getString("sp"), f.getDouble("bandwidth"), f.getString("location"))).toDF()
      eventDataFrame.createOrReplaceTempView("events")
      val sumDataFrame = sparkSession.sql("select uuid, sp, sum(bandwidth), location from events group by uuid, sp, location")
      val results = sumDataFrame.collect().iterator
      while (results.hasNext) {
        val item = results.next()
        val event = new JSONObject()
        event.put("uuid", item.getString(0))
        event.put("sp", item.getString(1))
        event.put("bandwidth", item.getDouble(2))
        event.put("location", item.getString(3))
        println("Processed data --> " + event.toJSONString)
      }

      /*rdd.foreachPartition { partitionOfRecords => {
        val eventDataFrame = partitionOfRecords.map(f => Record(f.getString("uuid"), f.getString("sp"), f.getDouble("bandwidth"), f.getString("location"))).toDF()
        eventDataFrame.createOrReplaceTempView("events")
        val sumDataFrame = sparkSession.sql("select uuid, sp, sum(bandwidth), location from events group by uuid, sp, location")
        val results = sumDataFrame.collect().iterator
        //save result or just print
        while (results.hasNext) {
          val item = results.next()
          val event = new JSONObject()
          event.put("uuid", item.getString(0))
          event.put("sp", item.getString(1))
          event.put("bandwidth", item.getDouble(2))
          event.put("location", item.getString(3))
          println("Processed data --> " + event.toJSONString)
        }
      }*/
  })

  // Listen for SIGTERM and shutdown gracefully.
  sys.ShutdownHookThread {
    println("Gracefully stopping Spark Streaming Application")
    ssc.stop(stopSparkContext = true, stopGracefully = true)
    println("Application stopped")
  }
  ssc.start()
  ssc.awaitTermination()
}

case class Record(uuid: String, sp: String, bandwidth: Double, location: String)

}


