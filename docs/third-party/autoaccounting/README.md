# AutoAccounting XP 源码移植记录

上游：https://github.com/AutoAccountingOrg/AutoAccounting
固定版本：4e707980be19df365d9c9a35d242115dd03d2b7f
移植日期：2026-09-19。仅涉及支付应用 XP 采集，不改 SystemUI 超级岛 Hook。

## 直接移植

工程 platform/xposed/upstream/：
- dex/ 对应上游 dex/src/main/java/net/ankio/dex/ 的 DEX 扫描器、规则模型及结果模型，共七个文件。只调整 package/import、来源注释和空白；扫描算法不变。
- LuckMoneyModel.kt 对应上游微信红包规则。保留包名正则、两个 int 加八个 String 的构造签名以及 void onGYNetEnd(int,String,JSONObject)；去掉上游全局 HookerClazz 依赖，将规则身份改为固定字符串。
- 使用与上游相同的 org.smali:dexlib2:2.5.2，其许可为 BSD-3-Clause；依赖按 Gradle 正常传递解析。

## 适配到现有工程的部分

- WechatHookAdaptation.kt 来自 core/utils/AdaptationUtils.kt、core/api/HookerClazz.kt 的版本/规则缓存与类映射流程。上游存储服务改为宿主私有的 willdo_payment_adaptation SharedPreferences，只保存本模块生成的缓存。只注册现阶段需要的红包规则。
- PaymentCaptureHook.kt 的生命周期采用 core/App.kt：仅主进程，在 Instrumentation.callApplicationOnCreate 返回后安装；不再在 Application.attach 安装，不监听全局 ClassLoader.loadClass。
- 微信采集入口对齐 DatabaseHooker 的 WCDB insert、WebViewHooker 的 evaluateJavascript、RedPackageHooker 的精确模型回调；移除之前自行扩展的 compat/update 和 tools/toolsmp 入口。
- 支付宝采用 MessageBoxHooker 的 getData/toString 入口及解码数组后逐条发送；保留原有的递归保护。
- 上游 analysisData 替换为现有 PaymentCaptureProvider；继续使用本地解析、统一入库和去重。仅转发现有解析器支持的微信交易类型，未移植联系人读取、支付金额缓存、群收款及发红包推测。
- 保留本项目的数据大小上限、宿主参数不变和日志脱敏约束。上游示例支付正文与调试正文不复制进项目。
- 上游缓存未命中时后台适配、存储；上游随后会重启宿主，本项目不主动重启，等待下次正常冷启动安装红包 Hook。固定数据库/详情入口独立安装，红包适配失败不阻塞它们。
- 不移植 Tinker 补丁清理、杀进程、网络安全修改、Toast 服务和远程规则执行器。
- 与上游一样，支付总开关控制消息处理；启用 Xposed 作用域后仍会安装入口。关闭总开关不能替代取消作用域；本轮未宣称改变该行为。

## 许可与署名

上游仓库根目录采用 GPL-3.0，原始完整许可保存在 [LICENSE](LICENSE)，与本仓库 GPLv3 分发一并提供。
保留所取源文件原始版权及许可头。部分上游头注写作不存在的“Apache License, Version 3.0”，与根许可证不一致；此处如实保留，不将其擅自改写或宣称为 Apache-2.0。本次源码使用依据为上游仓库根 GPL-3.0 授权。
直接移植与改编代码均保留上游来源、作者及本地修改说明。

## 验证边界

本地规则测试及编译不能证明微信 8.0.49 白屏已修复。需要用户自行构建 Release，验证冷启动、支付和红包缓存适配，具体见 [自动记账验证](../../automatic-accounting-testing.md)。
