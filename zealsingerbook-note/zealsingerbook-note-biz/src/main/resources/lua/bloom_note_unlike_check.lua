-- LUA 脚本：点赞布隆过滤器

local key = KEYS[1] -- 操作的 Redis Key
local noteId = ARGV[1] -- 笔记ID

-- 使用 EXISTS 命令检查布隆过滤器是否存在
local exists = redis.call('EXISTS', key)
if exists == 0 then
    return -1
end

-- 校验该篇笔记是否被点赞过(1 表示已经点赞，0 表示未点赞)
local isLiked = redis.call('BF.EXISTS', key, noteId)
-- 如果为1 则说明已经点赞 可以返回了 因为布隆一般不进行删除操作  如果为0则说明不存在 确实没点赞
if isLiked == 0 then
    return 1
end

return 0
