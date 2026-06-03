package com.simon.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.simon.campus.model.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

  /** Latest assistant reply in the most recent session that asked this exact question. */
  @Select("""
      SELECT cm.content FROM chat_messages cm
      WHERE cm.role = 'assistant'
        AND cm.session_id = (
          SELECT al.session_id FROM agent_logs al
          WHERE al.user_query = #{question}
          ORDER BY al.created_at DESC
          LIMIT 1
        )
      ORDER BY cm.created_at DESC
      LIMIT 1
      """)
  String selectLatestAssistantAnswer(@Param("question") String question);
}
