package com.shandong.policyagent.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class KnowledgeBaseInitStatusConverter implements AttributeConverter<KnowledgeBaseInitStatus, String> {

    @Override
    public String convertToDatabaseColumn(KnowledgeBaseInitStatus attribute) {
        return attribute != null ? attribute.name() : null;
    }

    @Override
    public KnowledgeBaseInitStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }

        String normalized = dbData.trim();
        if (normalized.isEmpty()) {
            return KnowledgeBaseInitStatus.READY;
        }

        try {
            return KnowledgeBaseInitStatus.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return KnowledgeBaseInitStatus.READY;
        }
    }
}
