package com.simon.campus.service.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.simon.campus.mapper.ChildChunkMapper;
import com.simon.campus.model.dto.RecallCandidate;
import com.simon.campus.model.entity.ChildChunk;
import com.simon.campus.service.ingest.BM25Indexer;
import com.simon.campus.service.ingest.VisibilityPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * BM25 关键词召回（MySQL 倒排表 chunk_inverted_index）。
 * <p>
 * 仅对 {@link QueryExpansion#getMainQuery()} 检索一次；子查询 subQueries 不参与 BM25。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BM25Retriever {

    private final BM25Indexer bm25Indexer;
    private final ChildChunkMapper childChunkMapper;

    /**
     * @param query 一般为 mainQuery（完整问句，非 keywords 拼接）
     */
    public List<RecallCandidate> retrieve(String query, int userAccessLevel, int topK) {
        try {
            List<Map.Entry<String, Double>> scored = bm25Indexer.search(query, userAccessLevel, topK);
            if (scored.isEmpty()) return Collections.emptyList();

            List<String> childIds = scored.stream().map(Map.Entry::getKey).toList();
            Map<String, Double> scoreMap = new HashMap<>();
            scored.forEach(e -> scoreMap.put(e.getKey(), e.getValue()));

            Map<String, ChildChunk> chunkMap = new HashMap<>();
            LambdaQueryWrapper<ChildChunk> qw = new LambdaQueryWrapper<ChildChunk>()
                .in(ChildChunk::getChildId, childIds);
            childChunkMapper.selectList(qw)
                .forEach(c -> chunkMap.put(c.getChildId(), c));

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
                    .source("bm25")
                    .build());
            }
            return candidates;
        } catch (Exception e) {
            log.warn("BM25Retriever failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
