package com.shatacloud.bandstream.service

import com.shatacloud.bandstream.util.UniqueIdGenerator
import org.apache.kafka.common.TopicPartition
import org.apache.spark.sql.Row
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.kafka010.HasOffsetRanges

/**
  * Created by Alex Mok.
  */
trait BaseService {

  def getOffsetFromDB: Map[TopicPartition, Long]

  def createAndAggregateStream(ssc: StreamingContext, dbFromOffsets: Map[TopicPartition, Long], kafkaParams: Map[String, Object]): StreamingContext

  def generateSubmitId(milliseconds: Long, partitionId: Int): BigInt = UniqueIdGenerator.generateLongId(milliseconds).longValue() + partitionId

  def transactionSavePerBatch(results: Array[Row], hasOffsetRanges: HasOffsetRanges, submitId: BigInt)
}
