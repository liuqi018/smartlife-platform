-- 1.参数列表
-- 1.1 优惠券id
local voucherId=ARGV[1]
-- 1.2 用户id
local userId=ARGV[2]
-- 修改lua脚本配合消息队列
-- 1.3 订单id
local orderId=ARGV[3]
-- 2.数据Key
-- 2.1 库存Key
local stockKey='seckill:stock:' .. voucherId
-- 2.2 订单Key
local orderKey='seckill:order:' .. voucherId
-- 3.脚本业务
-- 3.1 判断库存是否充足
local stock = tonumber(redis.call('get', stockKey) or "0")
if stock <= 0 then
	return 1
end
-- 3.2 判断用户是否下过单
if(redis.call('sismember',orderKey,userId)==1) then
	-- 3.3 存在，说明是重复下单 返回2
	return 2
end
--3.4 扣库存
redis.call('incrby',stockKey,-1)
--3.5 下单并保存用户
redis.call('sadd',orderKey,userId)
-- 修改lua脚本配合消息队列
-- 3.6 确认有资格后 发消息到队列中  orderId用id是为了和实体类中的voucher的变量名对应起来
redis.call('xadd','stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)
return 0