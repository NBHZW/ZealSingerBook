-- collection收藏布隆过滤器添加数据lua脚本
local key = KEYS[1]  -- collection收藏布隆过滤器的redisKey  bloom:note:collects:#{userId}
local noteId = ARGV[1] -- value 对应noteId
local expireSeconds = ARGV[2] --过期时间

redis.call('BF.ADD',key,noteId)
redis.call('EXPIRE',key,expireSeconds)  -- 设置过期时间
return 0