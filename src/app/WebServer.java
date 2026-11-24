package app;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import model.ContractEmployee;
import model.Employee;
import model.FullTimeEmployee;
import model.InvalidEmployeeException;
import model.PartTimeEmployee;
import service.PayrollManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WebServer {

    private static final int PORT = 8080;
    private static final String WEB_ROOT = "web";
    private static final Gson gson = new GsonBuilder().serializeNulls().create();
    private static final PayrollManager manager = new PayrollManager();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        // API Context Handler
        server.createContext("/api", new ApiHandler());

        // Static Web Files Handler
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null); // default multithreaded executor
        server.start();
        System.out.println("==================================================");
        System.out.println("   E-Zorax Web Server is Running!                 ");
        System.out.println("   URL: http://localhost:" + PORT + "             ");
        System.out.println("==================================================");
    }

    // -------------------------------------------------------------
    // REST API Handler
    // -------------------------------------------------------------
    static class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Set CORS Headers
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");

            String method = exchange.getRequestMethod().toUpperCase();
            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getRawQuery());

            try {
                // Route: /api/summary
                if (path.equals("/api/summary") && "GET".equals(method)) {
                    handleGetSummary(exchange);
                }
                // Route: /api/departments
                else if (path.equals("/api/departments") && "GET".equals(method)) {
                    handleGetDepartments(exchange);
                }
                // Route: /api/employees (GET or POST)
                else if (path.equals("/api/employees") || path.equals("/api/employees/")) {
                    if ("GET".equals(method)) {
                        handleGetEmployees(exchange, queryParams);
                    } else if ("POST".equals(method)) {
                        handleCreateEmployee(exchange);
                    } else {
                        sendError(exchange, 405, "Method Not Allowed");
                    }
                }
                // Route: /api/employees/{id} (GET, PUT, DELETE)
                else if (path.startsWith("/api/employees/")) {
                    String id = path.substring("/api/employees/".length()).trim();
                    if (id.isEmpty()) {
                        sendError(exchange, 400, "Employee ID is required");
                        return;
                    }
                    if ("GET".equals(method)) {
                        handleGetEmployeeById(exchange, id);
                    } else if ("PUT".equals(method)) {
                        handleUpdateEmployee(exchange, id);
                    } else if ("DELETE".equals(method)) {
                        handleDeleteEmployee(exchange, id);
                    } else {
                        sendError(exchange, 405, "Method Not Allowed");
                    }
                }
                // Route: /api/payslip/{id}
                else if (path.startsWith("/api/payslip/")) {
                    String id = path.substring("/api/payslip/".length()).trim();
                    if ("GET".equals(method)) {
                        handleGetPayslip(exchange, id);
                    } else {
                        sendError(exchange, 405, "Method Not Allowed");
                    }
                }
                // Route: /api/payroll/run
                else if (path.equals("/api/payroll/run") && "POST".equals(method)) {
                    handleRunPayroll(exchange);
                }
                // Route: /api/payslips?month=YYYY-MM
                else if (path.equals("/api/payslips") && "GET".equals(method)) {
                    handleGetPayslips(exchange, queryParams);
                }
                // Route: /api/raise
                else if (path.equals("/api/raise") && "POST".equals(method)) {
                    handleDepartmentRaise(exchange);
                }
                // Route: /api/audit
                else if (path.equals("/api/audit") && "GET".equals(method)) {
                    handleGetAudit(exchange);
                }
                else {
                    sendError(exchange, 404, "Endpoint not found: " + path);
                }
            } catch (InvalidEmployeeException e) {
                sendError(exchange, 400, e.getMessage());
            } catch (SQLException e) {
                String msg = e.getMessage();
                if (msg != null && msg.contains("Duplicate entry")) {
                    sendError(exchange, 409, msg);
                } else {
                    sendError(exchange, 500, "Database Error: " + msg);
                }
            } catch (Exception e) {
                sendError(exchange, 500, "Server Error: " + e.getMessage());
            }
        }

        private void handleGetSummary(HttpExchange exchange) throws Exception {
            Map<String, Object> summary = manager.getSummary();
            sendJson(exchange, 200, summary);
        }

        private void handleGetDepartments(HttpExchange exchange) throws Exception {
            List<Map<String, Object>> depts = manager.getDepartments();
            sendJson(exchange, 200, depts);
        }

        private void handleGetEmployees(HttpExchange exchange, Map<String, String> query) throws Exception {
            String search = query.get("search");
            String type = query.get("type");
            String deptStr = query.get("dept");
            Integer deptId = null;
            if (deptStr != null && !deptStr.trim().isEmpty()) {
                try {
                    deptId = Integer.parseInt(deptStr.trim());
                } catch (NumberFormatException ignored) {}
            }

            List<Employee> list = manager.getEmployeesFiltered(search, type, deptId);
            List<Map<String, Object>> response = list.stream()
                    .map(Employee::toMap)
                    .collect(Collectors.toList());
            sendJson(exchange, 200, response);
        }

        private void handleGetEmployeeById(HttpExchange exchange, String id) throws Exception {
            Employee emp = manager.findById(id);
            if (emp == null) {
                sendError(exchange, 404, "Employee not found: " + id);
                return;
            }
            sendJson(exchange, 200, emp.toMap());
        }

        private void handleCreateEmployee(HttpExchange exchange) throws Exception {
            String body = readBody(exchange);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            String id = getJsonString(json, "id");
            String name = getJsonString(json, "name");
            int deptId = getJsonInt(json, "deptId", 1);
            String type = getJsonString(json, "type");

            Employee emp;
            switch (type.toUpperCase()) {
                case "FULL_TIME":
                    double basic = getJsonDouble(json, "basic", 0.0);
                    emp = new FullTimeEmployee(id, name, deptId, basic);
                    break;
                case "PART_TIME":
                    int hours = getJsonInt(json, "hoursWorked", 0);
                    double rate = getJsonDouble(json, "hourlyRate", 0.0);
                    emp = new PartTimeEmployee(id, name, deptId, hours, rate);
                    break;
                case "CONTRACT":
                    double fee = getJsonDouble(json, "feePerProject", 0.0);
                    int projects = getJsonInt(json, "projectsDone", 0);
                    emp = new ContractEmployee(id, name, deptId, fee, projects);
                    break;
                default:
                    throw new InvalidEmployeeException("Invalid employee type: " + type);
            }

            manager.addEmployee(emp);

            // Re-read with department name and updated timestamp
            Employee created = manager.findById(emp.getId());
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("message", "Employee " + emp.getName() + " (" + emp.getId() + ") added successfully!");
            resp.put("employee", created != null ? created.toMap() : emp.toMap());
            sendJson(exchange, 201, resp);
        }

        private void handleUpdateEmployee(HttpExchange exchange, String id) throws Exception {
            String body = readBody(exchange);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            String name = getJsonString(json, "name");
            int deptId = getJsonInt(json, "deptId", 1);
            String type = getJsonString(json, "type");

            Employee emp;
            switch (type.toUpperCase()) {
                case "FULL_TIME":
                    double basic = getJsonDouble(json, "basic", 0.0);
                    emp = new FullTimeEmployee(id, name, deptId, basic);
                    break;
                case "PART_TIME":
                    int hours = getJsonInt(json, "hoursWorked", 0);
                    double rate = getJsonDouble(json, "hourlyRate", 0.0);
                    emp = new PartTimeEmployee(id, name, deptId, hours, rate);
                    break;
                case "CONTRACT":
                    double fee = getJsonDouble(json, "feePerProject", 0.0);
                    int projects = getJsonInt(json, "projectsDone", 0);
                    emp = new ContractEmployee(id, name, deptId, fee, projects);
                    break;
                default:
                    throw new InvalidEmployeeException("Invalid employee type: " + type);
            }

            manager.updateEmployee(emp);

            Employee updated = manager.findById(id);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("message", "Employee " + id + " updated successfully!");
            resp.put("employee", updated != null ? updated.toMap() : emp.toMap());
            sendJson(exchange, 200, resp);
        }

        private void handleDeleteEmployee(HttpExchange exchange, String id) throws Exception {
            boolean deleted = manager.deleteEmployee(id);
            if (!deleted) {
                sendError(exchange, 404, "Employee not found with ID: " + id);
                return;
            }
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("message", "Employee " + id + " deleted successfully!");
            sendJson(exchange, 200, resp);
        }

        private void handleGetPayslip(HttpExchange exchange, String id) throws Exception {
            Employee emp = manager.findById(id);
            if (emp == null) {
                sendError(exchange, 404, "Employee not found with ID: " + id);
                return;
            }

            Map<String, Object> payslip = new LinkedHashMap<>();
            payslip.put("id", emp.getId());
            payslip.put("name", emp.getName());
            payslip.put("deptId", emp.getDeptId());
            payslip.put("deptName", emp.getDeptName());
            payslip.put("type", emp.getType());
            payslip.put("netSalary", emp.calculateSalary());
            payslip.put("breakdown", emp.getBreakdown());
            payslip.put("bonusPercent", emp.calculateBonus(10));
            payslip.put("bonusFixed", emp.calculateBonus(5000.0));
            payslip.put("company", Employee.COMPANY);
            payslip.put("updatedAt", emp.getUpdatedAt());

            sendJson(exchange, 200, payslip);
        }

        private void handleRunPayroll(HttpExchange exchange) throws Exception {
            String body = readBody(exchange);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            String month = getJsonString(json, "month");
            if (month == null || month.trim().isEmpty()) {
                sendError(exchange, 400, "Month is required (e.g. '2026-11')");
                return;
            }

            month = month.trim();
            int count = manager.runMonthlyPayroll(month);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("message", "Successfully generated " + count + " payslips for " + month + ".");
            resp.put("count", count);
            resp.put("month", month);
            sendJson(exchange, 200, resp);
        }

        private void handleGetPayslips(HttpExchange exchange, Map<String, String> query) throws Exception {
            String month = query.get("month");
            if (month == null || month.trim().isEmpty()) {
                sendError(exchange, 400, "Query parameter 'month' is required (e.g. ?month=2026-11)");
                return;
            }
            List<Map<String, Object>> list = manager.getPayslips(month.trim());
            sendJson(exchange, 200, list);
        }

        private void handleDepartmentRaise(HttpExchange exchange) throws Exception {
            String body = readBody(exchange);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            int deptId = getJsonInt(json, "deptId", 0);
            double percent = getJsonDouble(json, "percent", 0.0);

            if (deptId <= 0 || percent <= 0) {
                sendError(exchange, 400, "Valid department ID and positive raise percentage are required.");
                return;
            }

            manager.giveRaise(deptId, percent);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("message", String.format("Successfully applied a %.2f%% raise to Department %d.", percent, deptId));
            resp.put("deptId", deptId);
            resp.put("percent", percent);
            sendJson(exchange, 200, resp);
        }

        private void handleGetAudit(HttpExchange exchange) throws Exception {
            List<Map<String, Object>> list = manager.getAuditLog();
            sendJson(exchange, 200, list);
        }
    }

    // -------------------------------------------------------------
    // Static File Serving Handler
    // -------------------------------------------------------------
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestPath = exchange.getRequestURI().getPath();

            // Default to index.html
            if (requestPath.equals("/") || requestPath.isEmpty()) {
                requestPath = "/index.html";
            } else if (requestPath.equals("/employees")) {
                requestPath = "/employees.html";
            } else if (requestPath.equals("/payroll")) {
                requestPath = "/payroll.html";
            } else if (requestPath.equals("/reports")) {
                requestPath = "/reports.html";
            } else if (requestPath.equals("/audit")) {
                requestPath = "/audit.html";
            }

            File file = new File(WEB_ROOT, requestPath);
            if (!file.exists() || file.isDirectory()) {
                String notFound = "<h1>404 Not Found</h1><p>The requested file " + requestPath + " was not found on this server.</p>";
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(404, notFound.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            String contentType = getContentType(file.getName());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.sendResponseHeaders(200, file.length());

            try (InputStream is = new FileInputStream(file);
                 OutputStream os = exchange.getResponseBody()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
        }

        private String getContentType(String fileName) {
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=UTF-8";
            if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
            if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
            if (lower.endsWith(".svg")) return "image/svg+xml";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".ico")) return "image/x-icon";
            return "text/plain; charset=UTF-8";
        }
    }

    // -------------------------------------------------------------
    // Helper Utilities
    // -------------------------------------------------------------
    private static void sendJson(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = gson.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, String> err = new HashMap<>();
        err.put("error", message);
        sendJson(exchange, statusCode, err);
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parseQueryParams(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            } else if (idx < 0 && !pair.isEmpty()) {
                String key = URLDecoder.decode(pair, StandardCharsets.UTF_8);
                params.put(key, "");
            }
        }
        return params;
    }

    private static String getJsonString(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsString().trim();
        }
        return "";
    }

    private static int getJsonInt(JsonObject json, String key, int defaultVal) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            try {
                return json.get(key).getAsInt();
            } catch (Exception ignored) {}
        }
        return defaultVal;
    }

    private static double getJsonDouble(JsonObject json, String key, double defaultVal) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            try {
                return json.get(key).getAsDouble();
            } catch (Exception ignored) {}
        }
        return defaultVal;
    }
}
