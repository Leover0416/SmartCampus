package com.simon.campus.service.admin;

import com.simon.campus.mapper.SystemConfigMapper;
import com.simon.campus.model.entity.SystemConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;

@DisplayName("SystemConfigService - 默认配置")
class SystemConfigServiceDefaultsTest {

    @Test
    @DisplayName("初始化会补齐基础配置页需要的模型参数")
    void initSeedsModelRuntimeDefaults() {
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        when(mapper.selectList(null)).thenReturn(List.of());
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(SystemConfig.class))).thenReturn(1);

        SystemConfigService service = new SystemConfigService(mapper);
        setDefaultFields(service);

        service.init();

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(mapper, atLeastOnce()).insert(captor.capture());
        Set<String> keys = captor.getAllValues().stream()
            .map(SystemConfig::getConfigKey)
            .collect(Collectors.toSet());

        assertThat(keys).contains(
            "models.intent-router.temperature",
            "models.intent-router.max-tokens",
            "models.query-rewriter.temperature",
            "models.query-rewriter.max-tokens",
            "models.rag-generator.temperature",
            "models.rag-generator.max-tokens",
            "models.tool-caller.temperature",
            "models.chitchat.temperature",
            "models.chitchat.max-tokens"
        );
    }

    private void setDefaultFields(SystemConfigService service) {
        ReflectionTestUtils.setField(service, "defaultIntentModel", "qwen-turbo");
        ReflectionTestUtils.setField(service, "defaultRewriteModel", "qwen-plus");
        ReflectionTestUtils.setField(service, "defaultRagModel", "qwen-max");
        ReflectionTestUtils.setField(service, "defaultToolModel", "qwen-plus");
        ReflectionTestUtils.setField(service, "defaultChitchatModel", "qwen-turbo");
        ReflectionTestUtils.setField(service, "defaultIntentTemp", "0.1");
        ReflectionTestUtils.setField(service, "defaultIntentMaxTokens", "16");
        ReflectionTestUtils.setField(service, "defaultRewriteTemp", "0.2");
        ReflectionTestUtils.setField(service, "defaultRewriteMaxTokens", "512");
        ReflectionTestUtils.setField(service, "defaultRagTemp", "0.2");
        ReflectionTestUtils.setField(service, "defaultRagMaxTokens", "2048");
        ReflectionTestUtils.setField(service, "defaultToolTemp", "0.1");
        ReflectionTestUtils.setField(service, "defaultToolMaxTokens", "1024");
        ReflectionTestUtils.setField(service, "defaultChitchatTemp", "0.7");
        ReflectionTestUtils.setField(service, "defaultChitchatMaxTokens", "1024");
    }
}
