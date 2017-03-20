package com.intenthq.icicle

import java.util

import com.intenthq.icicle.redis.RoundRobinRedisPool
import org.specs2.matcher.ThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.collection.JavaConversions._

class IcicleIdGeneratorIntegrationSpec extends Specification {
  sequential

  "The IcicleIdGenerator" should {
    "handle generating 100,000 unique and k-sorted IDs successfully" in new Context {
      redis.set(logicalShardIdRedisKey, "1")

      val n = 100000
      val ids = (1 to n).map { _ => underTest.generateId() }

      // They were all successful...
      (ids.forall(_.isPresent) must beTrue) and
        // They were all unique...
        (ids.map(_.get).toSet.size must_== n) and
        // And they were in order!
        (ids.map(_.get).map(_.getId) must beSorted)
    }

    "handle generating over 100,000 unique and k-sorted IDs in batch successfully" in new Context {
      redis.set(logicalShardIdRedisKey, "1")
      redis.set(sequenceRedisKey, "-1")

      val n = 100000

      var ids = List[Id]()
      while(ids.size <= n) {
        val result = underTest.generateIdBatch()
        if(result.isPresent())
          ids ++= result.get()
      }

      // They were all unique...
      (ids.toSet.size must beGreaterThan(n)) and
        // And they were in order!
        (ids.map(_.getId) must beSorted)
    }

    "increments the sequence by the maximum batch size part way through the sequence" in new Context {
      val startingSequence = 4000
      redis.set(logicalShardIdRedisKey, "1")
      redis.set(sequenceRedisKey, startingSequence.toString)

      val result = underTest.generateIdBatch()
      (result.isPresent must beTrue) and
        (redis.get(sequenceRedisKey) must_== "-1") and
        (result.get().size must_== 95)
    }

    "increments the sequence by 10" in new Context {
      redis.set(logicalShardIdRedisKey, "1")
      redis.set(sequenceRedisKey, "-1")

      (underTest.generateIdBatch(10).isPresent must beTrue) and
        (redis.get(sequenceRedisKey) must_== "9")
    }

    "increments the sequence by maximum batch size" in new Context {
      redis.set(logicalShardIdRedisKey, "1")
      redis.set(sequenceRedisKey, "-1")

      val result = underTest.generateIdBatch()
      (result.isPresent must beTrue) and
        (redis.get(sequenceRedisKey) must_== "-1") and
        (result.get().size must_== 4096) and
        (result.get().map(_.getId) must beSorted)
    }

    "increments the sequence" in new Context {
      redis.set(logicalShardIdRedisKey, "1")
      redis.set(sequenceRedisKey, "2")

      (underTest.generateId().isPresent must beTrue) and
        (redis.get(sequenceRedisKey) must_== "3")
    }

    "handle overflowing the sequence by rolling it over" in new Context {
      redis.set(logicalShardIdRedisKey, "1")
      redis.set(sequenceRedisKey, "4094")

      (underTest.generateId().isPresent must beTrue) and
        (redis.get(sequenceRedisKey) must_== "-1")
    }
  }

  trait Context extends Scope with Mockito with ThrownExpectations {
    val sequenceRedisKey = "icicle-generator-sequence"
    val logicalShardIdRedisKey = "icicle-generator-logical-shard-id"

    val redis = new TestRedis("localhost", 6379)
    val roundRobinRedisPool = new RoundRobinRedisPool(util.Arrays.asList(redis))
    val underTest = new IcicleIdGenerator(roundRobinRedisPool)
  }
}
