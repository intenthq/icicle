# Icicle [![Build Status](https://travis-ci.org/intenthq/icicle.svg?branch=master)](https://travis-ci.org/intenthq/icicle) [![Join the chat at https://gitter.im/intenthq/icicle](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/intenthq/icicle)
**A distributed, k-sortable unique ID generation system using Redis and Lua.**

Icicle is a project to generate 64-bit, k-sortable unique IDs in a distributed fashion by using Redis' Lua scripting.

Implementations of Icicle could be written in any language using just a [Redis client library](http://redis.io/clients) and [the Lua script](icicle-core/src/main/resources/id-generation.lua).

[Read more about Icicle on the Intent HQ Engineering blog](http://engineering.intenthq.com/2015/03/icicle-distributed-id-generation-with-redis-lua/).

> **Icicle v1.x users:** There are some important breaking changes in v2.x! Please [read the release notes](https://github.com/intenthq/icicle/releases/tag/v2.0.0) for more information.

## Using Icicle

## Setup Your Redis Nodes

You need to have some Redis nodes available and running to use with Icicle. **Icicle works on Redis >= v2.6 as it requires Lua support.** Redis 3.x is supported and tested. We recommend running _at least_ 2 Redis nodes, allowing for redundancy and better performance.

Once you have the Redis servers up and running, **you must assign a logical shard ID to each Redis instance**. This is a unique number between 0 and 1023 (inclusive) that is assigned to each node to prevent duplicate IDs:

```
SET icicle-generator-logical-shard-id 123
```

Make sure this is unique per node, or you run the risk you will generate duplicate IDs. Icicle will otherwise report an error to you if you set the ID too small or too large.

## Configure NTP

To keep your system clock in check, you should install and configure NTP on every Redis node server. **You need to run NTP in a mode where it won't move the clock backwards, as Icicle will not guarantee an ID won't be duplicated it does this.** See [this Stack Overflow post](http://serverfault.com/questions/94683/will-ntp-drift-the-clock-backwards) for more information on the behaviour of NTP.

## Use The Client Library

### With Jedis

A pre-rolled `icicle-jedis` library is available and using it is really simple. Create yourself an instance of the `IcicleIdGenerator` class, passing the

```java
JedisIcicle redisServerOne = new JedisIcicle("server-one:6379");
JedisIcicle redisServerTwo = new JedisIcicle("server-two:6379");
JedisIcicle redisServerThree = new JedisIcicle("server-three:6379");
List<JedisIcicle> redises = Arrays.asList(redisServerOne, redisServerTwo, redisServerThree);

RoundRobinRedisPool roundRobinRedisPool = new RoundRobinRedisPool(redises);
IcicleIdGenerator icicleIdGenerator = new IcicleIdGenerator(roundRobinRedisPool);
```

And now to generate an ID:

```java
Optional<Id> id = icicleIdGenerator.generateId();
```

Or a batch of IDs;

```java
Optional<List<Id>> ids = icicleIdGenerator.generateIdBatch(10); // Get up to 10 IDs
Optional<List<Id>> ids = icicleIdGenerator.generateIdBatch(); // Get up to 4096 IDs
```

When requesting a batch of IDs, the generator cannot always return you the amount you asked for (this is a limitation related to our strong uniqueness guarantees) - instead, it will return between `1` and `n`, where `n` is the number of IDs you asked for in the batch. You may wish to check how many you actually received, and request more until you have the total you need.

### With Another Redis Library

You can use Icicle with any Redis library you please. All you have to do is implement the `Redis` interface, and you can then use the `RoundRobinRedisPool` and `IcicleIdGenerator` classes as above with `icicle-jedis`.

## Structure

We chose to pack our IDs in a 64-bit long, structured as follows:

![The ID structure, in the format ABBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBCCCCCCCCCCDDDDDDDDDDDD](id-structure.png)

* The first bit is the reserved signed bit (which we chose not to use because some platforms make it difficult to get at and it messes with ordering).
* Secondly, the timestamp in milliseconds since a custom epoch, 41 bits in total.
* Next is the logical shard ID, 10 bits in total.
* Finally, the sequence, 12 bits in total.

## Kudos

The name of our project was inspired by a sadly now defunct project from Twitter called "[Snowflake](https://github.com/twitter/snowflake)", from which we drew much inspiration.
