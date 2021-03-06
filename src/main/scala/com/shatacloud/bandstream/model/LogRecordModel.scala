package com.shatacloud.bandstream.model

/**
  * Model case class for log file
  */
case class LogRecordModel(
  http_x_session_id: String,
  sp_channel: String,
  msec: Double,
  request_method: String,
  request_uri: String,
  status: Int,
  response_time: Double,
  response_header_length: Int,
  segment_bytes_sent: BigInt,
  is_hit: String,
  remote_port: Int,
  remote_addr: String,
  device_id: String,
  device_node: String,
  http_referer: String,
  upstream_response_time: Double,
  request_length: Int,
  http_user_agent: String,
  host: String,
  upstream_connect_time: Double,
  scheme: String,
  duration: Double,
  remote_user: String,
  server_addr: String,
  server_port: String,
  msec_first: Double,
  bytes_sent: BigInt,
  segment_type: Int,
  upstream_addr: String,
  status_auth: String,
  http_range: String,
  server_protocol: String,
  body_bytes_sent: BigInt,
  request_time: Double,
  ant_auth_info: String,
  content_type: String,
  cache_control: String,
  ant_log_append: String,
  unknown: String
)