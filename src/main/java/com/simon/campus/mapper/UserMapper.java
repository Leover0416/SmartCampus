package com.simon.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.simon.campus.model.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM users WHERE username = #{usernameOrEmail} OR email = #{usernameOrEmail} LIMIT 1")
    User findByUsernameOrEmail(String usernameOrEmail);

    @Select("SELECT * FROM users WHERE username = #{username} AND email = #{email} LIMIT 1")
    User findByUsernameAndEmail(String username, String email);
}
