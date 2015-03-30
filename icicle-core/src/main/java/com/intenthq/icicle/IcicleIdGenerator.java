package com.intenthq.icicle;

import com.google.common.base.Optional;

import com.intenthq.icicle.exception.InvalidLogicalShardIdException;
import com.intenthq.icicle.exception.LuaScriptFailedToLoadException;
import com.intenthq.icicle.redis.Redis;
import com.intenthq.icicle.redis.IcicleRedisResponse;
import com.intenthq.icicle.redis.RoundRobinRedisPool;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Generates IDs using Redis that have strong guarantees of k-ordering, and include a timestamp that can be considered
 * issued by a time oracle so long as time is kept in check on the Redis instances used.
 *
 * This allows events to be generated in a distributed fashion, stored in a immutable data-store, and a fetch to
 * reconstruct time ordering at any point in the future with strong guarantees that the order is the intended one.
 *
 * We are generating an ID that will be comprised of the following:
 *
 * > 41 bit time + 10 bit logical shard id + 12 bit sequence id
 *
 * Note this adds to 63 bit, because the MSB is reserved in some languages and we value interoperability.
 */
public class IcicleIdGenerator {
  private static final Logger logger = LoggerFactory.getLogger(IcicleIdGenerator.class);

  private static final String LUA_SCRIPT_RESOURCE_PATH = "/id-generation.lua";
  private static final int DEFAULT_MAX_ATTEMPTS = 5;

  // We specify an custom epoch that we will use to fit our timestamps within the bounds of the 41 bits we have
  // available. This gives us a range of ~69 years within which we can generate IDs.
  private static final long CUSTOM_EPOCH = 1401277473L;

  private static final int LOGICAL_SHARD_ID_BITS = 10;
  private static final int SEQUENCE_BITS = 12;

  private static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + LOGICAL_SHARD_ID_BITS;
  private static final int LOGICAL_SHARD_ID_SHIFT = SEQUENCE_BITS;

