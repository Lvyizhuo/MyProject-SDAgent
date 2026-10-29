# scripts

本目录存放**一次性/辅助**脚本，不属于应用运行时依赖。应用代码在 `backend/` 与 `frontend/`。

| 路径 | 用途 | 使用方式 |
| :--- | :--- | :--- |
| `scrape_shandong.py` | 抓取山东省商务厅政策原文，产出 `data/scraped/{policies,provinces}.json` | `python3 scrape_shandong.py`（需自行确认目标站点可访问） |
| `chat-module-integration-test.sh` | 对话模块手工冒烟脚本，逐个调用 REST 接口并打印结果 | 先启动后端，再 `bash chat-module-integration-test.sh` |
| `data/scraped/` | 上述爬虫的输出产物，供 `app.rag.scraped.policy-json-paths` 读取 | 只读数据，请勿手工编辑 |

## 注意

- 这里的脚本**不参与** CI，也没有自动化测试覆盖。
- `data/scraped/policies.json` 体积较大（约 15 MB）。如需调整数据，请直接替换文件而不是在仓库里累积多份副本
  （历史上前缀重复的 `scripts/scripts/` 目录已清理）。
- 抓取目标站点若有反爬或结构变更，脚本需要相应调整，不属于稳定接口。
