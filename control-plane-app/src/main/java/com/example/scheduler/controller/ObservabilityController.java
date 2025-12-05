package com.example.scheduler.controller;

import com.example.scheduler.config.ObservabilityProperties;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 控制面观测接口，统一暴露 Grafana 嵌入链接等能力。
 */
@Slf4j
@RestController
@RequestMapping("/api/observability")
@RequiredArgsConstructor
public class ObservabilityController {

    private final ObservabilityProperties observabilityProperties;

    @GetMapping("/grafana/dashboards")
    public List<ObservabilityProperties.DashboardConfig> dashboards() {
        return observabilityProperties.getGrafana().getDashboards();
    }

    @GetMapping("/grafana/embed-url")
    public GrafanaEmbedResponse grafanaEmbed(
            @RequestParam("dashboardUid") String dashboardUid,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "refresh", required = false) String refresh,
            @RequestParam Map<String, String> queryParams) {

        ObservabilityProperties.GrafanaConfig grafana = observabilityProperties.getGrafana();
        String baseUrl = trimTrailingSlash(grafana.getUrl());
        String resolvedFrom = StringUtils.hasText(from) ? from : grafana.getDashboards().stream()
                .filter(d -> d.getUid().equals(dashboardUid))
                .findFirst()
                .map(ObservabilityProperties.DashboardConfig::getDefaultFrom)
                .orElse("now-15m");
        String resolvedTo = StringUtils.hasText(to) ? to : grafana.getDashboards().stream()
                .filter(d -> d.getUid().equals(dashboardUid))
                .findFirst()
                .map(ObservabilityProperties.DashboardConfig::getDefaultTo)
                .orElse("now");
        String resolvedRefresh = StringUtils.hasText(refresh) ? refresh : grafana.getDefaultRefresh();

        Map<String, String> variables = queryParams.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("var-"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/d/" + dashboardUid)
                .queryParam("orgId", grafana.getOrgId())
                .queryParam("refresh", resolvedRefresh)
                .queryParam("from", resolvedFrom)
                .queryParam("to", resolvedTo);

        variables.forEach(builder::queryParam);

        String externalUrl = builder.toUriString();
        String iframeUrl = builder.cloneBuilder()
                .queryParam("kiosk", "tv")
                .toUriString();

        return GrafanaEmbedResponse.builder()
                .dashboardUid(dashboardUid)
                .iframeUrl(iframeUrl)
                .externalUrl(externalUrl)
                .build();
    }

    private String trimTrailingSlash(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Data
    @Builder
    public static class GrafanaEmbedResponse {
        private String dashboardUid;
        private String iframeUrl;
        private String externalUrl;
    }
}
