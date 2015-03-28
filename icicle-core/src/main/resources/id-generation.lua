local sequence_key = 'icicle-generator-sequence'
local logical_shard_id_key = 'icicle-generator-logical-shard-id'

local max_sequence = tonumber(KEYS[1])
local min_logical_shard_id = tonumber(KEYS[2])
local max_logical_shard_id = tonumber(KEYS[3])
local lock_key = KEYS[4]

if redis.call('EXISTS', lock_key) == 1 then
  redis.log(redis.LOG_WARNING, 'Could not proceed with ID generation, lock key is present.')
  return redis.error_reply('Could not proceed with ID generation, lock key is present.')
end

local sequence = redis.call('INCR', sequence_key)
local logical_shard_id = tonumber(redis.call('GET', logical_shard_id_key)) or -1

if sequence == max_sequence then
  --[[
  As the sequence is about to roll around, we can't generate another ID until we're sure we're not in the same
  millisecond since we last rolled. This is because we may have already generated an ID with the same time and
  sequence, and we cannot allow even the smallest possibility of duplicates.

  The only way we can handle this is to block for a millisecond, as we can't store the time due the purity constraints
  of Redis Lua scripts.

  In addition to a neat side-effect of handling leap seconds (where milliseconds will last a little bit longer to bring
  time back to where it should be) because Redis uses system time internally to expire keys, this prevents any duplicate
  IDs from being generated if the rate of generation is greater than the maximum sequence per millisecond.

  Note that it only blocks even it rolled around *not* in the same millisecond; this is because unless we do this, the
  IDs won't remain ordered.
  --]]
  redis.log(redis.LOG_WARNING, 'Writing ID generation lock key.')
  redis.call('PSETEX', lock_key, 1, 'lock')
elseif sequence > max_sequence then
  --[[
  Now this next block will get called on the next millisecond, and we can finally wrap the sequence around to 0 and
  continue generating IDs where we left off!
  --]]
  sequence = 0
  redis.call('SET', sequence_key, '0')
  redis.log(redis.LOG_WARNING, 'Rolling ID generation sequence back to 0.')
end

--[[
The TIME command MUST be called after anything that mutates state, or the Redis server will error the script out.
This is to ensure the script is "pure" in the sense that randomness or time based input will not change the
outcome of the writes.

See the "Scripts as pure functions" section at http://redis.io/commands/eval for more information.
--]]
local time = redis.call('TIME')

return {
  sequence, -- Doesn't need conversion, the result of INCR or the variable set is always a number.
  logical_shard_id,
  tonumber(time[1]),
  tonumber(time[2])
}
