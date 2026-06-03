package com.simon.campus.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface FaqSuggestionMapper {

    @Select("""
        SELECT
          user_query AS question,
          COUNT(*) AS askCount,
          SUBSTRING_INDEX(GROUP_CONCAT(intent ORDER BY created_at DESC), ',', 1) AS intent,
          ROUND(AVG(COALESCE(total_ms, 0))) AS avgMs,
          MAX(created_at) AS lastAskedAt,
          SUM(CASE WHEN parent_count IS NULL OR parent_count = 0 THEN 1 ELSE 0 END) AS weakRecallCount,
          SUM(CASE WHEN total_ms IS NOT NULL AND total_ms >= 5000 THEN 1 ELSE 0 END) AS slowCount
        FROM agent_logs
        WHERE created_at >= #{since}
          AND user_query IS NOT NULL
          AND CHAR_LENGTH(TRIM(user_query)) >= 4
          AND (hit_docs IS NULL OR hit_docs <> 'FAQ')
          AND intent IN ('POLICY_QA', 'DOC_SEARCH', 'ACADEMIC_TOOL')
        GROUP BY user_query
        HAVING COUNT(*) >= #{minCount}
        ORDER BY askCount DESC, lastAskedAt DESC
        LIMIT #{limit}
        """)
    List<Map<String, Object>> selectCandidateQueries(
        @Param("since") LocalDateTime since,
        @Param("minCount") int minCount,
        @Param("limit") int limit);
}
