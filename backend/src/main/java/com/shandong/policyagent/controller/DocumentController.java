package com.shandong.policyagent.controller;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.shandong.policyagent.exception.BusinessException;
import com.shandong.policyagent.rag.VectorStoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "文档管理", description = "知识库文档加载与删除（开发调试用）")
public class DocumentController {

    private final VectorStoreService vectorStoreService;

    /**
     * 目录导入白名单根目录：防止 path 参数穿越到服务器任意目录。
     * 默认 data 与 app.rag.document-path 保持一致，可用 APP_KNOWLEDGE_IMPORT_ROOT 覆盖。
     */
    @Value("${app.knowledge.import-root:data}")
    private String importRoot;

    @Operation(summary = "加载默认文档目录", description = "加载默认文档目录下的所有政策文档并入库")
    @PostMapping("/load")
    public ResponseEntity<Map<String, Object>> loadDefaultDocuments() {
        log.info("开始加载默认文档目录");
        int count = vectorStoreService.loadAndStoreAllDocuments();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "文档加载完成");
        response.put("chunksCount", count);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "加载指定目录文档", description = "从知识库根目录内的指定子目录加载文档并入库（开发调试用）")
    @PostMapping("/load-directory")
    public ResponseEntity<Map<String, Object>> loadFromDirectory(
            @RequestParam String path) {
        Path target = Paths.get(path).toAbsolutePath().normalize();
        // 路径白名单：规范化后必须位于知识库根目录内，防止穿越读取服务器任意目录
        Path root = Paths.get(importRoot).toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            log.warn("拒绝越界目录导入请求: {}（根目录: {}）", target, root);
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "路径越界：仅允许加载知识库根目录内的文档目录");
        }
        log.info("从指定目录加载文档: {}", target);
        // 实际加载使用校验后的规范化绝对路径，避免加载与校验对象不一致
        int count = vectorStoreService.loadAndStoreFromDirectory(target.toString());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "目录文档加载完成");
        response.put("directoryPath", target.toString());
        response.put("chunksCount", count);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "删除文档", description = "按文档 ID 批量删除已入库文档（开发调试用）")
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteDocuments(
            @RequestParam java.util.List<String> ids) {
        log.info("删除文档: {}", ids);
        vectorStoreService.deleteDocuments(ids);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "文档删除完成");
        response.put("deletedCount", ids.size());

        return ResponseEntity.ok(response);
    }
}
