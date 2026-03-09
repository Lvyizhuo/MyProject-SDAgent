import React from 'react';
import './ConfigJsonTab.css';

const ConfigJsonTab = ({ config }) => {
    const displayConfig = config ? {
        runtimeStatus: {
            source: config.effectiveConfigSource,
            provider: config.effectiveModelProvider,
            apiUrl: config.effectiveApiUrl,
            modelName: config.effectiveModelName,
            temperature: config.effectiveTemperature,
            maxTokens: config.effectiveMaxTokens,
            topP: config.effectiveTopP,
            updatedAt: config.updatedAt
        },
        selectedModels: {
            llm: config.resolvedLlmModel,
            vision: config.resolvedVisionModel,
            audio: config.resolvedAudioModel,
            embedding: config.resolvedEmbeddingModel
        },
        bindings: {
            id: config.id,
            name: config.name,
            description: config.description,
            llmModelId: config.llmModelId,
            visionModelId: config.visionModelId,
            audioModelId: config.audioModelId,
            embeddingModelId: config.embeddingModelId,
            greetingMessage: config.greetingMessage,
            systemPrompt: config.systemPrompt,
            skills: config.skills,
            mcpServersConfig: config.mcpServersConfig,
            updatedAt: config.updatedAt,
            createdAt: config.createdAt
        }
    } : null;

    return (
        <div className="config-json-tab">
            <pre>
                <code>
                    {JSON.stringify(displayConfig, null, 2)}
                </code>
            </pre>
        </div>
    );
};

export default ConfigJsonTab;