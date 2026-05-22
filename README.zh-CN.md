# VMCO

语言：[English](README.md) | [Deutsch](README.de.md) | 中文

VMCO 为 IntelliJ Copilot 生成可读取的上下文 Prompt。

当 Copilot 插件无法直接调用外部工具时，VMCO 在 IDE 外收集仓库、分支差异或工单信息，输出给 Copilot Agent 读取的 Prompt 文件。

## 快速开始

在需要分析的项目根目录中，调用 VMCO 脚本：

```bash
/path/to/vmco/bin/create-llm-review-diffs.sh [base-branch] [output-dir]
```

默认值：

- `base-branch`: `master`
- `output-dir`: `.llm`
- 输出文件：`.llm/review-instruction.md`

生成后，在 IntelliJ Copilot 中切换到 Agent 模式，让 Copilot 先读取输出文件并按其中约束执行。

## 用例

- PR Reviewer：生成用于 Pull Request Review 的上下文 Prompt。
- Context for JIRA：从 JIRA 工单生成上下文收集 Prompt。

## 设计原则

- 外部脚本负责收集事实和生成 Prompt。
- Copilot 负责读取 Prompt 并在仓库内继续分析。
- Prompt 应保持单文件、可追溯、约束明确。
- 默认避免写入大段 diff，减少上下文浪费。
- Prompt 应带版本信息。
