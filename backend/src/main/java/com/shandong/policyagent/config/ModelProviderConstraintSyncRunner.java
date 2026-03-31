package com.shandong.policyagent.config;

import java.util.Arrays;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.shandong.policyagent.entity.ModelType;

@Slf4j
@Component
@Order(120)
@RequiredArgsConstructor
public class ModelProviderConstraintSyncRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!modelProviderTableExists()) {
            return;
        }

        String constraintDefinition = findTypeConstraintDefinition();
        if (constraintDefinition == null || constraintDefinition.contains("'RERANK'")) {
            return;
        }

        log.warn("检测到 model_provider_type_check 未包含 RERANK，开始自动同步约束");
        syncTypeConstraint();
    }

    private boolean modelProviderTableExists() {
        try {
            Boolean exists = jdbcTemplate.queryForObject(
                    """
                    SELECT EXISTS (
                        SELECT 1
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name = 'model_provider'
                    )
                    """,
                    Boolean.class
            );
            return Boolean.TRUE.equals(exists);
        } catch (DataAccessException e) {
            log.warn("检查 model_provider 表是否存在时失败，跳过约束同步", e);
            return false;
        }
    }

    private String findTypeConstraintDefinition() {
        try {
            return jdbcTemplate.query(
                    """
                    SELECT pg_get_constraintdef(c.oid)
                    FROM pg_constraint c
                    JOIN pg_class t ON c.conrelid = t.oid
                    WHERE t.relname = 'model_provider'
                      AND c.conname = 'model_provider_type_check'
                    LIMIT 1
                    """,
                    rs -> rs.next() ? rs.getString(1) : null
            );
        } catch (DataAccessException e) {
            log.warn("读取 model_provider_type_check 约束定义失败，跳过约束同步", e);
            return null;
        }
    }

    private void syncTypeConstraint() {
        String allowedTypes = Arrays.stream(ModelType.values())
                .map(ModelType::name)
                .map(type -> "'" + type + "'")
                .collect(Collectors.joining(", "));

        jdbcTemplate.execute("ALTER TABLE model_provider DROP CONSTRAINT IF EXISTS model_provider_type_check");
        jdbcTemplate.execute("ALTER TABLE model_provider ADD CONSTRAINT model_provider_type_check CHECK (type IN (" + allowedTypes + "))");
        log.info("已同步 model_provider_type_check: {}", allowedTypes);
    }
}
