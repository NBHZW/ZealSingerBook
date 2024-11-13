-- 添加新元素到笔记收藏的zset中
local key = KEYS[1]  --操作的笔记收藏zset的key  对应 user:note:collects:#{userId}
local noteId = ARGV[1] --收藏的笔记ID
local timestamp = ARGV[2] --时间戳

-- 先检测是否存在
local exists = redis.call('EXISTS',key)
if exists == 0 then
    return -1
end

-- 检测数量是否有300
local size = redis.call('ZCARD',key)
if size >=300 then
    redis.call('ZPOPMIN',key)
end

--添加新元素进去
redis.call('ZADD',key,timestamp,noteId)
return 0