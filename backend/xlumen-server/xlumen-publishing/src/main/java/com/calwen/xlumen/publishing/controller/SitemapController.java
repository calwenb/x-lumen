package com.calwen.xlumen.publishing.controller;

import cn.hutool.core.util.StrUtil;
import com.calwen.xlumen.common.context.WorkspaceContext;
import com.calwen.xlumen.content.api.ContentApi;
import com.calwen.xlumen.content.api.dto.ContentPageResult;
import com.calwen.xlumen.content.api.dto.PublishedKnowledgeDTO;
import com.calwen.xlumen.knowledge.api.KnowledgeApi;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * SEO 资产端点（公开）：sitemap.xml 与 robots.txt，xml 内容按已发布可见知识生成。
 *
 * @author calwen
 * @date 2026/8/26
 */
@RestController
@RequestMapping("/api/v1/public")
public class SitemapController {

    private static final DateTimeFormatter SITEMAP_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private ContentApi contentApi;
    @Resource
    private KnowledgeApi knowledgeApi;

    @Value("${XLUMEN_SITE_URL:https://www.xlumen.dev}")
    private String siteUrl;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (PublishedKnowledgeDTO doc : publishedDocs()) {
            String loc = siteUrl + "/knowledge/" + doc.getId();
            String lastmod = doc.getPublishedAt() == null ? "" : doc.getPublishedAt().format(SITEMAP_DATE);
            sb.append("  <url><loc>").append(loc).append("</loc>")
                    .append(lastmod.isEmpty() ? "" : "<lastmod>" + lastmod + "</lastmod>")
                    .append("</url>\n");
        }
        sb.append("</urlset>\n");
        return sb.toString();
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        return "User-agent: *\nAllow: /\n\nSitemap: " + siteUrl + "/api/v1/public/sitemap.xml\n";
    }

    private List<PublishedKnowledgeDTO> publishedDocs() {
        List<Long> visible = knowledgeApi.resolveVisibleKbIds(WorkspaceContext.userId());
        if (visible == null || visible.isEmpty()) {
            return List.of();
        }
        ContentPageResult<PublishedKnowledgeDTO> page = contentApi.listPublished(null,
                com.calwen.xlumen.content.api.dto.KnowledgeQueryDTO.builder()
                        .visibleKbIds(visible).pageNo(1L).pageSize(100L).build());
        return page.getRecords();
    }

    @SuppressWarnings("unused")
    private static String escape(String text) {
        return StrUtil.isBlank(text) ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}