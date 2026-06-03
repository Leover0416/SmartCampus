package com.simon.campus.service.user;

import cn.hutool.crypto.SecureUtil;
import com.simon.campus.common.BizException;
import com.simon.campus.common.JwtUtil;
import com.simon.campus.mapper.UserMapper;
import com.simon.campus.model.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("UserService - 忘记密码")
class UserServiceResetPasswordTest {

    @Test
    @DisplayName("用户名和注册邮箱匹配时重置为默认密码")
    void resetsPasswordWhenUsernameAndEmailMatch() {
        UserMapper userMapper = mock(UserMapper.class);
        User user = new User();
        user.setId(1L);
        user.setUsername("student001");
        user.setEmail("student001@campus.edu");
        user.setPassword(SecureUtil.sha256("old-pass"));
        when(userMapper.findByUsernameAndEmail("student001", "student001@campus.edu")).thenReturn(user);

        UserServiceImpl service = new UserServiceImpl(userMapper, mock(JwtUtil.class));

        service.resetPassword("student001", "student001@campus.edu");

        assertThat(user.getPassword()).isEqualTo(SecureUtil.sha256("123456"));
        verify(userMapper).updateById(user);
    }

    @Test
    @DisplayName("用户名和邮箱不匹配时拒绝重置")
    void rejectsResetWhenUsernameAndEmailDoNotMatch() {
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.findByUsernameAndEmail("student001", "other@campus.edu")).thenReturn(null);

        UserServiceImpl service = new UserServiceImpl(userMapper, mock(JwtUtil.class));

        assertThatThrownBy(() -> service.resetPassword("student001", "other@campus.edu"))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("用户名或邮箱不匹配");
    }
}
