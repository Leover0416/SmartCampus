package com.simon.campus.service.ingest;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.JiebaSegmenter.SegMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BM25Indexer {

    private final JdbcTemplate jdbcTemplate;

    // BM25 constants
    private static final double K1 = 1.5;
    private static final double B  = 0.75;

    private final JiebaSegmenter segmenter = new JiebaSegmenter();

    /**
     * Index a batch of child chunks into chunk_inverted_index and chunk_stats.
     */
    public void indexChildren(String docId, List<String> childIds, List<String> contents) {
        if (childIds.isEmpty()) return;

        // Compute average doc length for this batch (used later during search)
        double avgLen = contents.stream().mapToInt(String::length).average().orElse(100.0);

        // Build TF map per child
        List<Object[]> indexRows = new ArrayList<>();
        List<Object[]> statsRows = new ArrayList<>();

        for (int i = 0; i < childIds.size(); i++) {
            String childId = childIds.get(i);
            String content = contents.get(i);
            int docLen = content.length();

            Map<String, Integer> tf = computeTF(content);
            for (Map.Entry<String, Integer> entry : tf.entrySet()) {
                // tf_value stores raw TF for BM25 calculation at query time
                indexRows.add(new Object[]{childId, docId, entry.getKey(), entry.getValue(), docLen});
            }
            statsRows.add(new Object[]{childId, docLen});
        }

        // Batch insert into chunk_inverted_index
        jdbcTemplate.batchUpdate(
            "INSERT IGNORE INTO chunk_inverted_index (child_id, doc_id, term, tf, doc_len) VALUES (?,?,?,?,?)",
            indexRows
        );

        // Batch insert/update chunk_stats
        jdbcTemplate.batchUpdate(
            "INSERT INTO chunk_stats (child_id, doc_len, hit_count) VALUES (?,?,0) " +
            "ON DUPLICATE KEY UPDATE doc_len = VALUES(doc_len)",
            statsRows
        );

        log.debug("Indexed {} child chunks from doc {}", childIds.size(), docId);
    }

    /**
     * BM25 search: returns childId → score map, top-k.
     */
    public List<Map.Entry<String, Double>> search(String query, int accessLevel, int topK) {
        List<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) return Collections.emptyList();

        // Get global stats
        long totalDocs = Optional.ofNullable(
            jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT child_id) FROM chunk_stats", Long.class)
        ).orElse(1L);
        double avgDocLen = Optional.ofNullable(
            jdbcTemplate.queryForObject("SELECT AVG(doc_len) FROM chunk_stats", Double.class)
        ).orElse(100.0);

        Map<String, Double> scores = new HashMap<>();

        for (String term : queryTerms) {
            // IDF component
            Long df = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT child_id) FROM chunk_inverted_index WHERE term = ?",
                Long.class, term
            );
            if (df == null || df == 0) continue;
            double idf = Math.log((totalDocs - df + 0.5) / (df + 0.5) + 1.0);

            // Fetch TF records (with visibility join via child_chunks)
            String sql = "SELECT i.child_id, i.tf, i.doc_len FROM chunk_inverted_index i " +
                         "JOIN child_chunks c ON c.child_id = i.child_id " +
                         "WHERE i.term = ? AND c.access_level IN (" + accessPlaceholders(accessLevel) + ")";
            List<Object> params = new ArrayList<>();
            params.add(term);
            params.addAll(VisibilityPolicy.visibleLevelsForViewer(accessLevel));
            jdbcTemplate.query(sql, rs -> {
                String childId = rs.getString("child_id");
                double tf = rs.getDouble("tf");
                double docLen = rs.getDouble("doc_len");
                double tfNorm = (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * docLen / avgDocLen));
                scores.merge(childId, idf * tfNorm, Double::sum);
            }, params.toArray());
        }

        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .toList();
    }

    /**
     * Remove all index entries for a document (called on doc delete).
     */
    public void deleteByDocId(String docId) {
        // Get child IDs for this doc first
        List<String> childIds = jdbcTemplate.queryForList(
            "SELECT DISTINCT child_id FROM chunk_inverted_index WHERE doc_id = ?",
            String.class, docId
        );
        jdbcTemplate.update("DELETE FROM chunk_inverted_index WHERE doc_id = ?", docId);
        if (!childIds.isEmpty()) {
            String placeholders = String.join(",", childIds.stream().map(id -> "?").toList());
            jdbcTemplate.update(
                "DELETE FROM chunk_stats WHERE child_id IN (" + placeholders + ")",
                childIds.toArray()
            );
        }
    }

    public List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        for (var word : segmenter.process(text, SegMode.SEARCH)) {
            String w = word.word.trim();
            if (w.length() >= 2) tokens.add(w);
        }
        return tokens;
    }

    private Map<String, Integer> computeTF(String content) {
        Map<String, Integer> tf = new HashMap<>();
        for (var word : segmenter.process(content, SegMode.SEARCH)) {
            String w = word.word.trim();
            if (w.length() >= 2) tf.merge(w, 1, Integer::sum);
        }
        return tf;
    }

    private String accessPlaceholders(int accessLevel) {
        return String.join(",", VisibilityPolicy.visibleLevelsForViewer(accessLevel).stream().map(v -> "?").toList());
    }
}
