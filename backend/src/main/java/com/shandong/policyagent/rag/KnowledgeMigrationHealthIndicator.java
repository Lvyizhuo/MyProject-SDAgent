package com.shandong.policyagent.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KnowledgeMigrationHealthIndicator implements HealthIndicator {

    private final KnowledgeMigrationState migrationState;

    @Override
    public Health health() {
        if (migrationState.isPending()) {
            return Health.outOfService()
                    .withDetail("knowledgeMigration", migrationState.getSummary())
                    .build();
        }
        if (migrationState.isFailed()) {
            return Health.outOfService()
                    .withDetail("knowledgeMigration", migrationState.getSummary())
                    .build();
        }
        return Health.up()
                .withDetail("knowledgeMigration", migrationState.getSummary())
                .build();
    }
}
