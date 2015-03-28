package com.intenthq.icicle.exception;

/**
 * Exception thrown if the Lua script embedded in the JAR resources fails to load. This is an unrecoverable exception
 * as the ID generation cannot function without the Lua script.
 */
public class LuaScriptFailedToLoadException extends RuntimeException {
  public LuaScriptFailedToLoadException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
