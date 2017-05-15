package com.shatacloud.bandstream.util

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession

/**
  * Lazily instantiated singleton instance of SparkSession
  * Created by Alex on 2017/4/25.
  */
object SparkSessionSingleton {
  @transient private var instance: SparkSession = _
  def getInstance(sparkConf: SparkConf): SparkSession = {
    if (instance == null) {
      instance = SparkSession
        .builder
        .config(sparkConf)
        //.config("spark.sql.warehouse.dir", "")
        .enableHiveSupport()
        .getOrCreate()
    }
    instance
  }
}
