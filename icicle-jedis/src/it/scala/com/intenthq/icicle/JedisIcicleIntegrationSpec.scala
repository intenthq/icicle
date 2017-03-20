package com.intenthq.icicle

import org.specs2.mutable._

class JedisIcicleIntegrationSpec extends Specification {
  "constructor" should {
    "sets the correct host and port if a valid host and port is passed" in {
      val underTest = new JedisIcicle("localhost:6379")
      val jedis = underTest.getJedisPool.getResource

      (jedis.getClient.getHost must_== "localhost") and
        (jedis.getClient.getPort must_== 6379)
    }
  }
}
