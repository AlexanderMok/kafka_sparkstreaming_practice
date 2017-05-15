package com.shatacloud.bandstream.util

import scalikejdbc.{ConnectionPool, ConnectionPoolSettings}

object SetupJdbc {
  def apply(driver: String, url: String, user: String, password: String): Unit = {
    Class.forName(driver)
    ConnectionPool.singleton(url, user, password,
      ConnectionPoolSettings(connectionPoolFactoryName = "bonecp"))
  }
}
