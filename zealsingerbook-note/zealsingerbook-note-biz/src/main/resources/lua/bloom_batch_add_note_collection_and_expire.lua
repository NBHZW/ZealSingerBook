-- 笔记收藏布隆过滤器的初始化lua脚本
local key = KEYS[1]  -- 操作的key

-- 遍历每一个元素 即遍历noteIdList中的每一个noteId加入到布隆过滤器中
for i = 1 , #ARGV - 1 do
    redis.call('BF.ADD',key,ARGV[i])
end

local expireTime = ARGV[#ARGV]  --最后一个元素为过期时间
redis.call('EXPIRE',key,expireTime)
return 0