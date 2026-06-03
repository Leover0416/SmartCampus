package com.simon.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.simon.campus.model.entity.FaqPair;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FaqPairMapper extends BaseMapper<FaqPair> {

    @Select("SELECT * FROM faq_pairs WHERE enabled = 1 ORDER BY priority DESC, hit_count DESC")
    List<FaqPair> findAllEnabled();
}
