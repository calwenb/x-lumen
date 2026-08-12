package com.calwen.xlumen.identity.iam.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.identity.iam.dto.LoginDTO;
import com.calwen.xlumen.identity.iam.dto.RefreshTokenDTO;
import com.calwen.xlumen.identity.iam.dto.RegisterDTO;
import com.calwen.xlumen.identity.iam.entity.UserEntity;
import com.calwen.xlumen.identity.iam.entity.WorkspaceEntity;
import com.calwen.xlumen.identity.iam.entity.WorkspaceMemberEntity;
import com.calwen.xlumen.identity.iam.enums.RoleCode;
import com.calwen.xlumen.identity.iam.mapper.UserMapper;
import com.calwen.xlumen.identity.iam.mapper.WorkspaceMapper;
import com.calwen.xlumen.identity.iam.mapper.WorkspaceMemberMapper;
import com.calwen.xlumen.identity.iam.service.AuthService;
import com.calwen.xlumen.identity.iam.service.RefreshTokenService;
import com.calwen.xlumen.identity.iam.vo.TokenVO;
import com.calwen.xlumen.identity.iam.vo.UserProfileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * 认证服务实现（F-0101）：注册（注册即建空间，决策 D9）/登录/登出/刷新。
 * 登录失败统一提示 + 统一延迟防枚举（PRODUCT §10）；刷新令牌 GETDEL 轮换防重放（BACKEND.md §15.3）。
 *
 * @author calwen
 * @date 2026/8/12
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    /** 访问令牌有效期（秒）：15 分钟短时效。 */
    private static final long ACCESS_TOKEN_TTL_SECONDS = 15 * 60L;
    /** 登录失败统一延迟（毫秒）：防止通过响应时间差异枚举账号。 */
    private static final long LOGIN_FAIL_DELAY_MILLIS = 300L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final JwtEncoder jwtEncoder;
    private final String jwtIssuer;

    public AuthServiceImpl(UserMapper userMapper, WorkspaceMapper workspaceMapper,
                           WorkspaceMemberMapper memberMapper, PasswordEncoder passwordEncoder,
                           RefreshTokenService refreshTokenService, JwtEncoder jwtEncoder,
                           @Value("${xlumen.jwt.issuer:xlumen}") String jwtIssuer) {
        this.userMapper = userMapper;
        this.workspaceMapper = workspaceMapper;
        this.memberMapper = memberMapper;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.jwtEncoder = jwtEncoder;
        this.jwtIssuer = jwtIssuer;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TokenVO register(RegisterDTO dto) {
        String username = dto.username().trim();
        // 统一提示防枚举：用户名/邮箱冲突均返回同一消息
        if (userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getUsername, username)) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "用户名或邮箱已被使用");
        }
        String email = StrUtil.blankToDefault(dto.email(), "").trim();
        if (StrUtil.isNotBlank(email) && userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getEmail, email)) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "用户名或邮箱已被使用");
        }

        UserEntity user = new UserEntity();
        user.setId(IdUtil.getSnowflakeNextId());
        user.setUsername(username);
        user.setEmail(StrUtil.isBlank(email) ? null : email);
        user.setPasswordHash(passwordEncoder.encode(dto.password()));
        user.setStatus(1);
        userMapper.insert(user);

        // 注册即建空间（决策 D9）：默认空间 + OWNER 成员绑定
        WorkspaceEntity workspace = createDefaultWorkspace(user.getId(), username);
        bindMember(workspace.getId(), user.getId(), RoleCode.OWNER);

        return issueTokens(user, workspace.getId(), List.of(RoleCode.OWNER.name()));
    }

    @Override
    public TokenVO login(LoginDTO dto) {
        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getUsername, dto.username().trim()));
        if (user == null || user.getStatus() == null || user.getStatus() != 1
                || !passwordEncoder.matches(dto.password(), user.getPasswordHash())) {
            delayForAntiEnumeration();
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        WorkspaceMemberEntity member = findActiveMember(user.getId());
        if (member == null) {
            delayForAntiEnumeration();
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        return issueTokens(user, member.getWorkspaceId(), List.of(member.getRoleCode()));
    }

    @Override
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    @Override
    public TokenVO refresh(RefreshTokenDTO dto) {
        RefreshTokenService.RefreshSession session = refreshTokenService.rotate(dto.refreshToken());
        if (session == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "会话已失效，请重新登录");
        }
        UserEntity user = userMapper.selectById(session.userId());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "会话已失效，请重新登录");
        }
        WorkspaceMemberEntity member = findActiveMember(user.getId());
        if (member == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "会话已失效，请重新登录");
        }
        return issueTokens(user, member.getWorkspaceId(), List.of(member.getRoleCode()));
    }

    /** 签发访问令牌（JWT）+ 刷新令牌（Redis 哈希存储）。 */
    private TokenVO issueTokens(UserEntity user, Long workspaceId, List<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtIssuer)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ACCESS_TOKEN_TTL_SECONDS))
                .subject(String.valueOf(user.getId()))
                .claim("workspaceId", workspaceId)
                .claim("roles", roles)
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        String refreshToken = refreshTokenService.create(user.getId(), workspaceId);
        return new TokenVO(accessToken, refreshToken, ACCESS_TOKEN_TTL_SECONDS, workspaceId,
                new UserProfileVO(user.getId(), user.getUsername(), user.getEmail(), roles));
    }

    /** 注册即建空间：空间名取用户名，slug 用用户名 + 随机后缀保证唯一（uk_workspace_slug）。 */
    private WorkspaceEntity createDefaultWorkspace(Long ownerUserId, String username) {
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setId(IdUtil.getSnowflakeNextId());
        workspace.setName(username);
        workspace.setSlug(slugOf(username));
        workspace.setOwnerUserId(ownerUserId);
        workspace.setStatus(1);
        workspaceMapper.insert(workspace);
        return workspace;
    }

    private String slugOf(String username) {
        String base = username.toLowerCase(Locale.ROOT);
        String slug = base + "-" + SECURE_RANDOM.nextInt(100000, 999999);
        // 极端撞 slug 时追加重试（uk_workspace_slug 唯一键兜底）
        while (workspaceMapper.selectCount(Wrappers.<WorkspaceEntity>lambdaQuery()
                .eq(WorkspaceEntity::getSlug, slug)) > 0) {
            slug = base + "-" + SECURE_RANDOM.nextInt(100000, 999999);
        }
        return slug;
    }

    private void bindMember(Long workspaceId, Long userId, RoleCode roleCode) {
        WorkspaceMemberEntity member = new WorkspaceMemberEntity();
        member.setId(IdUtil.getSnowflakeNextId());
        member.setWorkspaceId(workspaceId);
        member.setUserId(userId);
        member.setRoleCode(roleCode.name());
        member.setStatus(1);
        memberMapper.insert(member);
    }

    /** 查询用户当前生效的成员绑定（MVP 单空间：取第一条）。 */
    private WorkspaceMemberEntity findActiveMember(Long userId) {
        return memberMapper.selectOne(Wrappers.<WorkspaceMemberEntity>lambdaQuery()
                .eq(WorkspaceMemberEntity::getUserId, userId)
                .eq(WorkspaceMemberEntity::getStatus, 1)
                .last("LIMIT 1"));
    }

    /** 登录失败统一延迟：防止通过响应时间差异枚举账号（PRODUCT §10）。 */
    private void delayForAntiEnumeration() {
        try {
            Thread.sleep(LOGIN_FAIL_DELAY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
