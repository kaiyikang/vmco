# VMCO

语言：[English](README.md) | [Deutsch](README.de.md) | 中文

当 IntelliJ Copilot 插件无法自行收集外部仓库、差异或工单上下文时，VMCO 在
IDE 外生成给 Copilot Agent 读取的 Prompt 文件。

## PR Reviewer

在需要分析的项目根目录中，调用 VMCO 脚本：

```bash
/path/to/vmco/bin/create-llm-review-diffs.sh [base-branch] [output-dir]
```

默认值：

- `base-branch`: `master`
- `output-dir`: `.llm`
- 输出文件：`<output-dir>/review-instruction.md`

生成后，在 IntelliJ Copilot 中切换到 Agent 模式，让 Copilot 先读取输出文件并按其中约束执行。

## Context For JIRA

先在环境变量或目标仓库根目录 `.env` 文件中配置 `JIRA_TOKEN` 和
`JIRA_URL_TEMPLATE`。URL 模板必须包含 `{ticket}`。

```bash
python3 /path/to/vmco/bin/context-for-jira.py <ticket-id>
```

脚本会写入 `.llm/context-for-jira-{ticket}-{timestamp}.md`。

## 设计原则

- 脚本在 IDE 外收集事实，Copilot 在仓库内继续分析。
- 生成的 Prompt 保持单文件、可追溯、约束明确。
- 默认引用大段 diff，而不是直接嵌入全文。
- 需要稳定审查历史的 Prompt 使用版本化模板。
