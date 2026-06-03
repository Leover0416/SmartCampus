package com.simon.campus.service.ingest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * BM25 公式正确性验证 — 不依赖数据库，直接验证数学公式实现。
 */
@DisplayName("BM25 公式单元测试")
class BM25ScoringTest {

    // k1=1.5, b=0.75 (与 BM25Indexer 保持一致)
    private static final double K1 = 1.5;
    private static final double B  = 0.75;

    /**
     * BM25 单词得分公式：
     * score = idf * (tf * (k1+1)) / (tf + k1 * (1 - b + b * docLen/avgDocLen))
     */
    private double bm25Score(int tf, int docLen, double avgDocLen, double idf) {
        double normTf = tf * (K1 + 1) / (tf + K1 * (1 - B + B * docLen / avgDocLen));
        return idf * normTf;
    }

    @Test
    @DisplayName("TF 越高，得分越高（饱和性）")
    void higherTF_givesHigherScore() {
        double idf = 2.0;
        double avgDocLen = 200.0;
        int docLen = 200;

        double score1 = bm25Score(1, docLen, avgDocLen, idf);
        double score3 = bm25Score(3, docLen, avgDocLen, idf);
        double score10 = bm25Score(10, docLen, avgDocLen, idf);

        assertThat(score1).isLessThan(score3);
        assertThat(score3).isLessThan(score10);
    }

    @Test
    @DisplayName("BM25 有 TF 饱和效果：高 TF 增益递减")
    void bm25HasTFSaturation() {
        double idf = 2.0;
        double avgDocLen = 200.0;
        int docLen = 200;

        double delta1to2 = bm25Score(2, docLen, avgDocLen, idf) - bm25Score(1, docLen, avgDocLen, idf);
        double delta9to10 = bm25Score(10, docLen, avgDocLen, idf) - bm25Score(9, docLen, avgDocLen, idf);

        assertThat(delta1to2).isGreaterThan(delta9to10);
    }

    @Test
    @DisplayName("文档越长（超过平均），得分越低（长度惩罚）")
    void longerDocument_givesLowerScore() {
        double idf = 2.0;
        double avgDocLen = 200.0;
        int tf = 3;

        double scoreShort = bm25Score(tf, 100, avgDocLen, idf);
        double scoreMid   = bm25Score(tf, 200, avgDocLen, idf);
        double scoreLong  = bm25Score(tf, 500, avgDocLen, idf);

        assertThat(scoreShort).isGreaterThan(scoreMid);
        assertThat(scoreMid).isGreaterThan(scoreLong);
    }

    @Test
    @DisplayName("IDF 为 0 时得分为 0（停用词不贡献分数）")
    void zeroIDF_givesZeroScore() {
        double score = bm25Score(5, 200, 200.0, 0.0);
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    @DisplayName("得分始终非负")
    void scoreIsAlwaysNonNegative() {
        // TF=0 极端情况
        double score = bm25Score(0, 200, 200.0, 2.0);
        assertThat(score).isGreaterThanOrEqualTo(0.0);
    }
}
