# `bitkey` Android Library Module

独立 Android library 模块，负责 BitKey BLE 协议的全部 Android 端实现。它被
`android/app` 模块依赖；UI 层不要直接写 BLE / GATT 代码。

## 模块职责

| 包 | 职责 |
| --- | --- |
| `com.bitwarden.bitkey.protocol` | 帧模型 (`BitKeyFrame` / `BitKeyFlags` / `BitKeyCommands` / `BitKeyConstants`)、CRC8、`BitKeyFrameEncoder` / `BitKeyFrameParser`、高层 `BitKeyProtocol`、`BitKeyFragmenter` |
| `com.bitwarden.bitkey.connection` | `BitKeyConnectionManager` 抽象 + 默认实现 `BluetoothGattBitKeyConnectionManager`（扫描、连接、bonding、MTU、ACK 队列、断线重连） |
| `com.bitwarden.bitkey.model` | 公共数据类：`BitKeyDiscoveredDevice` / `BitKeyConnectionState` / `BitKeyAck` / `BitKeyError` |
| `com.bitwarden.bitkey.send` | `BitKeySendService`：高层 `sendPassword()`，编排 SESSION_START → TYPE_TEXT（带分片）→ SESSION_END，并把结果包装为密封的 `BitKeySendResult` |
| `com.bitwarden.bitkey.di` | Hilt 模块 `BitKeyModule`，把 `BitKeyConnectionManager` 默认绑定到生产实现 |

## 设计原则

1. **不保存密码**：模块不写 SharedPreferences / DataStore；连接状态仅保留在内存。
2. **不暴露 BLE 细节**：UI 只看到 `BitKeyConnectionManager` 抽象，测试可以注入 fake。
3. **日志不输出 payload**：`BluetoothGattBitKeyConnectionManager` 内部对 payload 做脱敏，
   仅记录字节数与命令码。
4. **ACK 必须按 SEQ 匹配**：发送操作未完成前不接受下一次输入。
5. **MTU 降级**：连接时优先请求 247 字节 MTU，协商失败时按协商值保守切分。

## 关键约束

- 蓝牙权限与 `uses-feature` 在 `:app` 模块的 `AndroidManifest.xml` 声明，不在本模块。
- Bitwarden Android 端的所有 UI 文本与图标通过 `:app` 模块的 `VaultItemLoginContent` 与
  `dialog/BitKeyConnectionDialog` 提供。
- 本模块的 `build.gradle.kts` 已经声明 Hilt、KSP、Coroutines、Timber 等依赖，不要在
  `:app` 里再重复声明。

## 测试

```bash
./gradlew :bitkey:test
```

测试范围：

- `protocol/BitKeyFrameCodecTest` — 帧编码、CRC、字段边界
- `protocol/BitKeyFrameParserTest` — 流式解码、坏帧恢复
- `protocol/Crc8Test` — CRC8 参考向量
- `protocol/BitKeyProtocolTest` — 高层命令构建器
- `protocol/BitKeyFragmenterTest` — 分片与重组
- `send/BitKeySendServiceTest` — `sendPassword()` 的端到端编排、空文本、非 ASCII、设备错误、超时

测试用 `connection/FakeBitKeyConnectionManager` 注入帧、ACK 与失败，覆盖：
发送序列、超时断线、ACK 错误码、序列化校验等关键场景。

## 如何在 UI 里调用

```kotlin
class VaultItemViewModel @Inject constructor(
    private val bitKeySendService: BitKeySendService,
) : BaseViewModel<...>(...) {

    private fun handleSendToBitKeyClick(address: String) {
        viewModelScope.launch {
            val result = bitKeySendService.sendPassword(address, password)
            // result: BitKeySendResult.Success / EmptyText / UnsupportedCharacters /
            //         ConnectionFailed / DeviceError / TimedOut
        }
    }
}
```

或者直接通过 Hilt 注入 `BitKeyConnectionManager` 来驱动扫描/连接 UI：

```kotlin
class BitKeyPickerViewModel @Inject constructor(
    private val manager: BitKeyConnectionManager,
)
```