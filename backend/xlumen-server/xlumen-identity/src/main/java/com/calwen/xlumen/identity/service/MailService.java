package com.calwen.xlumen.identity.service;

import cn.hutool.core.util.StrUtil;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 邮件发送服务：SMTP 配置未就绪时进入开发模式（仅日志输出正文，不真实发信），
 * 保证忘记密码等流程在本地无邮件环境中可联调。
 *
 * @author calwen
 * @date 2026/8/26
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                       @Value("${XLUMEN_MAIL_FROM:}") String from) {
        this.mailSenderProvider = mailSenderProvider;
        this.from = from;
    }

    /** 是否已配置可用发信（SMTP 服务可用且四要素齐备）。 */
    public boolean isConfigured() {
        return mailSenderProvider.getIfAvailable() != null && StrUtil.isNotBlank(from);
    }

    /** 发送简单文本邮件；配置缺失时走开发模式（日志输出）并返回 false。 */
    public boolean send(String to, String subject, String body) {
        if (StrUtil.isBlank(to) || StrUtil.isBlank(body)) {
            return false;
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null || StrUtil.isBlank(from)) {
            log.info("[开发模式邮件] to={} subject={} body={}", to, subject, body);
            return true;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.warn("邮件发送失败 to={}", to, e);
            return false;
        }
    }
}