package com.simon.campus.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface DashboardMapper {

    @Select("SELECT intent, COUNT(*) AS cnt FROM agent_logs WHERE created_at >= #{since} GROUP BY intent ORDER BY cnt DESC")
    List<Map<String, Object>> selectIntentDistribution(@Param("since") LocalDateTime since);

    @Select("SELECT DATE_FORMAT(created_at, '%m-%d') AS date_str, COUNT(*) AS cnt FROM chat_sessions WHERE created_at >= #{since} GROUP BY DATE_FORMAT(created_at, '%m-%d'), DATE(created_at) ORDER BY DATE(created_at)")
    List<Map<String, Object>> selectSessionTrend(@Param("since") LocalDateTime since);

    @Select("SELECT DATE_FORMAT(created_at, '%m-%d') AS date_str, COUNT(*) AS cnt FROM chat_messages WHERE role = 'user' AND created_at >= #{since} GROUP BY DATE_FORMAT(created_at, '%m-%d'), DATE(created_at) ORDER BY DATE(created_at)")
    List<Map<String, Object>> selectMessageTrend(@Param("since") LocalDateTime since);

    @Select("SELECT DATE_FORMAT(s.created_at, '%m-%d') AS date_str, " +
            "COUNT(DISTINCT s.user_id) AS cnt " +
            "FROM chat_sessions s " +
            "WHERE s.created_at >= #{since} " +
            "GROUP BY DATE_FORMAT(s.created_at, '%m-%d'), DATE(s.created_at) " +
            "ORDER BY DATE(s.created_at)")
    List<Map<String, Object>> selectUniqueUserTrend(@Param("since") LocalDateTime since);

    @Select("SELECT DATE_FORMAT(d.dt, '%m-%d') AS date_str, " +
            "COALESCE(t.ticket_count, 0) AS cnt, " +
            "ROUND(CASE WHEN COALESCE(s.session_count, 0) = 0 THEN 0 ELSE COALESCE(t.ticket_count, 0) / s.session_count * 100 END, 1) AS rate " +
            "FROM ( " +
            "  SELECT DATE(created_at) AS dt FROM chat_sessions WHERE created_at >= #{since} " +
            "  UNION SELECT DATE(created_at) AS dt FROM human_tickets WHERE created_at >= #{since} " +
            ") d " +
            "LEFT JOIN (SELECT DATE(created_at) AS dt, COUNT(*) AS session_count FROM chat_sessions WHERE created_at >= #{since} GROUP BY DATE(created_at)) s ON s.dt = d.dt " +
            "LEFT JOIN (SELECT DATE(created_at) AS dt, COUNT(*) AS ticket_count FROM human_tickets WHERE created_at >= #{since} GROUP BY DATE(created_at)) t ON t.dt = d.dt " +
            "ORDER BY d.dt")
    List<Map<String, Object>> selectHumanTakeoverTrend(@Param("since") LocalDateTime since);

    @Select("SELECT user_query, COUNT(*) AS cnt FROM agent_logs WHERE created_at >= #{since} AND user_query IS NOT NULL GROUP BY user_query ORDER BY cnt DESC LIMIT #{n}")
    List<Map<String, Object>> selectTopQueries(@Param("since") LocalDateTime since, @Param("n") int n);

    @Select("SELECT REPLACE(hit_docs, 'TOOL:', '') AS tool_name, COUNT(*) AS cnt FROM agent_logs WHERE hit_docs LIKE 'TOOL:%' AND created_at >= #{since} GROUP BY hit_docs ORDER BY cnt DESC")
    List<Map<String, Object>> selectToolCalls(@Param("since") LocalDateTime since);

    @Select("SELECT CASE intent " +
            "WHEN 'ACADEMIC_TOOL' THEN '教务工具' " +
            "WHEN 'POLICY_QA' THEN '政策问答' " +
            "WHEN 'DOC_SEARCH' THEN '文档检索' " +
            "WHEN 'HUMAN' THEN '人工服务' " +
            "WHEN 'HUMAN_HANDOFF' THEN '人工服务' " +
            "WHEN 'CHITCHAT' THEN '闲聊咨询' " +
            "ELSE COALESCE(NULLIF(intent, ''), '其他') END AS name, " +
            "COUNT(*) AS cnt " +
            "FROM agent_logs " +
            "WHERE created_at >= #{since} " +
            "GROUP BY name " +
            "ORDER BY cnt DESC " +
            "LIMIT 6")
    List<Map<String, Object>> selectHotCategories(@Param("since") LocalDateTime since);

    @Select("SELECT COALESCE(AVG(total_ms), 0) FROM agent_logs WHERE created_at >= #{since} AND total_ms > 0")
    Double selectAvgResponseMs(@Param("since") LocalDateTime since);

    @Select("SELECT COALESCE(AVG(total_ms), 0) FROM agent_logs WHERE created_at >= #{start} AND created_at < #{end} AND total_ms > 0")
    Double selectAvgResponseMsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) FROM agent_logs WHERE hit_docs = 'FAQ' AND created_at >= #{since}")
    long countFaqHits(@Param("since") LocalDateTime since);

    @Select("SELECT COUNT(*) FROM agent_logs WHERE created_at >= #{since} AND hit_docs IS NOT NULL AND hit_docs <> '' AND hit_docs NOT LIKE 'TOOL:%'")
    long countKnowledgeHits(@Param("since") LocalDateTime since);

    @Select("SELECT COALESCE(SUM(parent_count), 0) FROM agent_logs WHERE created_at >= #{since} AND parent_count > 0")
    long countKnowledgeReferences(@Param("since") LocalDateTime since);

    @Select("SELECT COUNT(*) FROM chat_messages WHERE role = 'assistant' AND created_at >= #{since} AND source_refs IS NOT NULL AND source_refs <> ''")
    long countAssistantMessagesWithRefs(@Param("since") LocalDateTime since);

    @Select("SELECT COUNT(*) FROM chat_messages WHERE role = 'assistant' AND created_at >= #{since}")
    long countAssistantMessages(@Param("since") LocalDateTime since);

    @Select("SELECT COUNT(*) FROM agent_logs WHERE created_at >= #{since}")
    long countTotalRequests(@Param("since") LocalDateTime since);

    @Select("SELECT COUNT(*) FROM agent_logs WHERE created_at >= #{start} AND created_at < #{end}")
    long countTotalRequestsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) FROM agent_logs WHERE created_at >= #{since} AND (intent = 'HUMAN' OR intent = 'HUMAN_HANDOFF')")
    long countHumanRequests(@Param("since") LocalDateTime since);

    @Select("SELECT COUNT(*) FROM agent_logs WHERE created_at >= #{start} AND created_at < #{end} AND (intent = 'HUMAN' OR intent = 'HUMAN_HANDOFF')")
    long countHumanRequestsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) FROM human_tickets WHERE created_at >= #{since}")
    long countHumanTickets(@Param("since") LocalDateTime since);

    @Select("SELECT COALESCE(AVG(rating), 0) FROM human_tickets WHERE rating IS NOT NULL AND created_at >= #{since}")
    Double selectAvgRating(@Param("since") LocalDateTime since);

    @Select("SELECT COUNT(*) FROM chat_sessions WHERE DATE(created_at) = CURDATE()")
    long countSessionsToday();

    @Select("SELECT COUNT(*) FROM chat_sessions WHERE created_at >= #{start} AND created_at < #{end}")
    long countSessionsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) FROM chat_messages WHERE DATE(created_at) = CURDATE()")
    long countMessagesToday();

    @Select("SELECT COUNT(*) FROM human_tickets WHERE status = 'PENDING'")
    long countPendingTickets();

    @Select("SELECT COUNT(*) FROM knowledge_docs WHERE status = 'READY'")
    long countReadyDocs();

    @Select("SELECT COUNT(*) FROM faq_pairs WHERE enabled = 1")
    long countActiveFaqs();
}