  // These three bitopped constants are also used as bit masks for the maximum value of the data they represent.
  private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);
  private static final long MAX_LOGICAL_SHARD_ID = ~(-1L << LOGICAL_SHARD_ID_BITS);
  private static final long MIN_LOGICAL_SHARD_ID = 1L;

  private static final long ONE_MILLI_IN_MICRO_SECS = TimeUnit.MICROSECONDS.convert(1, TimeUnit.MILLISECONDS);

  private final RoundRobinRedisPool roundRobinRedisPool;
  private final int maximumAttempts;
  private final String luaScript;
  private final String luaScriptSha;

  /**
   * Create an ID generator that will operate using the given pool of Redis servers. The servers will be used in a
   * round-robin fashion.
   *
   * Note that this constructor means that if a failure occurs, we will attempt to retry generating the ID up to 5
   * times.
   *
   * @param roundRobinRedisPool The pool of Redis servers to use for ID generation.
   */
  public IcicleIdGenerator(final RoundRobinRedisPool roundRobinRedisPool) {
    this(roundRobinRedisPool, DEFAULT_MAX_ATTEMPTS);
  }

  /**
   * Create an ID generator that will operate using the given pool of Redis servers. The servers will be used in a
   * round-robin fashion.
   *
   * Note that this constructor means that if a failure occurs, we will attempt to retry generating the ID up to the
   * number of `maximumAttempts` specified. Specify 1 to try only once.
   *
   * @param roundRobinRedisPool The pool of Redis servers to use for ID generation.
   * @param maximumAttempts The number of times to attempt ID generation in the case of failures.
   */
  public IcicleIdGenerator(final RoundRobinRedisPool roundRobinRedisPool, final int maximumAttempts) {
    this.roundRobinRedisPool = roundRobinRedisPool;
    this.maximumAttempts = maximumAttempts;

    try {
      InputStream is = this.getClass().getResourceAsStream(LUA_SCRIPT_RESOURCE_PATH);
      this.luaScript = IOUtils.toString(is, "UTF-8");
    } catch (IOException e) {
      throw new LuaScriptFailedToLoadException("Could not load Icicle Lua script from the resources in the JAR.", e);
    }

    // Lame, but we have to use the deprecated version here in order to get this running in Hadoop (which ships with
    // commons-codec 1.4 still for some ungodly reason...)
    this.luaScriptSha = Hex.encodeHexString(DigestUtils.sha(luaScript));
  }

  /**
   * Generate an ID. It will try to generate an ID, retrying up to `maximumAttempts` times.
   *
   * @return An optional ID. It will be present if it was successful, and absent if for any reason the ID generation
   * failed even after the retries.
   */
  public Optional<Id> generateId() {
    for (int retries = 0; retries < maximumAttempts; retries++) {
      try {
        Optional<Id> result = generateIdUsingRedis(roundRobinRedisPool.getNextRedis());

        // We'll retry if the ID didn't generate for whatever reason.
        if (result.isPresent()) {
          return result;
        }

        // Exponentially back-off the more we have to retry. The sleep time will be of the sequence:
        //
        // > 0, 1, 4, 9, 16, 25, 36, 49, 64, 81, ...
        //
        // This avoids a total run on the cluster of ID generation Redis servers.
        Thread.sleep(retries * retries);
      } catch (RuntimeException | InterruptedException e) {
        logger.warn("Failed to generate ID. Underlying exception was: {}", e);
      }
    }

    logger.error("No ID generated. ID generation failed after {} retries.", maximumAttempts);
    return Optional.absent();
  }

  /**
   * Generate an ID using the given redis instance.
   *
   * @param redis The redis instance to use to generate an ID with.
   * @return An optional ID. It will be present if it was successful, and absent if for any reason the response was
   * null.
   */
  private Optional<Id> generateIdUsingRedis(final Redis redis) {
    Optional<IcicleRedisResponse> optionalIcicleRedisResponse = executeOrLoadLuaScript(redis);

    if (!optionalIcicleRedisResponse.isPresent()) {
      return Optional.absent();
    }

    IcicleRedisResponse icicleRedisResponse = optionalIcicleRedisResponse.get();

    // We get the timestamp from Redis in seconds, but we get microseconds too, so we can make a timestamp in
    // milliseconds (losing some precision in the meantime for the sake of keeping things in 41 bits) using both of
    // these values.
    long timestamp = (icicleRedisResponse.getTimeSeconds() * ONE_MILLI_IN_MICRO_SECS)
        + (icicleRedisResponse.getTimeMicroseconds() / ONE_MILLI_IN_MICRO_SECS);

    long logicalShardId = icicleRedisResponse.getLogicalShardId();
    validateLogicalShardId(logicalShardId);

    // Here's the fun bit-shifting. The purpose of this is to get a 64-bit ID of the following
    // format:
    //
    //  ABBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBCCCCCCCCCCDDDDDDDDDDDD
    //
    // Where:
    //   * A is the reserved signed bit of a Java long.
    //   * B is the timestamp in milliseconds since custom epoch bits, 41 in total.
    //   * C is the logical shard ID, 10 bits in total.
    //   * D is the sequence, 12 bits in total.
    long id = ((timestamp - CUSTOM_EPOCH) << TIMESTAMP_SHIFT)
        | (logicalShardId << LOGICAL_SHARD_ID_SHIFT)
        | icicleRedisResponse.getSequence();

    return Optional.of(new Id(id, timestamp));
  }

  /**
   * Try executing the Lua script using the SHA of its contents.
   *
   * If the Lua script hasn't been loaded before, we'll load it first and then try executing it again. This should
   * only need to be done once per version of the given Lua script. This guards against a Redis server being added
   * into the pool to help increase capacity, as the script will just be loaded again if missing.
   *
   * This also gives a performance gain:
   *
   *   * If the Lua script is already loaded, it's already parsed, tokenised and in memory. This is MUCH faster
   *     than loading it again every time using eval instead of evalsha.
   *   * If the script with this SHA was already loaded by another process, we can use it instead of loading it
   *     again, giving us a small performance gain.
   *
   * @param redis The redis instance to use to execute or load the Lua script with.
   * @return The result of executing the Lua script.
   */
  private Optional<IcicleRedisResponse> executeOrLoadLuaScript(final Redis redis) {
    Optional<IcicleRedisResponse> response = executeLuaScript(redis);

    // Great! The script was already loaded and ran, so we saved a call.
    if (response.isPresent()) {
      return response;
    }

    // Otherwise we need to load and try again, failing if it doesn't work the second time.
    redis.loadLuaScript(luaScript);
    return executeLuaScript(redis);
  }

  /**
   * Execute the ID generation Lua script on the given redis instance, returning the results.
   *
   * @param redis The redis instance to use to execute the Lua script with.
   * @return The optional result of executing the Lua script. Absent if the Lua script referenced by the SHA was missing
   * when it was attempted to be executed.
   */
  private Optional<IcicleRedisResponse> executeLuaScript(final Redis redis) {
    List<String> args = Arrays.asList(String.valueOf(MAX_SEQUENCE),
                                      String.valueOf(MIN_LOGICAL_SHARD_ID),
                                      String.valueOf(MAX_LOGICAL_SHARD_ID));
    return redis.evalLuaScript(luaScriptSha, args);
  }

  /**
   * Check that the given logical shard ID is within the bounds that we allow. This is important to
   * check, as otherwise when bit-shifting we may lose digits outside of the bits we care about,
   * introducing possible collisions.
   *
   * @param logicalShardId The logical shard ID as retrieved from Redis.
   */
  private void validateLogicalShardId(final long logicalShardId) {
    if (logicalShardId < MIN_LOGICAL_SHARD_ID || logicalShardId > MAX_LOGICAL_SHARD_ID) {
      throw new InvalidLogicalShardIdException(
          "The logical shard ID set in Redis is less than " + String.valueOf(MIN_LOGICAL_SHARD_ID)
              + " or is greater than the supported maximum of "
              + String.valueOf(MAX_LOGICAL_SHARD_ID));
    }
  }
}
