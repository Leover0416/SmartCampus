package com.simon.campus.service.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.simon.campus.mapper.ParentChunkMapper;
import com.simon.campus.model.dto.RecallCandidate;
import com.simon.campus.model.entity.ParentChunk;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Stage 5: Group child hits by parent_id, score parents, fetch top 3–6 parent texts.
 * Score = 0.5 * max_child_score + 0.3 * log(1 + hit_count) + 0.2 * coverage
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ParentChildContextAssembler {

    private static final int MIN_PARENTS = 3;
    private static final int MAX_PARENTS = 6;
    private static final int TOKEN_BUDGET = 4000;

    private final ParentChunkMapper parentChunkMapper;

    @Data
    @AllArgsConstructor
    public static class AssembledContext {
        private final String contextText;
        private final List<SourceRef> sourceRefs;
    }

    @Data
    @AllArgsConstructor
    public static class SourceRef {
        private final String docTitle;
        private final String headingPath;
        private final Integer pageStart;
        private final String parentId;
    }

    public AssembledContext assemble(List<RecallCandidate> rerankedChildren) {
        if (rerankedChildren.isEmpty()) {
            return new AssembledContext("", Collections.emptyList());
        }

        // Group children by parent_id
        Map<String, List<RecallCandidate>> byParent = rerankedChildren.stream()
            .collect(Collectors.groupingBy(RecallCandidate::getParentId));

        // Score each parent
        List<ParentScore> scored = new ArrayList<>();
        for (Map.Entry<String, List<RecallCandidate>> e : byParent.entrySet()) {
            String parentId = e.getKey();
            List<RecallCandidate> children = e.getValue();
            double maxChildScore = children.stream().mapToDouble(RecallCandidate::getScore).max().orElse(0);
            int hitCount = children.size();
            int totalChildren = byParent.values().stream().mapToInt(List::size).sum();
            double coverage = totalChildren > 0 ? (double) hitCount / rerankedChildren.size() : 0;
            double parentScore = 0.5 * maxChildScore + 0.3 * Math.log(1 + hitCount) + 0.2 * coverage;
            scored.add(new ParentScore(parentId, parentScore, children.get(0)));
        }
        scored.sort(Comparator.comparingDouble(ParentScore::score).reversed());

        // Select top 3–6 parents within token budget
        int targetCount = Math.min(MAX_PARENTS, Math.max(MIN_PARENTS, scored.size()));
        List<ParentScore> selected = scored.subList(0, Math.min(targetCount, scored.size()));

        // Load parent content from DB
        List<String> parentIds = selected.stream().map(ParentScore::parentId).toList();
        Map<String, ParentChunk> parentMap = new HashMap<>();
        if (!parentIds.isEmpty()) {
            LambdaQueryWrapper<ParentChunk> qw = new LambdaQueryWrapper<ParentChunk>()
                .in(ParentChunk::getParentId, parentIds);
            parentChunkMapper.selectList(qw)
                .forEach(p -> parentMap.put(p.getParentId(), p));
        }

        StringBuilder sb = new StringBuilder();
        List<SourceRef> sourceRefs = new ArrayList<>();
        int totalChars = 0;
        int sectionIdx = 1;

        for (ParentScore ps : selected) {
            ParentChunk parent = parentMap.get(ps.parentId());
            if (parent == null) continue;

            String content = parent.getContent();
            // Rough token estimate: 1 token ≈ 1.5 Chinese chars
            int estimatedTokens = (int) (content.length() / 1.5);
            if (totalChars > 0 && totalChars + estimatedTokens > TOKEN_BUDGET) break;

            String heading = parent.getHeadingPath() != null ? parent.getHeadingPath() : parent.getDocTitle();
            String pageRef = parent.getPageStart() != null ? "第" + parent.getPageStart() + "页" : "";

            sb.append("【参考资料").append(sectionIdx++).append("】")
              .append(parent.getDocTitle());
            if (!pageRef.isEmpty()) sb.append(" ").append(pageRef);
            if (heading != null && !heading.isBlank()) sb.append(" > ").append(heading);
            sb.append("\n").append(content).append("\n\n");

            sourceRefs.add(new SourceRef(
                parent.getDocTitle(),
                parent.getHeadingPath(),
                parent.getPageStart(),
                parent.getParentId()
            ));
            totalChars += estimatedTokens;
        }

        log.debug("Context assembled: {} parents, ~{} tokens", sourceRefs.size(), totalChars);
        return new AssembledContext(sb.toString().strip(), sourceRefs);
    }

    private record ParentScore(String parentId, double score, RecallCandidate sampleChild) {}
}
