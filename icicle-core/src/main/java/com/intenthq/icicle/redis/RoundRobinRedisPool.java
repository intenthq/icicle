package com.intenthq.icicle.redis;

import com.google.common.base.Preconditions;

import java.util.Iterator;
import java.util.List;

/**
 * A wrapper around a list of redis instances to provide round robin behaviour for a group of servers.
 *
 * This is useful when you don't need to scale writes, but instead just need to use redis in a reliable,
 * distributed manner. An example is to use the redis servers for ID generation and timestamp oracle
 * behaviour.
 */
public class RoundRobinRedisPool {
  private final List<Redis> redisServers;
  private Iterator redisPoolIterator;

  /**
   * Creates a new round robin redis pool from the given list of servers.
   *
   * @param redisServers A list of redis servers to use.
   */
  public RoundRobinRedisPool(final List<Redis> redisServers) {
    Preconditions.checkNotNull(redisServers);
    Preconditions.checkArgument(redisServers.size() > 0);

    this.redisServers = redisServers;
    this.redisPoolIterator = redisServers.iterator();
  }

  /**
   * Returns the next instance of Redis from the pool, moving the iterator forward or looping back to the start if the
   * iterator is at the end.
   *
   * @return The instance of Redis as pulled from the pool.
   */
  public synchronized Redis getNextRedis() {
    if (!redisPoolIterator.hasNext()) {
      redisPoolIterator = redisServers.iterator();
    }

    return (Redis) redisPoolIterator.next();
  }
}
