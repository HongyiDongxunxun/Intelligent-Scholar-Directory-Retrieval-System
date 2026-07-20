# 交互式学者智能目录：公开验证重构版

本仓库是依据论文描述、实验报告和重构文档重新实现的可运行原型。它保留结构化文献检索、学者发现、跨实体专题装配、证据回溯和自然语言解析确认链路，但不包含原系统前后端代码、原始数据库、真实学者实体、账号或密钥。

## 数据边界

- 运行时由固定种子代码生成 60 篇合成文献、40 名合成学者和 20 个合成机构。
- 所有名称均带“合成”标识，数据不来源于原数据库，也不连接 MySQL 或 Elasticsearch。
- 后端索引状态将 `dataMode` 明确标记为 `SYNTHETIC`。
- 论文中的 915/1,473/323 及历史性能数据仅为历史环境快照，不是本系统的验收常量。

## 技术栈

- 后端：Java 17+、Spring Boot 2.7.14、Maven
- 前端：Vue 3、Vite、Lucide
- 实验：Python 3 标准库，无额外依赖

## 快速启动

分别在两个 PowerShell 窗口中执行：

```powershell
Set-Location -LiteralPath "<repository-directory>"
powershell -ExecutionPolicy Bypass -File ".\scripts\start_backend.ps1"
```

```powershell
Set-Location -LiteralPath "<repository-directory>"
powershell -ExecutionPolicy Bypass -File ".\scripts\start_frontend.ps1"
```

随后访问：

- 前端：`http://127.0.0.1:5174`
- 后端：`http://127.0.0.1:8091`
- 健康检查：`http://127.0.0.1:8091/actuator/health`
- 索引状态：`http://127.0.0.1:8091/api/v1/system/index-status`

在对应窗口按 `Ctrl+C` 停止服务。

## 构建与测试

```powershell
Set-Location -LiteralPath ".\backend"
mvn.cmd test
mvn.cmd package

Set-Location -LiteralPath "..\frontend"
npm.cmd install
npm.cmd run build
```

后端集成测试覆盖合成索引一致性、文献与学者证据、专题公式与缓存、AI 解析确认闭环。

## 复现实验

先启动后端，再运行：

```powershell
powershell -ExecutionPolicy Bypass -File ".\scripts\run_experiments.ps1" -Workers 30 -Requests 60
```

每次运行会创建新的 `experiments/results/<run-id>/`，包含环境、请求、响应、测量、摘要、Markdown 报告和 SHA-256 校验和。合成实验只证明链路可执行、评分可审计和性能可测量，不支持检索质量优越性或生产就绪结论。

## 目录

```text
new_sys/
|-- backend/       Spring Boot API、合成数据和集成测试
|-- frontend/      Vue 操作界面
|-- data/          公开验证数据清单
|-- experiments/   E1/E2/E3 单文件运行器
|-- scripts/       构建、启动和停止脚本
`-- docs/          实现说明与接口示例
```
