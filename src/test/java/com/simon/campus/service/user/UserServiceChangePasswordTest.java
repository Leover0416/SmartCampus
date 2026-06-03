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

@DisplayName("UserService - 修改密码")
class UserServiceChangePasswordTest {

    @Test
    @DisplayName("旧密码正确时更新为新密码哈希")
    void changesPasswordWhenOldPasswordMatches() {
        UserMapper userMapper = mock(UserMapper.class);
        User user = new User();
        user.setId(1L);
        user.setPassword(SecureUtil.sha256("old-pass"));
        when(userMapper.selectById(1L)).thenReturn(user);

        UserServiceImpl service = new UserServiceImpl(userMapper, mock(JwtUtil.class));

        service.changePassword(1L, "old-pass", "new-pass");

        assertThat(user.getPassword()).isEqualTo(SecureUtil.sha256("new-pass"));
        verify(userMapper).updateById(user);
    }

    @Test
    @DisplayName("旧密码错误时拒绝修改")
    void rejectsWrongOldPassword() {
        UserMapper userMapper = mock(UserMapper.class);
        User user = new User();
        user.setId(1L);
        user.setPassword(SecureUtil.sha256("old-pass"));
        when(userMapper.selectById(1L)).thenReturn(user);

        UserServiceImpl service = new UserServiceImpl(userMapper, mock(JwtUtil.class));

        assertThatThrownBy(() -> service.changePassword(1L, "wrong-pass", "new-pass"))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("原密码错误");
    }
}
