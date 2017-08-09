package com.intenthq.icicle.redis

import java.util

import org.specs2.matcher.ThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mutable._
import org.specs2.specification.Scope

object RoundRobinRedisPoolSpec extends Specification {
  "constructor" should {
    "throw exception if null list of servers given" in {
      new RoundRobinRedisPool(null) must throwA[NullPointerException]
    }

    "throw exception if empty list of servers given" in {
      new RoundRobinRedisPool(util.Arrays.asList()) must throwA[IllegalArgumentException]
    }
  }
  "#getNextRedis" should  {
    "cycle through the redis servers in an infinite loop" in new Context {
      (underTest.getNextRedis must_== redisServerOne) and
        (underTest.getNextRedis must_== redisServerTwo) and
        (underTest.getNextRedis must_== redisServerThree) and
        (underTest.getNextRedis must_== redisServerOne) and
        (underTest.getNextRedis must_== redisServerTwo) and
        (underTest.getNextRedis must_== redisServerThree)
    }
  }

  trait Context extends Scope with Mockito with ThrownExpectations {
    val redisServerOne = mock[Redis]
    val redisServerTwo = mock[Redis]
    val redisServerThree = mock[Redis]

    val underTest = new RoundRobinRedisPool(util.Arrays.asList(redisServerOne, redisServerTwo, redisServerThree))
  }
}
