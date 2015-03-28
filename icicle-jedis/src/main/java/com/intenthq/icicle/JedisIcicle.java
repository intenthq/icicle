package com.intenthq.icicle;

import com.google.common.base.Function;
import com.google.common.base.Optional;

import com.intenthq.icicle.redis.Redis;
import com.intenthq.icicle.redis.IcicleRedisResponse;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisDataException;

/**
 * Implementation of the Icicle Redis interface for Jedis.
 */
public class JedisIcicle implements Redis {
  private static final Pattern SERVER_FORMAT = Pattern.compile("^([^:]+):([0-9]+)$");

  private final JedisPool jedisPool;

  /**
   * Create an instance of JedisIcicle from a host and port string of the format "server:port".
   *
   * @param hostAndPort A host and port string for a Redis instance to use for ID generation, of the format "host:port".
   */
  public JedisIcicle(final String hostAndPort) {
    this.jedisPool = jedisPoolFromServerAndPort(hostAndPort);
  }

  /**
   * Create an instance of JedisIcicle from an existing JedisPool instance.
   *
   * @param jedisPool An existing JedisPool instance you have configured that can be used for the ID generation.
   */
  public JedisIcicle(final JedisPool jedisPool) {
    this.jedisPool = jedisPool;
  }

  /**
   * Getter for the JedisPool instance used for the ID generation.
   *
   * @return The instance of JedisPool that was either passed or created from the host and port given.
   */
  public JedisPool getJedisPool() {
    return jedisPool;
  }

  /**
   * Return true if the given key exists, otherwise false.
   *
   * @param key The key to check the existence of.
   * @return True if the given key exists, otherwise false.
   */
  @Override
  public boolean exists(final String key) {
    return withJedis(new Function<Jedis, Boolean>() {
      @Override
      public Boolean apply(final Jedis jedis) {
        return jedis.exists(key);
      }
    });
  }

  /**
   * Load the given Lua script into Redis.
   *
   * @param luaScript The Lua script to load into Redis.
   * @return The SHA of the loaded Lua script.
   */
  @Override
  public String loadLuaScript(final String luaScript) {
    return withJedis(new Function<Jedis, String>() {
      @Override
      public String apply(final Jedis jedis) {
        return jedis.scriptLoad(luaScript);
      }
    });
  }

  /**
   * Execute the Lua script with the given SHA, passing the given list of arguments.
   *
   * @param luaScriptSha The SHA of the Lua script to execute.
   * @param arguments The arguments to pass to the Lua script.
   * @return The optional result of executing the Lua script. Absent if the Lua script referenced by the SHA was missing
   * when it was attempted to be executed.
   */
  @Override
  public Optional<IcicleRedisResponse> evalLuaScript(final String luaScriptSha, final List<String> arguments) {
    return withJedis(new Function<Jedis, Optional<IcicleRedisResponse>>() {
      @Override
      public Optional<IcicleRedisResponse> apply(final Jedis jedis) {
        String[] args = arguments.toArray(new String[arguments.size()]);

        try {
          @SuppressWarnings("unchecked")
          List<Long> results = (List<Long>) jedis.evalsha(luaScriptSha, arguments.size(), args);
          return Optional.of(new IcicleRedisResponse(results));
        } catch (JedisDataException e) {
          return Optional.absent();
        }
      }
    });
  }

  /**
   * Given a string of the format "host:port", create a new JedisPool instance or throw a InvalidServerFormatException
   * if invalid.
   *
   * @param hostAndPort A host and port string for a Redis instance to use for ID generation, of the format "host:port".
   * @return A JedisPool instance pointing at the given host and port.
   * @throws InvalidServerFormatException
   */
  private JedisPool jedisPoolFromServerAndPort(final String hostAndPort) {
    Matcher matcher = SERVER_FORMAT.matcher(hostAndPort);

    if (!matcher.matches()) {
      throw new InvalidServerFormatException(hostAndPort);
    }

    return new JedisPool(matcher.group(1), Integer.valueOf(matcher.group(2)));
  }

  /**
   * Request a Jedis resource from the pool, execute the given callback passing the resource, and then ensure the
   * resource is always returned to the pool regardless of success or failure in the callback.
   * @param callback The callback to pass the Jedis resource to so some operation can be done with it.
   * @param <T> The type that will be returned by the callback.
   * @return The value returned by the callback.
   */
  private <T> T withJedis(final Function<Jedis, T> callback) {
    Jedis jedis = jedisPool.getResource();

    try {
      T result = callback.apply(jedis);
      jedisPool.returnResource(jedis);
      return result;
    } catch (Exception e) {
      jedisPool.returnBrokenResource(jedis);
      throw e;
    }
  }
}
