package com.shatacloud.bandstream.util

import com.typesafe.config.{Config,ConfigFactory}

object AppConfig {
  val conf: Config = ConfigFactory.load

  val CHECKPOINT_DIR: String = conf.getString("checkpointDir")

  val TOPIC: String = conf.getString("kafka.topic")
  val BOOTSTRAP_SERVERS_CONFIG: String = conf.getString("kafka.brokers")
  val GROUP_ID_CONFIG: String = conf.getString("kafka.group_id")

  val MYSQL_JDBC_DRIVER: String = conf.getString("mysql.driver")
  val MYSQL_JDBC_URL: String = conf.getString("mysql.url")
  val MYSQL_JDBC_USER: String = conf.getString("mysql.user")
  val MYSQL_JDBC_PASSWORD: String = conf.getString("mysql.password")

  val TABLE_AGGREGATE_TRAFFIC_5 = conf.getString("table.raw_aggregate_traffic_5")
  val TABLE_KAFKA_OFFSET = conf.getString("table.node_traffic_kafka_offset")
}
