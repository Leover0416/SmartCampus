package com.simon.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.simon.campus.model.entity.ParentChunk;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ParentChunkMapper extends BaseMapper<ParentChunk> {

    default void insertBatch(List<ParentChunk> list) {
        list.forEach(this::insert);
    }
}
