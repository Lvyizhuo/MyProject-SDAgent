package com.shandong.policyagent.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagBootstrapRunner implements ApplicationRunner {

    private final RagConfig ragConfig;
    private final VectorStoreService vectorStoreService;

    @Override
    public void run(ApplicationArguments args) {
        if (!ragConfig.getBootstrap().isAutoLoadOnStartup()) {
            log.info("已关闭启动自动入库，跳过默认文档加载");
            return;
        }

        try {
            log.info("启动自动入库已开启，开始加载默认文档与爬虫文档");
            int chunks = vectorStoreService.loadAndStoreAllDocuments();
            log.info("启动自动入库完成，新增向量切片 {} 条", chunks);
        } catch (Exception e) {
            log.error("启动自动入库失败，请检查数据库与模型配置", e);
        }
    }
}
