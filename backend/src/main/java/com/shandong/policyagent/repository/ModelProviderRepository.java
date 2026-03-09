package com.shandong.policyagent.repository;

import com.shandong.policyagent.entity.ModelProvider;
import com.shandong.policyagent.entity.ModelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModelProviderRepository extends JpaRepository<ModelProvider, Long> {

    /**
     * 根据类型查询模型列表
     */
    List<ModelProvider> findByType(ModelType type);

    /**
     * 根据类型查询模型列表（启用状态）
     */
    List<ModelProvider> findByTypeAndIsEnabled(ModelType type, Boolean isEnabled);

    /**
     * 获取某类型的默认模型
     */
    Optional<ModelProvider> findByTypeAndIsDefaultTrue(ModelType type);

    /**
     * 获取某类型的默认模型（仅启用状态）
     */
    Optional<ModelProvider> findByTypeAndIsDefaultTrueAndIsEnabledTrue(ModelType type);

    /**
     * 根据类型和服务商查询
     */
    List<ModelProvider> findByTypeAndProvider(ModelType type, String provider);

    /**
     * 取消某类型的所有默认模型
     */
    @Modifying
    @Query("UPDATE ModelProvider m SET m.isDefault = false WHERE m.type = :type")
    void clearDefaultByType(@Param("type") ModelType type);

    /**
     * 检查某类型是否存在默认模型
     */
    boolean existsByTypeAndIsDefaultTrue(ModelType type);

    /**
     * 检查某类型是否存在默认模型（仅启用状态）
     */
    boolean existsByTypeAndIsDefaultTrueAndIsEnabledTrue(ModelType type);
}