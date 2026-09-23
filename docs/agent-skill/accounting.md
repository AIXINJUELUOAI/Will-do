# 记账

先读 protocol.md 并确认 getCapabilities.methods 包含对应方法。无需开启“允许 Agent 操作数据库”；正常 Agent 权限与第三方访问权限即可。

## 单位和边界

- 账单时间为 Unix 毫秒，不能使用日程接口的 Unix 秒。查询区间 [startMs, endMs)，包含开始、不含结束；按用户时区计算当天/周/月起止。
- 输入 amount 为正数十进制元字符串，最多两位小数，例如 "35.20"；输出 amountMinor 及汇总字段均为整数分，显示时除以 100。不要传浮点金额或负金额。
- EXPENSE 为支出，INCOME 为收入（包括已到账退款），TRANSFER 为不计收支。
- 新增 currency 默认为 CNY，只支持 CNY；传其他币种会报错，不自动换算。查询和汇总可包含其他币种，但必须分币种展示，不直接相加。
- 缺少时间的当前随口记账（如“午饭花了35块”）可省略 occurredAt，服务端使用当前时间。历史账单、详情页和退款必须提供有依据的交易时间；未知时先询问，不省略时间制造当前记录。
- hasAttachment 只表示关联截图是否存在；本 skill 不提供附件内容、修改或删除。

## 接口

| 方法 | payload | 结果 |
| --- | --- | --- |
| createBill | {"bill":{...}} | {status,bill?,draftId?} |
| getBill | {"id":"账单ID"} | 单笔账单 |
| queryBills | {"filter":{...},"offset":0,"limit":200,"includePending":false} | {bills,total,nextOffset} |
| updateBill | {"id":"账单ID","patch":{...}} | 修改后的账单 |
| deleteBill | {"id":"账单ID"} | {completed:true} |
| getBillSummary | {...筛选字段...} | 按币种分组的汇总数组 |

筛选字段（全部可选）：startMs、endMs、direction、category、currency、text。分类与币种精确匹配；text 搜索名称、分类、备注或交易号。

查询按交易时间倒序、同时间按 ID 排序。limit 为 1–200，offset 非负；需要更多记录时使用 nextOffset，为空则结束。数据在分页期间发生变化时重新查询。total 为筛选后的完整条数。

查询默认只包含已确认账单；includePending:true 额外包含已导入但待核对的账单，**不包含识别草稿**。删除账单不再返回。

### 新增

必填 amount、direction；可选：

- category：默认“未分类”。
- merchant：名称或交易对方，不提供则沿用分类。
- note：备注。
- occurredAt：交易时间（毫秒）。
- zoneId：IANA 时区；默认手机时区。
- channel：已知支付渠道，如“微信支付”“支付宝”。
- transactionId：有依据的交易号。
- transactionIdType：PAYMENT、MERCHANT_ORDER、UNKNOWN，默认 UNKNOWN，不推测号码类型。

用户输入“午饭花了35块”时的 payload：

    {"bill":{"amount":"35.00","direction":"EXPENSE","category":"餐饮"}}

新增结果必须按 status 解释：

- SAVED：已入库，bill 携带新账单；使用其 ID 读回验证。
- DUPLICATE：重复账单，本次没有新入库；不要重试新增。
- SUSPECTED_DUPLICATE：疑似重复，未入库；提示用户在 Will do 待确认账单中核对，draftId 不是账单 ID。
- PENDING：待核对，未入库；同样引导到应用确认。

协议 success:true 只表示请求被正确处理，不代表账单一定保存成功。不得通过改时间、改交易号或通用数据库绕过去重。

### 修改、删除

先 getBill 查询原账单，确认目标；删除按 SKILL.md 的授权约定执行。

updateBill 为局部更新，至少提供一个字段：amount、direction、category、merchant、note、occurredAt。省略字段保留原值；空 note 清空备注。不支持修改币种、渠道、交易号、来源、核对状态、去重标识或截图；这些数据由原账单保留。

    {"id":"实际账单ID","patch":{"category":"餐饮","note":"和同事午饭"}}

删除使用软删除，不会因再次查询而复活。删除后 getBill 应返回 NOT_FOUND。

### 汇总

金额问题优先使用 getBillSummary，不要只把第一页账单相加。直接将筛选字段作为 payload，不包裹 filter。

每个币种返回 currency、count、expenseMinor、incomeMinor、transferMinor、balanceMinor。收支差额为收入减支出；TRANSFER 单列，不参与差额。只统计已确认、未删除账单，覆盖全部匹配记录；不受分页影响。返回空数组代表没有匹配的已确认账单。

## Awill 与第三方调用

此 skill 使用普通 Shell 广播。Awill 内置 willdo_bills 则通过官方连接调用同一组接口：action 为 query/get/create/update/delete/summary；summary 在工具参数中使用 filter，工具负责转换为服务端 payload。两种路径共用入库与去重。
