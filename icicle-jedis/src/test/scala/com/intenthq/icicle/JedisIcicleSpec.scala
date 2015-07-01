package com.intenthq.icicle

import java.util

import _root_.redis.clients.jedis.exceptions.JedisDataException
import _root_.redis.clients.jedis.{Jedis, JedisPool}
import com.google.common.base.Optional
import com.intenthq.icicle.redis.IcicleRedisResponse
import org.junit.runner.RunWith
import org.specs2.matcher.ThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope

@RunWith(classOf[JUnitRunner])
class JedisIcicleSpec extends Specification {
  val luaScript = "foo"
  val sha = "abcdef1234567890"

  "constructor" should {
    "throw a InvalidServerFormatException exception if the host and string passed is invalid" in {
      new JedisIcicle("foibles") must throwA[InvalidServerFormatException]
    }
  }

  "#loadLuaScript" should {
    "call to redis to load the script" in new Context {
      underTest.loadLuaScript(luaScript)

      there was one(jedis).scriptLoad(luaScript)
    }

    "returns the SHA returned by the call to Redis" in new Context {
      jedis.scriptLoad(luaScript) returns sha

      underTest.loadLuaScript(luaScript) must_== sha
    }

    "returns the resource if the call was successful" in new Context {
      underTest.loadLuaScript("foo")

      there was one(jedisPool).returnResource(jedis)
    }

    "returns the resource as failed if the call threw an exception" in new Context {
      jedis.scriptLoad(luaScript) throws new RuntimeException

      try {
        underTest.loadLuaScript(luaScript)
      } catch {
        case e: RuntimeException => null
      }

      there was one(jedisPool).returnBrokenResource(jedis)
    }

    "rethrows any exception the call throws" in new Context {
      val testScript = "foo"
      jedis.scriptLoad(testScript) throws new RuntimeException

      underTest.loadLuaScript(testScript) must throwA[RuntimeException]
    }
  }

  "#evalLuaScript" should {
    val args = util.Arrays.asList("foo")
    val response: java.util.List[java.lang.Long] = util.Arrays.asList(12L, 34L, 56L, 78L, 1L)

    "call to redis to eval the script with the given args" in new Context {
      val argsAsArray: Array[String] = args.toArray(Array[String]())

      jedis.evalsha(any, any, anyString) returns response

      underTest.evalLuaScript(sha, args)

      there was one(jedis).evalsha(sha, args.size(), argsAsArray:_*)
    }

    "returns the response returned by the call to Redis wrapped up as an IcicleRedisResponse" in new Context {
      jedis.evalsha(any, any, anyString) returns response

      underTest.evalLuaScript(sha, args) must_== Optional.of(new IcicleRedisResponse(response))
    }

    "returns absent when a JedisDataException is thrown" in new Context {
      jedis.evalsha(any, any, anyString) throws new JedisDataException("foo", new Throwable)

      underTest.evalLuaScript(sha, args).isPresent must beFalse
    }

    "returns the resource if the call was successful" in new Context {
      jedis.evalsha(any, any, anyString) returns response

      underTest.evalLuaScript(sha, args)

      there was one(jedisPool).returnResource(jedis)
    }

    "returns the resource as failed if the call threw an exception" in new Context {
      jedis.evalsha(any, any, anyString) throws new RuntimeException

      try {
        underTest.evalLuaScript(sha, args)
      } catch {
        case e: RuntimeException => null
      }

      there was one(jedisPool).returnBrokenResource(jedis)
    }

    "rethrows any exception the call throws" in new Context {
      val testScript = "foo"
      jedis.scriptLoad(testScript) throws new RuntimeException

      underTest.loadLuaScript(testScript) must throwA[RuntimeException]
    }
  }

  trait Context extends Scope with Mockito with ThrownExpectations {
    val jedisPool = mock[JedisPool]
    val underTest = new JedisIcicle(jedisPool)
    val jedis = mock[Jedis]

    jedisPool.getResource returns jedis
  }
}
