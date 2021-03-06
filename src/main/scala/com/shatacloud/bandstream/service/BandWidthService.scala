package com.shatacloud.bandstream.service

import com.shatacloud.bandstream.manager.BandWidthManager
import com.shatacloud.bandstream.model.LogRecordModel
import com.shatacloud.bandstream.util.SparkSessionSingleton
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.{KafkaException, TopicPartition}
import org.apache.spark.TaskContext
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, Dataset, Row}
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, HasOffsetRanges, KafkaUtils, LocationStrategies}
import org.slf4j.{Logger, LoggerFactory}

/**
  * Created by Alex Mok
  */
object BandWidthService extends BaseService {

  val logger: Logger = LoggerFactory.getLogger(BandWidthService.getClass)

  /**
    * Retrieve committed kafka topic, partition_num, from_offset from DB
    *
    * @return kafka topic, partition_num, from_offset
    */
  override def getOffsetFromDB: Map[TopicPartition, Long] = {
    BandWidthManager.findOffsetFromDB
  }

  /**
    * Apply spark Direct Kafka API to create DStream.
    * Aggregate(SUM) traffic per batch
    *
    * @param ssc           StreamingContext
    * @param dbFromOffsets offsetMap retrieved from DB
    * @param kafkaParams   kafka configuration params
    * @return ssc StreamingContext
    */
  override def createAndAggregateStream(ssc: StreamingContext, dbFromOffsets: Map[TopicPartition, Long], kafkaParams: Map[String, Object]): StreamingContext = {
    //val kafkaStream08 = org.apache.spark.streaming.kafka.KafkaUtils.createDirectStream[String,String](ssc, kafkaParams, topics)
    //val logStream08 = kafkaStream08.map(e => e._2).map(line => line.split("\\|"))
    try {
      val stream = KafkaUtils.createDirectStream[String, String](
        ssc,
        LocationStrategies.PreferConsistent,
        ConsumerStrategies.Assign[String, String](dbFromOffsets.keys.toList, kafkaParams, dbFromOffsets)
      )
      stream.foreachRDD { (rdd, time) =>
        val offsetRanges = rdd.asInstanceOf[HasOffsetRanges]
        logger.debug(s"Kafka Offsets directly get from Kafka ${offsetRanges.offsetRanges.mkString("[", " , ", "]")}")

        val partitionId = TaskContext.getPartitionId() + 1
        val uniqueId = this.generateSubmitId(time.milliseconds, partitionId)
        val recordDataFrame = mapLogRecordModel(rdd)
        val results = this.aggregateBandWidth(recordDataFrame, rdd).collect()

        // Back to running on the driver and this will cause shuffle
        this.transactionSavePerBatch(results, offsetRanges, uniqueId)
        //rdd.foreachPartitionAsync(i => i.map{e=>e.value().split("\\|")}.map(e => if(e.length!=39){}))
      }
    } catch {
      case e: KafkaException => logger.error(s"Create KafkaStream error. $e")
      case e: Exception => logger.error(s"Aggregation of KafkaStream error. $e")
    }
    ssc
  }

  /**
    * Map raw log record to LogRecordModel and save the raw data to Cassandra
    *
    * @param rdd a kafkaRDD wrapped log data get from Direct Kafka API
    * @return a DataFrame represents LogRecordModel
    */

  private def mapLogRecordModel(rdd: RDD[ConsumerRecord[String, String]]): Dataset[LogRecordModel] = {
    val sparkSession = SparkSessionSingleton.getInstance(rdd.sparkContext.getConf)
    import sparkSession.implicits._
    val recordDataFrame = rdd.map(record => record.value().split("\\|"))
      .filter(e => {if(e.length != 39){logger.warn(e.mkString("Abnormal Log Record ["," , ","]"))}; e.length == 39})
      .map(e => LogRecordModel(
        e(0),
        e(1),
        e(2) match { case "-" => 0.0; case _ => e(2).toDouble },
        e(3),
        e(4),
        e(5) match { case "-" => 0; case _ => e(5).toInt },
        e(6) match { case "-" => 0.0; case _ => e(6).toDouble },
        e(7) match { case "-" => 0; case _ => e(7).toInt },
        e(8) match { case "-" => 0; case _ => e(8).toLong },
        e(9),
        e(10) match { case "-" => 0; case _ => e(10).toInt },
        e(11), e(12), e(13), e(14),
        e(15) match { case "-" => 0.0; case _ => e(15).toDouble },
        e(16) match { case "-" => 0; case _ => e(16).toInt },
        e(17), e(18), e(19) match { case "-" => 0.0; case _ => e(19).toDouble }, e(20),
        e(21) match { case "-" => 0.0; case _ => e(21).toDouble }, e(22), e(23), e(24),
        e(25) match { case "-" => 0.0; case _ => e(25).toDouble },
        e(26) match { case "-" => 0; case _ => e(26).toLong },
        e(27) match { case "-" => 0; case _ => e(27).toInt },
        e(28), e(29), e(30), e(31), e(32) match { case "-" => 0; case _ => e(32).toLong },
        e(33) match { case "-" => 0.0; case _ => e(33).toDouble }, e(34), e(35), e(36), e(37), e(38)))
      .toDS()
    //BandWidthManager.saveRawLogCassandra(recordDataFrame, conf)
    recordDataFrame
  }

  /**
    * Use sparkSQL to calc traffic or bandwidth if necessary with aggregation operation SUM
    *
    * @param recordDataset log record DataFrame
    * @param rdd           a kafkaRDD get from Direct Kafka API
    * @return a dataFrame that wraps SQL calculation result
    */
  private def aggregateBandWidth(recordDataset: Dataset[LogRecordModel], rdd: RDD[ConsumerRecord[String, String]]): DataFrame = {
    val sparkSession = SparkSessionSingleton.getInstance(rdd.sparkContext.getConf)
    recordDataset.createOrReplaceTempView("node_traffic_events")

    val sql = "select sp_channel, device_node as node_tag, " +
      //"cast(to_date(from_unixtime(cast(msec as bigint) div 300 * 300)) as String) as record_date, " +
      "from_unixtime(cast(msec as BIGINT) div 300 * 300) AS record_time, " +
      "sum(segment_bytes_sent + case when segment_type = 2 then request_length else 0 end) AS traffic " +
      "from node_traffic_events " +
      "where request_method != 'HEAD' and request_method != 'PURGE' and segment_type != 0 " +
      "group by sp_channel, device_node, from_unixtime(cast(msec as BIGINT) div 300 * 300)"

    sparkSession.sql(sql)
  }

  /**
    * DB.localTx is transactional, if business update or offset update fails, neither will be committed
    * store business results and kafka offsets transactionally
    *
    * @param results         traffic calculation result
    * @param hasOffsetRanges HasOffsetRanges that wraps a private OffsetRanges get from Direct Kafka API
    * @param submitId        a unique id generated by timestamp and partition id
    */
  override def transactionSavePerBatch(results: Array[Row], hasOffsetRanges: HasOffsetRanges, submitId: BigInt) = {
    BandWidthManager.transactionSavePerBatch(results, hasOffsetRanges, submitId)
  }
}
