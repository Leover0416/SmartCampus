package com.simon.campus.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.simon.campus.mapper.FaqPairMapper;
import com.simon.campus.model.entity.FaqPair;
import com.simon.campus.service.rag.FaqEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FaqService {

    private final FaqPairMapper faqPairMapper;
    private final FaqEmbeddingService faqEmbeddingService;

    public List<FaqPair> list(String keyword, String category, Integer enabled, String priority) {
        LambdaQueryWrapper<FaqPair> qw = new LambdaQueryWrapper<FaqPair>()
            .orderByDesc(FaqPair::getHitCount)
            .orderByDesc(FaqPair::getCreatedAt);
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(FaqPair::getQuestion, keyword)
                         .or().like(FaqPair::getKeywords, keyword));
        }
        if (StringUtils.hasText(category)) {
            qw.eq(FaqPair::getCategory, category);
        }
        if (enabled != null) {
            qw.eq(FaqPair::getEnabled, enabled);
        }
        if (StringUtils.hasText(priority)) {
            qw.eq(FaqPair::getPriority, priority);
        }
        return faqPairMapper.selectList(qw);
    }

    public FaqPair getById(Long id) {
        return faqPairMapper.selectById(id);
    }

    public FaqPair create(FaqPair faq) {
        faq.setId(null);
        if (faq.getHitCount() == null) faq.setHitCount(0);
        if (faq.getEnabled() == null) faq.setEnabled(1);
        if (faq.getPriority() == null) faq.setPriority("MEDIUM");
        faq.setCreatedAt(LocalDateTime.now());
        faq.setUpdatedAt(LocalDateTime.now());
        faqPairMapper.insert(faq);
        embedQuestion(faq.getId(), faq.getQuestion());
        return faqPairMapper.selectById(faq.getId());
    }

    public void update(Long id, FaqPair faq) {
        FaqPair existing = faqPairMapper.selectById(id);
        faq.setId(id);
        faq.setUpdatedAt(LocalDateTime.now());
        faqPairMapper.updateById(faq);
        if (existing != null && !java.util.Objects.equals(existing.getQuestion(), faq.getQuestion())) {
            embedQuestion(id, faq.getQuestion());
        } else {
            faqEmbeddingService.invalidateCache();
        }
    }

    public void delete(Long id) {
        faqPairMapper.deleteById(id);
        faqEmbeddingService.invalidateCache();
    }

    public void toggleEnabled(Long id) {
        FaqPair faq = faqPairMapper.selectById(id);
        if (faq != null) {
            faq.setEnabled(faq.getEnabled() == 1 ? 0 : 1);
            faq.setUpdatedAt(LocalDateTime.now());
            faqPairMapper.updateById(faq);
            if (faq.getEnabled() == 1) {
                embedQuestion(id, faq.getQuestion());
            } else {
                faqEmbeddingService.invalidateCache();
            }
        }
    }

    private void embedQuestion(Long id, String question) {
        try {
            faqEmbeddingService.syncEmbedding(id, question);
            faqEmbeddingService.invalidateCache();
        } catch (Exception e) {
            log.warn("Failed to embed FAQ id={}: {}", id, e.getMessage());
        }
    }

    public void batchImport(List<FaqPair> faqs) {
        for (FaqPair faq : faqs) {
            create(faq);
        }
    }

    public List<FaqPair> exportAll() {
        return faqPairMapper.selectList(new LambdaQueryWrapper<FaqPair>()
            .orderByDesc(FaqPair::getCreatedAt));
    }

    public void incrementHitCount(Long id) {
        faqPairMapper.update(null,
            new LambdaUpdateWrapper<FaqPair>()
                .eq(FaqPair::getId, id)
                .setSql("hit_count = hit_count + 1"));
    }

    public List<FaqPair> getTopFaqs(int n) {
        return faqPairMapper.selectList(new LambdaQueryWrapper<FaqPair>()
            .eq(FaqPair::getEnabled, 1)
            .orderByDesc(FaqPair::getHitCount)
            .last("LIMIT " + n));
    }

    public List<String> getCategories() {
        return faqPairMapper.selectList(new LambdaQueryWrapper<FaqPair>()
            .select(FaqPair::getCategory)
            .isNotNull(FaqPair::getCategory)
            .groupBy(FaqPair::getCategory))
            .stream()
            .map(FaqPair::getCategory)
            .filter(c -> c != null && !c.isBlank())
            .distinct()
            .toList();
    }
}
