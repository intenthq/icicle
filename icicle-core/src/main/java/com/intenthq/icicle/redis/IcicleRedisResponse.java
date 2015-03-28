package com.intenthq.icicle.redis;

import com.google.common.base.Preconditions;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.List;

/**
 * The response from the Icicle ID generation script.
 *
 * It has four fields, all equally important to generate an ID:
 *
 *   * The sequence
 *   * The logical shard ID
 *   * The current time in seconds
 *   * The current time in microseconds
 *
 * Create an instance of this by passing the result you get back from executing the Lua script with your chosen Redis
 * library.
 */
public class IcicleRedisResponse {
  private static final int SEQUENCE_INDEX = 0;
  private static final int LOGICAL_SHARD_ID_INDEX = 1;
  private static final int TIME_SECONDS_INDEX = 2;
  private static final int TIME_MICROSECONDS_INDEX = 3;

  private final long sequence;
  private final long logicalShardId;
  private final long timeSeconds;
  private final long timeMicroseconds;

  /**
   * Create an instance of the response from the ID generation Lua script.
   * @param results The list of long values returned by the Lua script. If this param is null, a NullPointerException
   *                will be thrown.
   */
  public IcicleRedisResponse(final List<Long> results) {
    Preconditions.checkNotNull(results);

    this.sequence = results.get(SEQUENCE_INDEX);
    this.logicalShardId = results.get(LOGICAL_SHARD_ID_INDEX);
    this.timeSeconds = results.get(TIME_SECONDS_INDEX);
    this.timeMicroseconds = results.get(TIME_MICROSECONDS_INDEX);
  }

  public long getSequence() {
    return sequence;
  }

  public long getLogicalShardId() {
    return logicalShardId;
  }

  public long getTimeSeconds() {
    return timeSeconds;
  }

  public long getTimeMicroseconds() {
    return timeMicroseconds;
  }

  public boolean equals(final Object o) {
    return EqualsBuilder.reflectionEquals(this, o);
  }

  public int hashCode() {
    return HashCodeBuilder.reflectionHashCode(this);
  }
}
