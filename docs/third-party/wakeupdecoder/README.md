# WakeUpDecoder 协议移植记录

- 上游：[airline233/WakeUpDecoder](https://github.com/airline233/WakeUpDecoder)
- 固定版本：`577161c9ea8c34dddbcecd6ea9a702687c4ac8c4`
- 作者：airline233；上游许可：Apache License 2.0。
- 移植日期：2026-10-06。

## 移植内容与修改

`feature/backup/courseimport/external/wakeup/WakeUpShareProtocol.kt` 改编自上游 `wakeup_share_sim.py`：保留自定义 DES 的位序、填充、PC2 特殊值、十六进制编码，以及 RC4、设备标识派生与请求签名规则。不能用标准 DES 替换。

`WakeUpShareClient.kt` 改编自 `main.py` 与协议层：使用项目已有 Ktor 客户端，向 WakeUp 官方接口获取临时令牌，再调用新版分享接口并解密 `shareData`。增加 HTTP、业务状态、数据格式与认证挑战校验，保留协程取消，不输出口令、临时令牌、设备标识或课程正文。

`BackupCoordinator.fetchWakeUpShareImport` 接入新版请求流程。解密后复用 `CourseImportParser.parseWakeUpShareData` 和现有导入预览；只有用户确认才通过原有课程写入流程入库，无数据库版本调整。

## 固定兼容参数与边界

兼容参数来源为上游参考 APK 6.1.70（versionCode 450），包括公开客户端标识、渠道和公开签名证书摘要。这些值是协议参数，不是用户 API Key 或应用签名私钥。APK 与 native 库均未复制到本项目，也不引入 Python 运行环境。

沿用上游记录的全零 Android ID 兼容配置与固定设备描述，不读取本机 Android ID，不冒用其他用户的设备标识，不注册或登录 WakeUp 账号。上游称该兼容配置当时可用；服务端是否继续接受须通过有效口令实测，拒绝时明确报错并保留文件导入入口。没有自动遍历标识、无限重试或第三方代理服务。

上游协议变化时可能需要更新固定参数或算法。自定义加密仅为兼容 WakeUp，不用于 Will do 自身的数据加密。

## 许可与署名

上游完整许可随应用资源分发：[Apache 2.0 全文](../../../app/src/main/assets/licenses/WakeUpDecoder-LICENSE.txt)；改编与来源声明见 [NOTICE](../../../app/src/main/assets/licenses/WakeUpDecoder-NOTICE.txt)。上游仓库此版本未提供独立 NOTICE，当前 NOTICE 为本地来源与修改记录。

改编源文件注明来源、固定版本及 Kotlin 移植修改。Will do 整体继续以 GPL-3.0-or-later 分发，保留本部分的 Apache 2.0 许可及相关署名。

## 验证方式

`WakeUpShareProtocolTest` 使用固定上游 Python 生成的合成课表样本，核对认证签名、DES 解密、RC4、完整请求签名与中文课表解析，并通过 Ktor MockEngine 验证认证和分享请求、错误响应及取消。不使用真实用户口令或课表作为测试数据。

本次 native 编译、架构守卫与 10 项课表导入测试通过。另以用户提供的有效口令，通过上游参考实现及已编译的 Kotlin/Ktor CIO 实现分别访问当前 WakeUp 官方接口，两者均成功取得 7 门课程、13 条上课安排；只报告数量，不保存实际课程正文或将口令写入仓库。

在线验证在电脑 JVM 上完成，不能代替 Android 引擎及手机导入预览、确认入库的真机验证；用户仍需自行构建 Release 验证。此结果只证明本次口令与当前接口兼容，不保证后续协议更新。
