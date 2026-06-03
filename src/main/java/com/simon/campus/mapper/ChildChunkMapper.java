package com.simon.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.simon.campus.model.entity.ChildChunk;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChildChunkMapper extends BaseMapper<ChildChunk> {

    default void insertBatch(List<ChildChunk> list) {
        list.forEach(this::insert);
    }
}
