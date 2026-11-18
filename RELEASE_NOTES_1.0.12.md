# v1.0.12

- 修复 Xaero Minimap 26.6.0 中领地高亮提示触发 AbstractMethodError 的客户端崩溃。
- 领地与选区两个小地图高亮适配器同时支持旧版及新版 tooltip 接口，保留小地图显示功能。
- 新 API 编译签名仅用于构建，不包含在发布 JAR 中，不覆盖或替换 Xaero 自身类。
- 保留 v1.0.11 的世界确认、代理通道兼容及交易安全校验；不修改 Xaero 地图配置和服务端地图隔离。

验证：完整 Java 17 / Fabric CI 通过；实际 remapped 成品的两个高亮类分别在官方 Xaero Minimap 23.9.3 和 26.6.0 隔离 JVM 中经真实父类回调验证，空与非空提示均通过。未进行真实 Minecraft 渲染或用户 Paper→Fabric 联机测试。

更新：使用新版 Xaero 的客户端应更新 Land；服务端可同步更新。本次修改集中于客户端小地图接口兼容，不改变领地数据格式。
