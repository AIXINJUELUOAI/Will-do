# 记账业务参考项目调研

核查日期：2026-09-16。本轮只调研，没有改业务代码或引入第三方源码。当前仓库根 LICENSE 为 GPLv3。

## 推荐组合

本轮未确认单个项目同时满足无障碍、OCR 识屏、正则/AI、微信支付宝文件导入四项且许可可直接兼容现有 GPLv3。建议按能力组合参考。

| 项目 | 核实能力 | 维护证据（默认分支，UTC） | 许可 |
| --- | --- | --- | --- |
| [AutoAccounting](https://github.com/AutoAccountingOrg/AutoAccounting) | 无障碍、OCR、规则/正则及 AI；另有 Root/Shizuku/Xposed 可选模式。官方微信/支付宝 CSV/XLSX 文件导入未确认 | 2026-09-14 修复一木同步与转账还款，9 月还有导出功能提交 | GPL-3.0 |
| [Veri Fin](https://github.com/LumiDesk/verifin) | 本机中文 ML Kit OCR + AI；微信 XLSX、支付宝 CSV、导入预览和测试。本体明确不监听屏幕/通知，是分享或选图入口 | 2026-09-16 v1.18.6，新增不计入预算标记 | GPL-3.0-or-later |
| [double-entry-generator](https://github.com/deb-sig/double-entry-generator) | Go 规则驱动导入器；微信 CSV/XLSX、支付宝 CSV、多来源转换；无 Android 采集 | 默认分支 2026-06-03 新增 runtime template provider；仓库 pushed_at 的 8 月日期不是默认分支代码日期 | Apache-2.0 |
| [小遥账单](https://github.com/dtsola/xiaoyaoprivatebill) | Python 微信 CSV/XLSX、支付宝 CSV 解析；无 Android 无障碍/OCR 链路证据 | 仓库 2026-08-24 为文档/配置更新；微信解析修复 2026-04-03 | MIT |

建议 AutoAccounting 参考自动采集，Veri Fin 参考导入预览与确认，double-entry-generator 参考格式/规则与样例，小遥补充微信兼容问题。

## 源码与证据

### AutoAccounting

- [README](https://github.com/AutoAccountingOrg/AutoAccounting/blob/master/README.md)
- [无障碍服务](https://github.com/AutoAccountingOrg/AutoAccounting/blob/master/app/src/main/java/com/google/android/accessibility/selecttospeak/SelectToSpeakService.kt)：AccessibilityService，Android 11+ 截图能力说明。
- [OCR 服务](https://github.com/AutoAccountingOrg/AutoAccounting/blob/master/app/src/main/java/net/ankio/auto/service/OcrService.kt)
- [规则样例](https://github.com/AutoAccountingOrg/AutoAccounting/tree/master/app/src/main/assets/rule)
- [提交](https://github.com/AutoAccountingOrg/AutoAccounting/commits/master/) / [LICENSE](https://github.com/AutoAccountingOrg/AutoAccounting/blob/master/LICENSE)

### Veri Fin

- [微信解析](https://github.com/LumiDesk/verifin/blob/main/lib/app/backup/import/wechat.dart)：动态查找交易时间/收支表头、Excel 序列日期、中性交易。
- [支付宝解析](https://github.com/LumiDesk/verifin/blob/main/lib/app/backup/import/alipay.dart)：GBK、前置说明行与表头检测。
- [本地 OCR](https://github.com/LumiDesk/verifin/blob/main/lib/app/screenshot_recognizer_io.dart)：ML Kit Chinese，图像本地识别；识别文本交配置的 AI 解析。
- [AI 解析](https://github.com/LumiDesk/verifin/blob/main/lib/app/ai/ai_entry_parser.dart)
- [导入预览](https://github.com/LumiDesk/verifin/blob/main/lib/pages/import_preview_page.dart) / [测试](https://github.com/LumiDesk/verifin/blob/main/test/payment_import_test.dart)
- [提交](https://github.com/LumiDesk/verifin/commits/main/) / [LICENSE](https://github.com/LumiDesk/verifin/blob/main/LICENSE)

### double-entry-generator

- [微信 provider](https://github.com/deb-sig/double-entry-generator/tree/master/pkg/provider/wechat) / [支付宝 provider](https://github.com/deb-sig/double-entry-generator/tree/master/pkg/provider/alipay)
- [微信 CSV/XLSX 样例](https://github.com/deb-sig/double-entry-generator/tree/master/example/wechat)
- [导入器与测试](https://github.com/deb-sig/double-entry-generator/tree/master/pkg/importer)
- [提交](https://github.com/deb-sig/double-entry-generator/commits/master/) / [LICENSE](https://github.com/deb-sig/double-entry-generator/blob/master/LICENSE)

### 小遥账单

- [微信解析器](https://github.com/dtsola/xiaoyaoprivatebill/blob/main/backend/parsers/wechat.py)
- [2026-04-03 修复](https://github.com/dtsola/xiaoyaoprivatebill/commit/70814b81c48c6696cc8702a4da00e0e4417141db)：动态找表头，不再固定第 17 行，兼容金额列名差异。
- 解析器包含把退款/关闭/撤销统一转为负金额的简化逻辑，不可直接作为我们的交易语义标准。
- [LICENSE](https://github.com/dtsola/xiaoyaoprivatebill/blob/main/LICENSE)

## 排除和保留观察

- [BeeCount](https://github.com/TNT-Likely/BeeCount)：功能声明覆盖四项，9 月仍更新；但[当前 LICENSE](https://github.com/TNT-Likely/BeeCount/blob/main/LICENSE)是带商业限制的自定义许可，README 称 BSL。不能当作 MIT 或仅因免费/源码公开就直接并入 GPLv3；仅借鉴交互思路，若要源码需额外兼容授权。
- [auto_bookkeeper](https://github.com/ZhiQing456/auto_bookkeeper)：Apache-2.0，微信通知/无障碍文本/CSV；2026-09-13 仅首次公开提交，不能证明持续维护。支付宝仍待完善，未确认 OCR。其 README 对 BeeCount、double-entry-generator 的许可描述与当前上游不一致，必须以上游 LICENSE 为准。
- [Cashbook](https://github.com/WangJie0822/Cashbook)：Apache-2.0，近期主要可见依赖升级；通用 CSV 进出不能等同微信支付宝官方文件导入，本轮不作为四项需求首选。

## 后续实现边界

- GPLv3、Apache-2.0、MIT 可按各自条件用于当前 GPLv3 项目；保留版权、许可及修改说明，逐项核对所取文件、依赖与 OCR 模型权重许可。
- 功能声明、源码与测试文件存在，不代表用用户真实账单测试通过。本轮未运行候选项目，也未诊断旧微信导入失败根因。
- 优先对照文件格式、编码/BOM、动态表头、金额和 Excel 日期、交易状态、交易单号去重，再考虑移植逻辑。退款、撤销、失败和中性交易分别建模。
- 无障碍/OCR 与账单文件解析为不同入口，最终走项目已有 RecognitionApi / IngestCommandApi 契约，不另建平行入库主链路。


## 本轮落地（2026-09-16）

- 依据格式参考独立实现 `AccountingFileParser`，未复制上游源文件或引入解析库。
- 微信 CSV/XLSX、支付宝 CSV：动态表头、UTF-8/GB18030/UTF-16、Excel 1900/1904 日期、共享字符串与内联字符串，CSV/XLSX 错误行保留原始行号。
- 系统文件选择 → 本机解析和预览 → 确认 → `IngestCommandApi` → Room 事务；同平台交易号与收支方向组成去重标识，兼容旧测试版。无交易号时用交易内容生成稳定标识，内容完全相同的无单号记录可能合并。
- 沿用数据库 17 和现有 `accounting_entries` 表，只增加 DAO，无数据库结构迁移。
- 删除页面写死账单，日/周/月统计、图表及今日/全部页金额使用真实数据；顶部卡片改为真实周期摘要，未接消费建议分析。

- 浮动按钮为 +，点击新建账单、长按打开 FloatingActionCard 导入菜单；条目左滑编辑/删除，动作按钮与日程共用圆角背景组件。手动新增/编辑/软删除经统一入库契约保存；编辑保留导入身份与核对状态，重新导入不会覆盖手动编辑或恢复已删除记录。导出、自动识别、云同步不在本轮内。
- 失败/关闭等未完成交易跳过；中性交易保留且不计收支。支出含退款状态、外币记录标为待核对并排除统计；未实现退款金额抵扣、汇率换算或手动核对，统计不应当作平台原始账单净额。
- ZIP 外层包需先解压；旧 XLS、加密表格不支持。文件资源上限登记在 `ConfigCatalog`。
- JVM 用例覆盖格式解析、稳定去重标识和真实统计；另有使用独立内存数据库的 Android 测试覆盖重复导入、删除标记、退款状态更新和整批失败回滚，尚未在设备运行。

### 编译后建议验收

1. 分别导入微信 CSV/XLSX、支付宝 CSV；预览商户、金额、日期与原文件一致后确认，检查页面跳转至最新账单日期。
2. 原文件再导入一次：新增应为 0，重复数与有效记录数一致；退出并重新打开软件，账单仍存在。
3. 切换日/周/月，确认统计与首页金额联动；中性和待核对记录可查看详情，但不进入支出、入账或图表。
4. 取消预览应不写入；格式错误或只有无效行时不应显示导入成功。开启壁纸与玻璃后检查导入 Sheet 和系统文件选择返回。

用户已反馈账单导入正常；本轮补充手动新建/编辑/删除及金额校验测试。Gradle 编译、架构守卫和装机按用户要求留给用户执行。

### 后续补齐：收支建议卡

- 恢复预览阶段的十二类文案：购物较多、购物增加、餐饮占比、外卖频次、小额累计、大额支出、同期减少、同期平稳、向同人转账、收到同人转账、报销到账和空态。改为按当前日/周/月的真实账单本地计算，不请求 AI，不显示虚构姓名或金额。
- 多条命中时点击卡片切换，固定优先级，不自动轮播和播放切换动画；正文保持三行空间，数据不足时显示实际收支摘要。
- 只使用已确认的人民币收入和支出，排除待确认、不计收支及未来记录；转账、外卖、报销需要分类或文本中的明确标记，不仅凭商户名称猜测。
- 首版门槛在 ConfigCatalog 集中登记：频繁及同期比较至少 3 笔；购物/餐饮占比 40%；同期变化 20%，平稳范围 10%；小额单笔不超过 20 元且至少 5 笔；大额单笔至少 500 元。
- 同期按双方相同的已过天数比较，月长不同时取双方都有的天数；历史周期使用对应过去周期的文案，不称为本周/本月。提示仅描述已记录的数据，不代表用户完整流水或预算判断。
