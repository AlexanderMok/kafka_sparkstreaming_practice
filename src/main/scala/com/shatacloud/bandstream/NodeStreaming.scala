package com.shatacloud.bandstream

import com.shatacloud.bandstream.service.BandWidthService
import com.shatacloud.bandstream.util.SetupJdbc
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.SparkConf
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.slf4j.{Logger, LoggerFactory}
import scalikejdbc._


/**
  * Created by Alex on 2017/4/24.
  */
object NodeStreaming {

  val logger: Logger = LoggerFactory.getLogger(NodeStreaming.getClass)

  def main(args: Array[String]): Unit = {
    //init kafka
    val conf = ConfigFactory.load
    val kafkaParams = Map[String, Object](
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> conf.getString("kafka.brokers"),
      ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> classOf[StringDeserializer],
      ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> classOf[StringDeserializer],
      ConsumerConfig.GROUP_ID_CONFIG -> conf.getString("kafka.group_id"),
      ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "none",
      ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> (false: java.lang.Boolean)
    )
    val jdbcDriver = conf.getString("jdbc.driver")
    val jdbcUrl = conf.getString("jdbc.url")
    val jdbcUser = conf.getString("jdbc.user")
    val jdbcPassword = conf.getString("jdbc.password")

    val ssc = setupAndComputeStreamingContext(kafkaParams, jdbcDriver, jdbcUrl, jdbcUser, jdbcPassword, conf)()

    sys.ShutdownHookThread {
      println("Gracefully stopping Spark Streaming Application")
      ssc.stop(stopSparkContext = true, stopGracefully = true)
      ConnectionPool.closeAll()
      println("Application stopped")
    }

    ssc.start()
    ssc.awaitTermination()

  }

  def setupAndComputeStreamingContext(
                kafkaParams: Map[String, Object],
                jdbcDriver: String,
                jdbcUrl: String,
                jdbcUser: String,
                jdbcPassword: String,
                conf: Config
              )(): StreamingContext = {
    //init context
    val sparkConf: SparkConf = new SparkConf()
      .setAppName("NodeBandwithStreming")
      .setMaster("local[2]")
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    val ssc = new StreamingContext(sparkConf, Seconds(10))
    ssc.checkpoint(conf.getString("checkpointDir"))

    SetupJdbc(jdbcDriver, jdbcUrl, jdbcUser, jdbcPassword)

    // begin from the the offsets committed to the database
    val fromOffsets = BandWidthService.getOffsetFromDB
    println(fromOffsets.mkString(","))
    BandWidthService.createAndAggregateStream(ssc, fromOffsets, kafkaParams)
  }
}
