local lock_key = 'icicle-generator-lock'
local sequence_key = 'icicle-generator-sequence'
local logical_shard_id_key = 'icicle-generator-logical-shard-id'

local max_sequence = tonumber(KEYS[1])
local min_logical_shard_id = tonumber(KEYS[2])
local max_logical_shard_id = tonumber(KEYS[3])
local num_ids = tonumber(KEYS[4])

if redis.call('EXISTS', lock_key) == 1 then
  redis.log(redis.LOG_NOTICE, 'Icicle: Cannot generate ID, waiting for lock to expire.')
  return redis.error_reply('Icicle: Cannot generate ID, waiting for lock to expire.')
end

--[[
Increment by a set number, this can
--]]
local end_sequence = redis.call('INCRBY', sequence_key, num_ids)
local start_sequence = end_sequence - num_ids + 1
local logical_shard_id = tonumber(redis.call('GET', logical_shard_id_key)) or -1

if end_sequence >= max_sequence then
  --[[
  As the sequence is about to roll around, we can't generate another ID until we're sure we're not in the same
  millisecond since we last rolled. This is because we may have already generated an ID with the same time and
  sequence, and we cannot allow even the smallest possibility of duplicates. It's also because if we roll the sequence
  around, we will start generating IDs with smaller values than the ones previously in this millisecond - that would
  break our k-ordering guarantees!

  The only way we can handle this is to block for a millisecond, as we can't store the time due the purity constraints
  of Redis Lua scripts.

  In addition to a neat side-effect of handling leap seconds (where milliseconds will last a little bit longer to bring
  time back to where it should be) because Redis uses system time internally to expire keys, this prevents any duplicate
  IDs from being generated if the rate of generation is greater than the maximum sequence per millisecond.

  Note that it only blocks even it rolled around *not* in the same millisecond; this is because unless we do this, the
  IDs won't remain ordered.
  --]]
  redis.log(redis.LOG_NOTICE, 'Icicle: Rolling sequence back to the start, locking for 1ms.')
  redis.call('SET', sequence_key, '-1')
  redis.call('PSETEX', lock_key, 1, 'lock')
  end_sequence = max_sequence
end

--[[
The TIME command MUST be called after anything that mutates state, or the Redis server will error the script out.
This is to ensure the script is "pure" in the sense that randomness or time based input will not change the
outcome of the writes.

See the "Scripts as pure functions" section at http://redis.io/commands/eval for more information.
--]]
local time = redis.call('TIME')

return {
  start_sequence,
  end_sequence, -- Doesn't need conversion, the result of INCR or the variable set is always a number.
  logical_shard_id,
  tonumber(time[1]),
  tonumber(time[2])
}
