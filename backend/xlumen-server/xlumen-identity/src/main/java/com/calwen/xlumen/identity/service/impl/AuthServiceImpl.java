package com.calwen.xlumen.identity.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.calwen.xlumen.common.exception.BizException;
import com.calwen.xlumen.common.web.ErrorCode;
import com.calwen.xlumen.identity.dto.LoginDTO;
import com.calwen.xlumen.identity.dto.RefreshTokenDTO;
import com.calwen.xlumen.identity.dto.RegisterDTO;
import com.calwen.xlumen.identity.entity.UserEntity;
import com.calwen.xlumen.identity.entity.WorkspaceEntity;
import com.calwen.xlumen.identity.entity.WorkspaceMemberEntity;
import com.calwen.xlumen.identity.enums.RoleCode;
import com.calwen.xlumen.identity.mapper.UserMapper;
import com.calwen.xlumen.identity.mapper.WorkspaceMapper;
import com.calwen.xlumen.identity.mapper.WorkspaceMemberMapper;
import com.calwen.xlumen.identity.service.AuthService;
import com.calwen.xlumen.identity.service.MailService;
import com.calwen.xlumen.identity.service.RefreshSession;
import com.calwen.xlumen.identity.service.RefreshTokenService;
import com.calwen.xlumen.identity.vo.TokenVO;
import com.calwen.xlumen.identity.vo.UserProfileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * 认证服务实现：注册（注册即建空间，决策 D9）/登录/登出/刷新/忘记密码（邮箱验证码）。
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
    /** 密码重置验证码有效期。 */
    private static final Duration PWD_RESET_TTL = Duration.ofMinutes(10);
    /** 密码重置验证码错误次数上限（超限作废）。 */
    private static final int PWD_RESET_MAX_ATTEMPTS = 5;
    private static final String PWD_RESET_KEY_PREFIX = "xlumen:pwdreset:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final JwtEncoder jwtEncoder;
    private final String jwtIssuer;
    private final StringRedisTemplate redisTemplate;
    private final MailService mailService;

    public AuthServiceImpl(UserMapper userMapper, WorkspaceMapper workspaceMapper,
                           WorkspaceMemberMapper memberMapper, PasswordEncoder passwordEncoder,
                           RefreshTokenService refreshTokenService, JwtEncoder jwtEncoder,
                           @Value("${xlumen.jwt.issuer:xlumen}") String jwtIssuer,
                           StringRedisTemplate redisTemplate, MailService mailService) {
        this.userMapper = userMapper;
        this.workspaceMapper = workspaceMapper;
        this.memberMapper = memberMapper;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.jwtEncoder = jwtEncoder;
        this.jwtIssuer = jwtIssuer;
        this.redisTemplate = redisTemplate;
        this.mailService = mailService;
    }

    @Override
    public void forgotPassword(String email) {
        String normalized = email.trim().toLowerCase();
        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getEmail, normalized).last("LIMIT 1"));
        if (user == null) {
            // 统一成功响应，不泄露账号是否存在
            return;
        }
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1000000));
        try {
            redisTemplate.opsForValue().set(PWD_RESET_KEY_PREFIX + normalized, code, PWD_RESET_TTL);
        } catch (Exception e) {
            log.warn("密码重置验证码写入失败（Redis 不可用）email={}", normalized, e);
        }
        mailService.send(normalized, "xLumen 密码重置验证码",
                "你的密码重置验证码是：" + code + "，10 分钟内有效。如非本人操作请忽略本邮件。");
    }

    @Override
    public void resetPassword(String email, String code, String newPassword) {
        String normalized = email.trim().toLowerCase();
        String key = PWD_RESET_KEY_PREFIX + normalized;
        String stored;
        try {
            stored = redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INVALID_PARAM, "验证码已失效，请重新获取");
        }
        if (stored == null || !stored.equals(code.trim())) {
            failAttempt(key);
            throw new BizException(ErrorCode.INVALID_PARAM, "验证码错误或已过期，请重新获取");
        }
        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getEmail, normalized).last("LIMIT 1"));
        if (user == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "账号不存在");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        redisTemplate.delete(key);
        redisTemplate.delete(key + ":attempts");
    }

    /** 验证码错误计数：连续错误达上限后作废验证码。 */
    private void failAttempt(String key) {
        try {
            Long attempts = redisTemplate.opsForValue().increment(key + ":attempts");
            if (attempts != null && attempts >= PWD_RESET_MAX_ATTEMPTS) {
                redisTemplate.delete(key);
            }
        } catch (Exception ignored) {
            // Redis 异常不阻断主流程，靠 TTL 自然失效
        }
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
        RefreshSession session = refreshTokenService.rotate(dto.refreshToken());
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
                .claim("username", user.getUsername())
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
