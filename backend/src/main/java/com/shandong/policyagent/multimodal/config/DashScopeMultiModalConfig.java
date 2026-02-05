package com.shandong.policyagent.multimodal.config;

import com.alibaba.dashscope.utils.Constants;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "dashscope.multimodal")
public class DashScopeMultiModalConfig {

    private String apiKey;
    private String baseUrl = "https://dashscope.aliyuncs.com/api/v1";
    private Asr asr = new Asr();
    private Vision vision = new Vision();

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("DASHSCOPE_API_KEY");
        }
        Constants.baseHttpApiUrl = baseUrl;
    }

    @Getter
    @Setter
    public static class Asr {
        private String model = "qwen3-asr-flash";
        private String longAudioModel = "qwen3-asr-flash-filetrans";
        private boolean enableItn = false;
        private String language = "zh";
    }

    @Getter
    @Setter
    public static class Vision {
        private String model = "qwen-vl-plus";
    }
}
