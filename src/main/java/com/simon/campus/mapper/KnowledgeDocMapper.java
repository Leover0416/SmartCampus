package com.simon.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.simon.campus.model.entity.KnowledgeDoc;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface KnowledgeDocMapper extends BaseMapper<KnowledgeDoc> {

    @Update("UPDATE knowledge_docs SET status=#{status}, parent_chunk_count=#{parentCount}, " +
            "child_chunk_count=#{childCount}, error_msg=#{errorMsg} WHERE doc_id=#{docId}")
    void updateProcessResult(@Param("docId") String docId,
                             @Param("status") String status,
                             @Param("parentCount") int parentCount,
                             @Param("childCount") int childCount,
                             @Param("errorMsg") String errorMsg);
}
