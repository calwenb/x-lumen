package com.calwen.xlumen.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.identity.dto.LoginDTO;
import com.calwen.xlumen.identity.dto.RegisterDTO;
import com.calwen.xlumen.identity.entity.UserEntity;
import com.calwen.xlumen.identity.mapper.UserMapper;
import com.calwen.xlumen.identity.mapper.WorkspaceMapper;
import com.calwen.xlumen.identity.mapper.WorkspaceMemberMapper;
import com.calwen.xlumen.identity.service.RefreshTokenService;
import com.calwen.xlumen.identity.vo.TokenVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 认证服务单元测试（F-0101）：注册冲突统一提示、登录失败统一 401、令牌签发。
 *
 * @author calwen
 * @date 2026/8/12
 */
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private WorkspaceMapper workspaceMapper;
    @Mock
    private WorkspaceMemberMapper memberMapper;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private JwtEncoder jwtEncoder;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenAnswer(invocation -> Jwt.withTokenValue("jwt-token")
                .header("alg", "HS256")
                .subject("1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build());
        when(refreshTokenService.create(eq(1L), any())).thenReturn("refresh-token");
        authService = new AuthServiceImpl(userMapper, workspaceMapper, memberMapper, encoder,
                refreshTokenService, jwtEncoder, "xlumen");
    }

    @Test
    void register_usernameConflict_returnsUnifiedMessage() {
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        RegisterDTO dto = new RegisterDTO("taken_user", "Test123456", "");

        assertThatThrownBy(() -> authService.register(dto))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.CONFLICT))
                .hasMessageContaining("已被使用");
    }

    @Test
    void login_wrongPassword_returnsUnauthorized() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("tester");
        user.setPasswordHash(new BCryptPasswordEncoder().encode("CorrectPass123"));
        user.setStatus(1);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        assertThatThrownBy(() -> authService.login(new LoginDTO("tester", "WrongPass123")))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void login_validCredentials_issuesTokens() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("tester");
        user.setPasswordHash(new BCryptPasswordEncoder().encode("CorrectPass123"));
        user.setStatus(1);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);
        when(memberMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            com.calwen.xlumen.identity.entity.WorkspaceMemberEntity m =
                    new com.calwen.xlumen.identity.entity.WorkspaceMemberEntity();
            m.setWorkspaceId(100L);
            m.setRoleCode("OWNER");
            return m;
        });

        TokenVO token = authService.login(new LoginDTO("tester", "CorrectPass123"));

        assertThat(token.getAccessToken()).isEqualTo("jwt-token");
        assertThat(token.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(token.getUser().getRoles()).containsExactly("OWNER");
    }
}
