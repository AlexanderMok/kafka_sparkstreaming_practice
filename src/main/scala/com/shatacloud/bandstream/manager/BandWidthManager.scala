package com.shatacloud.bandstream.manager

import com.shatacloud.bandstream.model.LogRecordModel
import com.typesafe.config.Config
import org.apache.kafka.common.TopicPartition
import org.apache.spark.sql.cassandra._
import org.apache.spark.sql.{Dataset, Row}
import org.apache.spark.streaming.kafka010.HasOffsetRanges
import org.slf4j.{Logger, LoggerFactory}
import scalikejdbc.{DB, SQL}


/**
  * Created by Alex Mok
  */
object BandWidthManager {
  val logger: Logger = LoggerFactory.getLogger(BandWidthManager.getClass)

  def findOffsetFromDB(conf: Config): Map[TopicPartition, Long] = {
    val fromOffsets = DB.readOnly { implicit session =>
      SQL("""select topic, partition_num, from_offset, until_offset from kafka_offset where topic in (?)""")
        .bind(conf.getString("kafka.topic"))
        .map { resultSet =>
          new TopicPartition(resultSet.string(1), resultSet.int(2)) -> resultSet.long(3)
        }.list.apply().toMap
    }
    logger.info(s"Kafka Offsets query from DB is ${fromOffsets.mkString("["," , ","]")}")
    fromOffsets
  }

  def transactionSavePerBatch(results: Array[Row], hasOffsetRanges: HasOffsetRanges, submitId: BigInt, conf: Config) = {
    DB.localTx { implicit session =>
      results.foreach { row =>
        val metricRows =
          SQL(s"""insert into ${conf.getString("table.raw_aggregate_traffic_5")} values(?,?,?,?,?)""")
            .bind(
              submitId,
              row.getString(0) match { case null => "-"; case _ => row.getString(0) },
              row.getString(1) match { case null => "-"; case _ => row.getString(1) },
              row.getString(2) match { case null => "-"; case _ => row.getString(2) },
              row.getDecimal(3) match { case null => 0; case _ => row.getDecimal(3) }
            )
            .update()
            .apply()
        if (metricRows != 1) {
          logger.error(
            s"""Got $metricRows rows affected instead of 1 when attempting to update bandwidth calculation
               |for submitId [$submitId]""".stripMargin)
          throw new IllegalStateException(
            s"""Got $metricRows rows affected instead of 1 when attempting to update
               |bandwidth calculation for submitId [$submitId]""".stripMargin)
        }
      }

      hasOffsetRanges.offsetRanges.foreach { osr =>
        val offsetRows =
          SQL("""update kafka_offset set from_offset = ? where topic = ? and partition_num = ? and from_offset = ?""")
            .bind(osr.untilOffset, osr.topic, osr.partition, osr.fromOffset)
            .update
            .apply()
        if (offsetRows != 1) {
          logger.error(
            s"""Got $offsetRows rows affected instead of 1 when attempting to update kafka offsets in DB
               |for ${osr.topic} ${osr.partition} ${osr.fromOffset} -> ${osr.untilOffset}.
               |Was a partition repeated after a worker failure?""".stripMargin)
          throw new IllegalStateException(
            s"""Got $offsetRows rows affected instead of 1 when attempting to update kafka offsets in DB
               |for ${osr.topic} ${osr.partition} ${osr.fromOffset} -> ${osr.untilOffset}.
               |Was a partition repeated after a worker failure?""".stripMargin)
        }
      }
    }
  }

  def transactionSavePerBatchCassandra(results: Array[Row], hasOffsetRanges: HasOffsetRanges, submitId: BigInt, conf: Config) = {

  }

  def saveRawLogCassandra(dataset: Dataset[LogRecordModel], conf: Config): Dataset[LogRecordModel] = {
    dataset.write.format("org.apache.spark.sql.cassandra").cassandraFormat("","","").save()
    dataset
  }
}
