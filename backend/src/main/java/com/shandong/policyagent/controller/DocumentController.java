package com.shandong.policyagent.controller;

import java.util.HashMap;
import java.util.Map;

import com.shandong.policyagent.rag.VectorStoreService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class DocumentController {

    private final VectorStoreService vectorStoreService;

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

    @PostMapping("/load-directory")
    public ResponseEntity<Map<String, Object>> loadFromDirectory(
            @RequestParam String path) {
        log.info("从指定目录加载文档: {}", path);
        int count = vectorStoreService.loadAndStoreFromDirectory(path);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "目录文档加载完成");
        response.put("directoryPath", path);
        response.put("chunksCount", count);

        return ResponseEntity.ok(response);
    }

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
