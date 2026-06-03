package com.simon.campus.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.simon.campus.mapper.SystemConfigMapper;
import com.simon.campus.model.entity.SystemConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * System configuration service with DB persistence + in-memory cache.
 * Startup: loads DB → merges defaults from yml.
 * Runtime: DB is source of truth.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigService {

    private final SystemConfigMapper mapper;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    // Defaults injected from application.yml
    @Value("${models.intent-router.model:qwen-turbo}")      private String defaultIntentModel;
    @Value("${models.query-rewriter.model:qwen-plus}")      private String defaultRewriteModel;
    @Value("${models.rag-generator.model:qwen-max}")        private String defaultRagModel;
    @Value("${models.tool-caller.model:qwen-plus}")         private String defaultToolModel;
    @Value("${models.chitchat.model:qwen-turbo}")           private String defaultChitchatModel;
    @Value("${models.intent-router.temperature:0.1}")       private String defaultIntentTemp;
    @Value("${models.intent-router.max-tokens:16}")         private String defaultIntentMaxTokens;
    @Value("${models.query-rewriter.temperature:0.2}")      private String defaultRewriteTemp;
    @Value("${models.query-rewriter.max-tokens:512}")       private String defaultRewriteMaxTokens;
    @Value("${models.rag-generator.temperature:0.2}")       private String defaultRagTemp;
    @Value("${models.rag-generator.max-tokens:2048}")       private String defaultRagMaxTokens;
    @Value("${models.tool-caller.temperature:0.1}")         private String defaultToolTemp;
    @Value("${models.tool-caller.max-tokens:1024}")         private String defaultToolMaxTokens;
    @Value("${models.chitchat.temperature:0.7}")            private String defaultChitchatTemp;
    @Value("${models.chitchat.max-tokens:1024}")            private String defaultChitchatMaxTokens;

    @PostConstruct
    public void init() {
        Map<String, String[]> defaults = buildDefaults();
        // Seed cache with compiled defaults first so the app can start without DB
        defaults.forEach((k, v) -> cache.putIfAbsent(k, v[0]));

        try {
            // Load all from DB into cache (DB values override compiled defaults)
            mapper.selectList(null).forEach(c -> cache.put(c.getConfigKey(), c.getConfigValue()));

            // Persist any missing defaults to DB
            for (Map.Entry<String, String[]> entry : defaults.entrySet()) {
                String key = entry.getKey();
                String[] valueDesc = entry.getValue();
                if (mapper.selectCount(new LambdaQueryWrapper<SystemConfig>()
                        .eq(SystemConfig::getConfigKey, key)) == 0) {
                    SystemConfig cfg = new SystemConfig();
                    cfg.setConfigKey(key);
                    cfg.setConfigValue(valueDesc[0]);
                    cfg.setConfigType(valueDesc.length > 2 ? valueDesc[2] : "STRING");
                    cfg.setDescription(valueDesc[1]);
                    cfg.setUpdatedAt(LocalDateTime.now());
                    try {
                        mapper.insert(cfg);
                    } catch (Exception e) {
                        log.debug("Config {} already exists", key);
                    }
                }
            }
            log.info("SystemConfigService initialized from DB with {} configs", cache.size());
        } catch (Exception e) {
            log.warn("DB unavailable at startup, using compiled defaults ({} configs). Will retry on next request.",
                cache.size());
        }
    }

    public String get(String key, String defaultVal) {
        return cache.getOrDefault(key, defaultVal);
    }

    public boolean getBool(String key, boolean defaultVal) {
        String val = cache.get(key);
        if (val == null) return defaultVal;
        return "true".equalsIgnoreCase(val) || "1".equals(val);
    }

    public double getDouble(String key, double defaultVal) {
        String val = cache.get(key);
        if (val == null) return defaultVal;
        try { return Double.parseDouble(val); } catch (Exception e) { return defaultVal; }
    }

    public int getInt(String key, int defaultVal) {
        String val = cache.get(key);
        if (val == null) return defaultVal;
        try { return Integer.parseInt(val); } catch (Exception e) { return defaultVal; }
    }

    public List<SystemConfig> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<SystemConfig>()
            .orderByAsc(SystemConfig::getConfigKey));
    }

    public Map<String, Object> listGrouped() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (SystemConfig c : listAll()) {
            result.put(c.getConfigKey(), Map.of(
                "value", c.getConfigValue() != null ? c.getConfigValue() : "",
                "type", c.getConfigType() != null ? c.getConfigType() : "STRING",
                "description", c.getDescription() != null ? c.getDescription() : "",
                "updatedBy", c.getUpdatedBy() != null ? c.getUpdatedBy() : 0L,
                "updatedAt", c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : ""
            ));
        }
        return result;
    }

    public void update(String key, String value, Long updatedBy) {
        cache.put(key, value);
        LambdaQueryWrapper<SystemConfig> qw = new LambdaQueryWrapper<SystemConfig>()
            .eq(SystemConfig::getConfigKey, key);
        SystemConfig existing = mapper.selectOne(qw);
        if (existing != null) {
            mapper.update(null, new LambdaUpdateWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, key)
                .set(SystemConfig::getConfigValue, value)
                .set(SystemConfig::getUpdatedBy, updatedBy)
                .set(SystemConfig::getUpdatedAt, LocalDateTime.now()));
        } else {
            SystemConfig cfg = new SystemConfig();
            cfg.setConfigKey(key);
            cfg.setConfigValue(value);
            cfg.setConfigType("STRING");
            cfg.setUpdatedBy(updatedBy);
            cfg.setUpdatedAt(LocalDateTime.now());
            mapper.insert(cfg);
        }
    }

    public void batchUpdate(Map<String, String> updates, Long updatedBy) {
        updates.forEach((k, v) -> update(k, v, updatedBy));
    }

    public Map<String, String> export() {
        return new LinkedHashMap<>(cache);
    }

    public void importConfigs(Map<String, String> configs, Long updatedBy) {
        configs.forEach((k, v) -> update(k, v, updatedBy));
    }

    public void resetDefaults(Long updatedBy) {
        buildDefaults().forEach((key, valueDesc) -> update(key, valueDesc[0], updatedBy));
    }

    private Map<String, String[]> buildDefaults() {
        Map<String, String[]> d = new LinkedHashMap<>();
        // Model configs
        d.put("models.intent-router.model",      new String[]{defaultIntentModel,   "意图路由模型"});
        d.put("models.intent-router.temperature",new String[]{defaultIntentTemp,    "意图路由温度", "NUMBER"});
        d.put("models.intent-router.max-tokens", new String[]{defaultIntentMaxTokens,"意图路由最大Token", "NUMBER"});
        d.put("models.query-rewriter.model",     new String[]{defaultRewriteModel,  "查询改写模型"});
        d.put("models.query-rewriter.temperature",new String[]{defaultRewriteTemp,  "查询改写温度", "NUMBER"});
        d.put("models.query-rewriter.max-tokens", new String[]{defaultRewriteMaxTokens, "查询改写最大Token", "NUMBER"});
        d.put("models.rag-generator.model",      new String[]{defaultRagModel,      "RAG生成模型"});
        d.put("models.rag-generator.temperature",new String[]{defaultRagTemp,       "RAG生成温度", "NUMBER"});
        d.put("models.rag-generator.max-tokens", new String[]{defaultRagMaxTokens,  "RAG最大Token", "NUMBER"});
        d.put("models.tool-caller.model",        new String[]{defaultToolModel,     "工具调用模型"});
        d.put("models.tool-caller.temperature",  new String[]{defaultToolTemp,      "工具调用温度", "NUMBER"});
        d.put("models.tool-caller.max-tokens",   new String[]{defaultToolMaxTokens, "工具调用最大Token", "NUMBER"});
        d.put("models.chitchat.model",           new String[]{defaultChitchatModel, "闲聊模型"});
        d.put("models.chitchat.temperature",     new String[]{defaultChitchatTemp,  "闲聊温度", "NUMBER"});
        d.put("models.chitchat.max-tokens",      new String[]{defaultChitchatMaxTokens, "闲聊最大Token", "NUMBER"});
        // Prompt templates
        d.put("prompt.rag_default", new String[]{
            "你是SmartCampus校园智能助手。请根据参考资料回答问题，标注来源[来源: 文档名, 第X页]。",
            "RAG 默认 Prompt"});
        d.put("prompt.academic_default", new String[]{
            "你是SmartCampus校园智能助手，擅长查询教务信息。请使用工具获取准确数据后回答。",
            "教务工具 Prompt"});
        d.put("prompt.chitchat_default", new String[]{
            "你是SmartCampus校园智能助手，友好专业。日常闲聊轻松回应，校园问题引导使用功能查询。",
            "闲聊 Prompt"});
        // Tool switches
        d.put("tool.query_academic_calendar.enabled",  new String[]{"true",  "校历查询工具开关", "BOOLEAN"});
        d.put("tool.query_course_selection.enabled",   new String[]{"true",  "选课查询工具开关", "BOOLEAN"});
        d.put("tool.query_department_contact.enabled", new String[]{"true",  "部门联系工具开关", "BOOLEAN"});
        d.put("tool.create_human_ticket.enabled",      new String[]{"true",  "转人工工单工具开关", "BOOLEAN"});
        // RAG params
        d.put("rag.topk.child",          new String[]{"20",   "Child召回数", "NUMBER"});
        d.put("rag.topk.child_sub",      new String[]{"7",    "子问题Child召回数", "NUMBER"});
        d.put("rag.topk.parent",         new String[]{"5",    "Parent回捞数", "NUMBER"});
        d.put("rag.rerank.score_thresh", new String[]{"0.3",  "Rerank最低分", "NUMBER"});
        d.put("rag.rerank.top_n",        new String[]{"12",   "Rerank保留数量", "NUMBER"});
        d.put("faq.match.exact_thresh",  new String[]{"0.92", "FAQ精确命中阈值", "NUMBER"});
        d.put("faq.match.candidate_thresh", new String[]{"0.85", "FAQ候选命中阈值", "NUMBER"});
        return d;
    }
}
