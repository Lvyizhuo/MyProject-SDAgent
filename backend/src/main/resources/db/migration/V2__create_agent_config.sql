-- 创建 agent_config 表
CREATE TABLE IF NOT EXISTS agent_config (
    id BIGSERIAL PRIMARY KEY,

    -- 基础信息
    name VARCHAR(100) NOT NULL DEFAULT '政策问答智能体',
    description TEXT DEFAULT '用于山东省以旧换新补贴政策咨询',

    -- AI 模型配置
    model_provider VARCHAR(50) NOT NULL DEFAULT 'dashscope',
    api_key VARCHAR(255) DEFAULT '${DASHSCOPE_API_KEY}',
    api_url VARCHAR(255) DEFAULT 'https://dashscope.aliyuncs.com/compatible-mode',
    model_name VARCHAR(100) NOT NULL DEFAULT 'qwen3.5-plus',
    temperature DECIMAL(3,2) DEFAULT 0.70,

    -- 系统提示词
    system_prompt TEXT NOT NULL,

    -- 开场白
    greeting_message TEXT DEFAULT '您好！我是山东省以旧换新政策咨询智能助手。您可以问我关于汽车、家电、数码产品等的补贴标准和申请流程。

**我可以帮您：**
- 查询各类产品补贴金额
- 了解申请条件和流程
- 计算您能获得的补贴
- 解答政策相关疑问',

    -- 技能模块配置
    skills JSONB NOT NULL DEFAULT '{
        "webSearch": {"enabled": true},
        "subsidyCalculator": {"enabled": true},
        "fileParser": {"enabled": true}
    }'::jsonb,

    -- MCP 服务器配置
    mcp_servers_config JSONB DEFAULT '[]'::jsonb,

    -- 时间戳
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- 唯一约束（确保只有一个配置）
    CONSTRAINT single_agent CHECK (id = 1)
);

-- 插入默认配置（仅当表为空时）
INSERT INTO agent_config (id, name, description, model_provider, api_key, api_url, model_name, temperature, system_prompt, greeting_message, skills, mcp_servers_config)
SELECT 1,
    '政策问答智能体',
    '用于山东省以旧换新补贴政策咨询',
    'dashscope',
    '${DASHSCOPE_API_KEY}',
    'https://dashscope.aliyuncs.com/compatible-mode',
    'qwen3.5-plus',
    0.70,
    '你是一个专业的山东省以旧换新补贴政策咨询助手。你的任务是：

1. 准确理解用户关于补贴政策的咨询问题
2. 基于提供的政策文档内容回答问题
3. 当信息不足时，可以主动询问用户更多细节
4. 对于补贴金额计算，使用提供的工具进行精确计算
5. 保持回答准确、客观、易于理解

请始终基于提供的事实依据回答，不要编造信息。',
    '您好！我是山东省以旧换新政策咨询智能助手。您可以问我关于汽车、家电、数码产品等的补贴标准和申请流程。

**我可以帮您：**
- 查询各类产品补贴金额
- 了解申请条件和流程
- 计算您能获得的补贴
- 解答政策相关疑问',
    '{
        "webSearch": {"enabled": true},
        "subsidyCalculator": {"enabled": true},
        "fileParser": {"enabled": true}
    }'::jsonb,
    '[]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM agent_config WHERE id = 1);
