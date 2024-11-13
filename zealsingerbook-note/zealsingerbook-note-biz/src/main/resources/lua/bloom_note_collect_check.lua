--Lua脚本 笔记收藏布隆过滤器 添加并且检测是否已经收藏
local key = KEYS[1] --布隆过滤器主键 bloom:note:collects:#{userId}
local noteId = ARGV[1]  --对应笔记ID

-- 检测布隆过滤器是否存在
local exists = redis.call('EXISTS',key)
if exists == 0 then
    return -1
end

-- 检测该笔记是否被收藏过 已经收藏返回1
local isCollected = redis.call('BF.EXISTS',key,noteId)
if isCollected == 1 then
    return 1
end

-- 到这里说明布隆存在 且 数据没有被收藏(布隆过滤器存在但是过滤器中没有这个数据) 那么直接添加并且返回0
redis.call('BF.ADD',key,note)
return 0

