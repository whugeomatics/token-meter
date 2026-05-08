package local.agent.dashboard.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import local.agent.dashboard.domain.Report;
import local.agent.dashboard.domain.ReportQuery;
import local.agent.dashboard.domain.TeamIngestResult;
import local.agent.dashboard.ingestion.CodexIngestionService;
import local.agent.dashboard.ingestion.IngestionResult;
import local.agent.dashboard.ingestion.TeamIngestionService;
import local.agent.dashboard.report.ReportService;
import local.agent.dashboard.report.TeamReportService;
import local.agent.dashboard.store.TeamUsageStore;
import local.agent.dashboard.util.Json;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public final class DashboardServer {
    private final int port;
    private final ReportService reportService;
    private final CodexIngestionService localIngestionService;
    private final TeamIngestionService teamIngestionService;
    private final TeamReportService teamReportService;
    private final AdminAuth adminAuth;
    private final AdminService adminService;
    private final Object localIngestionLock = new Object();

    public DashboardServer(int port, ReportService reportService, CodexIngestionService localIngestionService,
                           TeamIngestionService teamIngestionService, TeamReportService teamReportService,
                           TeamUsageStore teamUsageStore, String adminToken) {
        this.port = port;
        this.reportService = reportService;
        this.localIngestionService = localIngestionService;
        this.teamIngestionService = teamIngestionService;
        this.teamReportService = teamReportService;
        this.adminAuth = new AdminAuth(adminToken);
        this.adminService = new AdminService(teamUsageStore);
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/report", this::handleReport);
        server.createContext("/api/ingest", this::handleLocalIngest);
        server.createContext("/api/team/ingest", this::handleTeamIngest);
        server.createContext("/api/team/report", this::handleTeamReport);
        server.createContext("/api/admin/login", this::handleAdminLogin);
        server.createContext("/api/admin/device-tokens", this::handleAdminDeviceTokens);
        server.createContext("/health", exchange -> writeJson(exchange, 200, "{\"status\":\"ok\"}"));
        server.createContext("/", this::handleDashboard);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
    }

    private void handleDashboard(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method_not_allowed", "Only GET is supported"));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if ("/app.css".equals(path)) {
            writeText(exchange, 200, "text/css; charset=utf-8", DashboardPage.resource("/static/app.css"));
            return;
        }
        if ("/app.js".equals(path)) {
            writeText(exchange, 200, "application/javascript; charset=utf-8", DashboardPage.resource("/static/app.js"));
            return;
        }
        if ("/admin.css".equals(path)) {
            writeText(exchange, 200, "text/css; charset=utf-8", DashboardPage.resource("/static/admin.css"));
            return;
        }
        if ("/admin.js".equals(path)) {
            writeText(exchange, 200, "application/javascript; charset=utf-8", DashboardPage.resource("/static/admin.js"));
            return;
        }
        if ("/admin-login.html".equals(path)) {
            if (!adminAuth.enabled()) {
                writeJson(exchange, 404, error("not_found", "Not found"));
                return;
            }
            writeText(exchange, 200, "text/html; charset=utf-8", DashboardPage.resource("/static/admin-login.html"));
            return;
        }
        if ("/admin.html".equals(path)) {
            if (!adminAuth.enabled()) {
                writeJson(exchange, 404, error("not_found", "Not found"));
                return;
            }
            if (!adminAuth.authorized(exchange)) {
                writeJson(exchange, 401, error("unauthorized", "Admin login required"));
                return;
            }
            writeText(exchange, 200, "text/html; charset=utf-8", DashboardPage.resource("/static/admin.html"));
            return;
        }
        if (!"/".equals(path) && !"/index.html".equals(path)) {
            writeJson(exchange, 404, error("not_found", "Not found"));
            return;
        }
        writeText(exchange, 200, "text/html; charset=utf-8", DashboardPage.resource("/static/index.html"));
    }

    private void handleReport(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method_not_allowed", "Only GET is supported"));
            return;
        }

        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        try {
            ReportQuery reportQuery = ReportQuery.from(query, reportService.zone());
            Report report = reportService.report(reportQuery);
            writeJson(exchange, 200, report.toJson());
        } catch (BadRequestException e) {
            writeJson(exchange, 400, error("invalid_query", e.getMessage()));
        } catch (Exception e) {
            writeJson(exchange, 500, error("internal_error", e.getMessage()));
        }
    }

    private void handleLocalIngest(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method_not_allowed", "Only POST is supported"));
            return;
        }
        try {
            IngestionResult result;
            synchronized (localIngestionLock) {
                result = localIngestionService.ingest();
            }
            writeJson(exchange, result.errors().isEmpty() ? 200 : 500, result.toJson());
        } catch (Exception e) {
            writeJson(exchange, 500, error("internal_error", e.getClass().getSimpleName()));
        }
    }

    private void handleTeamReport(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method_not_allowed", "Only GET is supported"));
            return;
        }
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        try {
            ReportQuery reportQuery = ReportQuery.from(query, teamReportService.zone());
            writeJson(exchange, 200, teamReportService.report(reportQuery).toJson());
        } catch (BadRequestException e) {
            writeJson(exchange, 400, error("invalid_query", e.getMessage()));
        } catch (Exception e) {
            writeJson(exchange, 500, error("internal_error", e.getClass().getSimpleName()));
        }
    }

    private void handleTeamIngest(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method_not_allowed", "Only POST is supported"));
            return;
        }
        String token = bearerToken(exchange.getRequestHeaders().getFirst("Authorization"));
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            TeamIngestResult result = teamIngestionService.ingest(token, body);
            int status = switch (result.errorCode() == null ? "" : result.errorCode()) {
                case "unauthorized" -> 401;
                case "forbidden" -> 403;
                case "identity_conflict" -> 409;
                case "invalid_payload" -> 400;
                case "payload_too_large" -> 413;
                case "storage_error" -> 500;
                default -> 200;
            };
            writeJson(exchange, status, result.toJson());
        } catch (Exception e) {
            writeJson(exchange, 500, error("internal_error", e.getClass().getSimpleName()));
        }
    }

    private void handleAdminLogin(HttpExchange exchange) throws IOException {
        if (!adminAuth.enabled()) {
            writeJson(exchange, 404, error("not_found", "Not found"));
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method_not_allowed", "Only POST is supported"));
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String token = Json.firstString(body, "admin_token").orElse("");
        if (!adminAuth.matches(token)) {
            writeJson(exchange, 401, error("unauthorized", "Invalid admin token"));
            return;
        }
        exchange.getResponseHeaders().add("Set-Cookie", adminAuth.loginCookie());
        writeJson(exchange, 200, "{\"status\":\"ok\"}");
    }

    private void handleAdminDeviceTokens(HttpExchange exchange) throws IOException {
        if (!adminAuth.enabled()) {
            writeJson(exchange, 404, error("not_found", "Not found"));
            return;
        }
        if (!adminAuth.authorized(exchange)) {
            writeJson(exchange, 401, error("unauthorized", "Admin login required"));
            return;
        }
        try {
            String path = exchange.getRequestURI().getPath();
            String base = "/api/admin/device-tokens";
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && base.equals(path)) {
                writeJson(exchange, 200, adminService.listDeviceTokens());
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && base.equals(path)) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                writeJson(exchange, 200, adminService.createDeviceToken(body));
                return;
            }
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && path.endsWith("/token")) {
                writeJson(exchange, 200, adminService.getDeviceTokenSecret(adminTokenId(path)));
                return;
            }
            if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, 200, adminService.deleteDeviceToken(adminTokenId(path)));
                return;
            }
            writeJson(exchange, 405, error("method_not_allowed", "Unsupported admin token operation"));
        } catch (BadRequestException e) {
            writeJson(exchange, 400, error("invalid_payload", e.getMessage()));
        } catch (Exception e) {
            writeJson(exchange, 500, error("internal_error", e.getClass().getSimpleName()));
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            int index = pair.indexOf('=');
            String key = index >= 0 ? pair.substring(0, index) : pair;
            String value = index >= 0 ? pair.substring(index + 1) : "";
            result.put(decode(key), decode(value));
        }
        return result;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String bearerToken(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return "";
        }
        return header.substring("Bearer ".length()).trim();
    }

    private static long adminTokenId(String path) {
        String base = "/api/admin/device-tokens/";
        if (!path.startsWith(base)) {
            throw new BadRequestException("invalid device token path");
        }
        String suffix = path.substring(base.length());
        if (suffix.endsWith("/token")) {
            suffix = suffix.substring(0, suffix.length() - "/token".length());
        }
        if (suffix.isBlank()) {
            throw new BadRequestException("device token id is required");
        }
        try {
            return Long.parseLong(suffix);
        } catch (NumberFormatException e) {
            throw new BadRequestException("device token id must be numeric");
        }
    }

    private static String error(String code, String message) {
        return "{\"error\":{\"code\":\"" + Json.escape(code) + "\",\"message\":\"" + Json.escape(message) + "\"}}";
    }

    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void writeText(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
