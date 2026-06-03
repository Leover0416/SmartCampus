package com.simon.campus.service.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.simon.campus.mapper.ChildChunkMapper;
import com.simon.campus.model.dto.RecallCandidate;
import com.simon.campus.model.entity.ChildChunk;
import com.simon.campus.service.ingest.EmbeddingService;
import com.simon.campus.service.ingest.MilvusService;
import com.simon.campus.service.ingest.VisibilityPolicy;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 稠密向量召回（Milvus）。
 * <p>
 * 被 {@link MultiRouteRecaller} 调用：mainQuery 与各 subQuery 各执行一次本方法，
 * 即「一个问题 → 一次 embed → 一次 Milvus TopK」。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DenseRetriever {

    private final MilvusService milvusService;
    private final EmbeddingService embeddingService;
    private final ChildChunkMapper childChunkMapper;

    /**
     * @param query           主问或子问文本
     * @param userAccessLevel 角色可见性过滤
     * @param topK            返回 Child 数量上限
     */
    public List<RecallCandidate> retrieve(String query, int userAccessLevel, int topK) {
        try {
            // DashScope text-embedding-v3
            float[] vector = embeddingService.embedOne(query);
            // Milvus HNSW 近似最近邻，按 access_level 过滤
            List<SearchResultsWrapper.IDScore> results = milvusService.search(vector, userAccessLevel, topK);
            return buildCandidates(results, "dense", userAccessLevel);
        } catch (Exception e) {
            log.warn("DenseRetriever failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Milvus 只存 childId 与向量，正文从 MySQL child_chunks 批量回填 */
    private List<RecallCandidate> buildCandidates(
            List<SearchResultsWrapper.IDScore> results, String source, int userAccessLevel) {
        if (results.isEmpty()) return Collections.emptyList();

        List<String> childIds = new ArrayList<>();
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        for (SearchResultsWrapper.IDScore r : results) {
            String childId;
            try {
                childId = r.getStrID();
            } catch (Exception e) {
                childId = String.valueOf(r.getLongID());
            }
            if (childId == null || childId.isBlank()) continue;
            childIds.add(childId);
            scoreMap.put(childId, (double) r.getScore());
        }

        Map<String, ChildChunk> chunkMap = new HashMap<>();
        if (!childIds.isEmpty()) {
            LambdaQueryWrapper<ChildChunk> qw = new LambdaQueryWrapper<ChildChunk>()
                .in(ChildChunk::getChildId, childIds);
            childChunkMapper.selectList(qw)
                .forEach(c -> chunkMap.put(c.getChildId(), c));
        }

        List<RecallCandidate> candidates = new ArrayList<>();
        for (String childId : childIds) {
            ChildChunk c = chunkMap.get(childId);
            if (c == null) continue;
            if (!VisibilityPolicy.canView(c.getAccessLevel(), userAccessLevel)) continue;
            candidates.add(RecallCandidate.builder()
                .childId(childId)
                .parentId(c.getParentId())
                .docId(c.getDocId())
                .docTitle(c.getDocTitle())
                .headingPath(c.getHeadingPath())
                .content(c.getContent())
                .pageStart(c.getPageStart())
                .score(scoreMap.getOrDefault(childId, 0.0))
                .source(source)
                .build());
        }
        return candidates;
    }
}
