package com.intenthq.icicle;

/**
 * This exception is thrown when a JedisIcicle instance is attempted to be created with an invalid "host:port" string.
 */
public class InvalidServerFormatException extends RuntimeException {
  /**
   * Create a new instance of this exception with the invalid "host:port" string that was passed.
   *
   * @param invalidServerAndPort The invalid string that was passed.
   */
  public InvalidServerFormatException(final String invalidServerAndPort) {
    super("The given redis server is not in the expected format 'host:port': " + invalidServerAndPort);
  }
}
