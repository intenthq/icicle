package com.intenthq.icicle

import java.util

import com.intenthq.icicle.redis.RoundRobinRedisPool
import org.specs2.mutable._

object JedisIcicleIntegrationSpec extends Specification {
  "constructor" should {
    "sets the correct host and port if a valid host and port is passed" in {
      val underTest = new JedisIcicle("localhost:6379")
      val jedis = underTest.getJedisPool.getResource

      try {
        (jedis.getClient.getHost must_== "localhost") and
          (jedis.getClient.getPort must_== 6379)
      } finally {
        jedis.close()
      }
    }
  }

  "works with a real ID generator" in {
    val jedisIcicle = new JedisIcicle("localhost:6379")
    val roundRobinRedisPool = new RoundRobinRedisPool(util.Arrays.asList(jedisIcicle))
    val idGenerator = new IcicleIdGenerator(roundRobinRedisPool)
    val jedis = jedisIcicle.getJedisPool.getResource
    try {
      jedis.set(logicalShardIdRedisKey, "1")
      jedis.del(sequenceRedisKey)
    } finally {
      jedis.close()
    }

    val n = 100000
    val ids = (1 to n).map { _ => idGenerator.generateId() }

    // They were all successful...
    (ids.forall(_.isPresent) must beTrue) and
      // They were all unique...
      (ids.map(_.get).toSet.size must_== n) and
      // And they were in order!
      (ids.map(_.get).map(_.getId) must beSorted)
  }

  val sequenceRedisKey = "icicle-generator-sequence"
  val logicalShardIdRedisKey = "icicle-generator-logical-shard-id"
}
