package com.intenthq.icicle;

import com.google.common.base.Optional;

import com.intenthq.icicle.redis.IcicleRedisResponse;
import com.intenthq.icicle.redis.Redis;

import java.util.List;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisDataException;

public class TestRedis implements Redis {
  private final Jedis jedis;

  public TestRedis(final String host, final int port) {
    this.jedis = new Jedis(host, port);
  }

  public String get(final String key) {
    return jedis.get(key);
  }

  public void set(final String key, final String value) {
    jedis.set(key, value);
  }

  @Override
  public boolean exists(final String key) {
    return jedis.exists(key);
  }

  @Override
  public String loadLuaScript(final String luaScript) {
    return jedis.scriptLoad(luaScript);
  }

  @Override
  public Optional<IcicleRedisResponse> evalLuaScript(final String luaScriptSha, final List<String> arguments) {
    String[] args = arguments.toArray(new String[arguments.size()]);

    try {
      @SuppressWarnings("unchecked")
      List<Long> results = (List<Long>) jedis.evalsha(luaScriptSha, arguments.size(), args);
      return Optional.of(new IcicleRedisResponse(results));
    } catch (JedisDataException e) {
      return Optional.absent();
    }
  }
}
