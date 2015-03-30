package com.intenthq.icicle

import java.util

import com.google.common.base.Optional
import com.intenthq.icicle.exception.InvalidLogicalShardIdException
import com.intenthq.icicle.redis.{IcicleRedisResponse, Redis, RoundRobinRedisPool}
import org.junit.runner.RunWith
import org.specs2.matcher.ThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope

@RunWith(classOf[JUnitRunner])
class IcicleIdGeneratorSpec extends Specification {
  "#generateId" should {
    "retry a default of 5 times" in new Context {
      redis.evalLuaScript(any, any) returns Optional.absent[IcicleRedisResponse]
      new IcicleIdGenerator(roundRobinRedisPool).generateId

      // The number is double because if the eval fails the first time it loads and tries to eval again.
      there were exactly(10)(redis).evalLuaScript(any, any)
    }

    "retry `maximumAttempts` times if passed" in new Context {
      redis.evalLuaScript(any, any) returns Optional.absent[IcicleRedisResponse]
      new IcicleIdGenerator(roundRobinRedisPool, 10).generateId

      // The number is double because if the eval fails the first time it loads and tries to eval again.
      there were exactly(20)(redis).evalLuaScript(any, any)
    }

    "return an absent optional if `maximumAttempts` is exceeded" in new Context {
      redis.evalLuaScript(any, any) returns Optional.absent[IcicleRedisResponse]

      val result = underTest.generateId

      result.isPresent must beFalse
    }

    "return an optional with ID" in new Context {
      redis.evalLuaScript(any, any) returns Optional.of(redisResponse)

      val result = underTest.generateId

      result.isPresent must beTrue
    }

    "construct the ID as expected" in new Context {
      redis.evalLuaScript(any, any) returns Optional.of(redisResponse)

      val result = underTest.generateId

      (result.isPresent must beTrue) and
        (result.get.getId must_== 5980618838099710408L) and
        (result.get.getTime must_== 1427291923000L)
    }

    "fail if the logicalShardId is too small" in new Context {
      redisResponse.getLogicalShardId returns -1
      redis.evalLuaScript(any, any) returns Optional.of(redisResponse)

      underTest.generateId.isPresent must beFalse
    }

    "fail if the logicalShardId is too big" in new Context {
      redisResponse.getLogicalShardId returns 9999
      redis.evalLuaScript(any, any) returns Optional.of(redisResponse)

      underTest.generateId.isPresent must beFalse
    }

    "load the lua script if not already loaded" in new Context {
      redis.evalLuaScript(any, any) returns Optional.absent[IcicleRedisResponse]

      underTest.generateId

      there was one(redis).loadLuaScript(any)
    }

    "return an optional with ID even if the script had to be loaded" in new Context {
      redis.evalLuaScript(any, any) returns Optional.absent[IcicleRedisResponse] thenReturn Optional.of(redisResponse)

      val result = underTest.generateId

      result.isPresent must beTrue
    }

    "fail if loading the script fails twice" in new Context {
      redis.evalLuaScript(any, any) returns Optional.absent[IcicleRedisResponse]

      underTest.generateId.isPresent must beFalse
    }
  }

  trait Context extends Scope with Mockito with ThrownExpectations {
    val redis = mock[Redis]
    val roundRobinRedisPool = new RoundRobinRedisPool(util.Arrays.asList(redis))
    val underTest = new IcicleIdGenerator(roundRobinRedisPool, 1)

    val redisResponse = mock[IcicleRedisResponse]
    redisResponse.getTimeSeconds returns 1427291923
    redisResponse.getTimeMicroseconds returns 123
    redisResponse.getSequence returns 456
    redisResponse.getLogicalShardId returns 789
  }
}
