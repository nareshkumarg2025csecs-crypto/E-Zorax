package service;

import db.DBConnection;
import model.ContractEmployee;
import model.Employee;
import model.FullTimeEmployee;
import model.InvalidEmployeeException;
import model.PartTimeEmployee;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class PayrollManager {

    private static final String BASE_JOIN_QUERY = 
        "SELECT e.emp_id, e.name, e.dept_id, d.dept_name, e.emp_type, e.updated_at, " +
        "       ft.basic, " +
        "       pt.hours_worked, pt.hourly_rate, " +
        "       c.fee_per_project, c.projects_done " +
        "FROM employee e " +
        "JOIN department d ON e.dept_id = d.dept_id " +
        "LEFT JOIN full_time ft ON e.emp_id = ft.emp_id " +
        "LEFT JOIN part_time pt ON e.emp_id = pt.emp_id " +
        "LEFT JOIN contract c ON e.emp_id = c.emp_id ";

    // Map a single ResultSet row to the appropriate Employee subclass
    private Employee mapRow(ResultSet rs) throws SQLException, InvalidEmployeeException {
        String id = rs.getString("emp_id");
        String name = rs.getString("name");
        int deptId = rs.getInt("dept_id");
        String deptName = rs.getString("dept_name");
        String type = rs.getString("emp_type");

        Employee emp;
        switch (type) {
            case "FULL_TIME":
                double basic = rs.getDouble("basic");
                emp = new FullTimeEmployee(id, name, deptId, basic);
                break;
            case "PART_TIME":
                int hours = rs.getInt("hours_worked");
                double rate = rs.getDouble("hourly_rate");
                emp = new PartTimeEmployee(id, name, deptId, hours, rate);
                break;
            case "CONTRACT":
                double fee = rs.getDouble("fee_per_project");
                int projects = rs.getInt("projects_done");
                emp = new ContractEmployee(id, name, deptId, fee, projects);
                break;
            default:
                throw new InvalidEmployeeException("Unknown employee type: " + type);
        }

        emp.setDeptName(deptName);
        try {
            Timestamp ts = rs.getTimestamp("updated_at");
            if (ts != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                emp.setUpdatedAt(sdf.format(ts));
            }
        } catch (SQLException ignored) {
            // column may not exist before web_upgrade
        }
        return emp;
    }

    // Retrieve all employees using ONE LEFT JOIN query
    public List<Employee> getAll() throws SQLException, InvalidEmployeeException {
        List<Employee> list = new ArrayList<>();
        String sql = BASE_JOIN_QUERY + "ORDER BY e.emp_id";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    // Retrieve employee by ID (case-insensitive) using ONE LEFT JOIN query
    public Employee findById(String id) throws SQLException, InvalidEmployeeException {
        if (id == null) {
            return null;
        }
        String sql = BASE_JOIN_QUERY + "WHERE UPPER(e.emp_id) = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, id.trim().toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    // Add employee within a database transaction
    public void addEmployee(Employee e) throws SQLException {
        String sqlEmp = "INSERT INTO employee (emp_id, name, dept_id, emp_type) VALUES (?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(sqlEmp)) {
                    ps.setString(1, e.getId());
                    ps.setString(2, e.getName());
                    ps.setInt(3, e.getDeptId());
                    ps.setString(4, e.getType());
                    ps.executeUpdate();
                }
                // Polymorphic persistence for subclass-specific table
                e.saveDetails(con);
                con.commit();
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    // Update existing employee within a database transaction
    public void updateEmployee(Employee e) throws SQLException {
        String sql = "UPDATE employee SET name = ?, dept_id = ?, updated_at = CURRENT_TIMESTAMP WHERE emp_id = ?";
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, e.getName());
                    ps.setInt(2, e.getDeptId());
                    ps.setString(3, e.getId());
                    int rows = ps.executeUpdate();
                    if (rows == 0) {
                        throw new SQLException("Employee not found with ID: " + e.getId());
                    }
                }
                // Polymorphic update for subclass-specific table (triggers trg_basic_audit if basic changes)
                e.updateDetails(con);
                con.commit();
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    // Delete employee by ID (cascades to full_time, part_time, contract, payslip)
    public boolean deleteEmployee(String id) throws SQLException {
        if (id == null) return false;
        String sql = "DELETE FROM employee WHERE UPPER(emp_id) = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, id.trim().toUpperCase());
            return ps.executeUpdate() > 0;
        }
    }

    // Retrieve departments list
    public List<Map<String, Object>> getDepartments() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT dept_id, dept_name FROM department ORDER BY dept_id";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", rs.getInt("dept_id"));
                map.put("name", rs.getString("dept_name"));
                list.add(map);
            }
        }
        return list;
    }

    // Retrieve historical payslips for a given month
    public List<Map<String, Object>> getPayslips(String monthYear) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT p.slip_id, p.emp_id, e.name, d.dept_name, e.emp_type, p.month_year, p.net_salary, p.generated_on " +
                     "FROM payslip p " +
                     "JOIN employee e ON p.emp_id = e.emp_id " +
                     "JOIN department d ON e.dept_id = d.dept_id " +
                     "WHERE p.month_year = ? " +
                     "ORDER BY p.net_salary DESC";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, monthYear);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("slipId", rs.getInt("slip_id"));
                    map.put("empId", rs.getString("emp_id"));
                    map.put("name", rs.getString("name"));
                    map.put("deptName", rs.getString("dept_name"));
                    map.put("empType", rs.getString("emp_type"));
                    map.put("monthYear", rs.getString("month_year"));
                    map.put("netSalary", rs.getDouble("net_salary"));
                    Timestamp ts = rs.getTimestamp("generated_on");
                    map.put("generatedOn", ts != null ? sdf.format(ts) : "");
                    list.add(map);
                }
            }
        }
        return list;
    }

    // Retrieve audit log rows, newest first
    public List<Map<String, Object>> getAuditLog() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT a.audit_id, a.emp_id, COALESCE(e.name, 'Unknown') AS name, a.old_basic, a.new_basic, a.changed_on " +
                     "FROM salary_audit a " +
                     "LEFT JOIN employee e ON a.emp_id = e.emp_id " +
                     "ORDER BY a.changed_on DESC, a.audit_id DESC";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("auditId", rs.getInt("audit_id"));
                map.put("empId", rs.getString("emp_id"));
                map.put("name", rs.getString("name"));
                map.put("oldBasic", rs.getDouble("old_basic"));
                map.put("newBasic", rs.getDouble("new_basic"));
                Timestamp ts = rs.getTimestamp("changed_on");
                map.put("changedOn", ts != null ? sdf.format(ts) : "");
                list.add(map);
            }
        }
        return list;
    }

    // Get comprehensive summary data for web dashboard
    public Map<String, Object> getSummary() throws SQLException, InvalidEmployeeException {
        List<Employee> employees = getAll();
        Map<String, Object> summary = new LinkedHashMap<>();

        int headcount = employees.size();
        summary.put("employeeCount", headcount);

        double totalPayout = employees.stream()
                .mapToDouble(Employee::calculateSalary)
                .sum();
        summary.put("totalPayout", totalPayout);

        Optional<Employee> topEmp = employees.stream()
                .max(Comparator.comparingDouble(Employee::calculateSalary));
        if (topEmp.isPresent()) {
            Employee top = topEmp.get();
            Map<String, Object> topMap = new LinkedHashMap<>();
            topMap.put("id", top.getId());
            topMap.put("name", top.getName());
            topMap.put("salary", top.calculateSalary());
            topMap.put("type", top.getType());
            topMap.put("deptName", top.getDeptName());
            summary.put("highestPaid", topMap);
        } else {
            summary.put("highestPaid", null);
        }

        // Payout by Type
        Map<String, Double> payoutByType = employees.stream()
                .collect(Collectors.groupingBy(
                        Employee::getType,
                        Collectors.summingDouble(Employee::calculateSalary)
                ));
        summary.put("payoutByType", payoutByType);

        // Headcount by Type
        Map<String, Long> countByType = employees.stream()
                .collect(Collectors.groupingBy(Employee::getType, Collectors.counting()));
        summary.put("countByType", countByType);

        // Department statistics from v_salary
        List<Map<String, Object>> deptStats = new ArrayList<>();
        String deptSql = "SELECT dept_name, COUNT(*) AS emp_count, SUM(net_salary) AS total_payout, ROUND(AVG(net_salary), 2) AS avg_salary " +
                         "FROM v_salary GROUP BY dept_name ORDER BY dept_name";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(deptSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> dMap = new LinkedHashMap<>();
                dMap.put("department", rs.getString("dept_name"));
                dMap.put("employeeCount", rs.getInt("emp_count"));
                dMap.put("totalPayout", rs.getDouble("total_payout"));
                dMap.put("averageSalary", rs.getDouble("avg_salary"));
                deptStats.add(dMap);
            }
        }
        summary.put("payoutByDept", deptStats);
        summary.put("departmentCount", deptStats.size());

        // Employees above company average
        List<Map<String, Object>> aboveAvg = new ArrayList<>();
        String aboveSql = "SELECT emp_id, name, dept_name, net_salary FROM v_salary " +
                          "WHERE net_salary > (SELECT AVG(net_salary) FROM v_salary) ORDER BY net_salary DESC";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(aboveSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> aMap = new LinkedHashMap<>();
                aMap.put("id", rs.getString("emp_id"));
                aMap.put("name", rs.getString("name"));
                aMap.put("department", rs.getString("dept_name"));
                aMap.put("netSalary", rs.getDouble("net_salary"));
                aboveAvg.add(aMap);
            }
        }
        summary.put("aboveAverage", aboveAvg);

        // Top earner per department
        List<Map<String, Object>> topByDept = new ArrayList<>();
        String topDeptSql = "SELECT s1.dept_name, s1.emp_id, s1.name, s1.net_salary " +
                            "FROM v_salary s1 WHERE s1.net_salary = (" +
                            "  SELECT MAX(s2.net_salary) FROM v_salary s2 WHERE s2.dept_name = s1.dept_name" +
                            ") ORDER BY s1.dept_name";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(topDeptSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> tMap = new LinkedHashMap<>();
                tMap.put("department", rs.getString("dept_name"));
                tMap.put("id", rs.getString("emp_id"));
                tMap.put("name", rs.getString("name"));
                tMap.put("netSalary", rs.getDouble("net_salary"));
                topByDept.add(tMap);
            }
        }
        summary.put("topEarnerByDept", topByDept);

        return summary;
    }

    // Filter employees by search keyword, type, and department
    public List<Employee> getEmployeesFiltered(String search, String type, Integer deptId)
            throws SQLException, InvalidEmployeeException {
        List<Employee> all = getAll();
        return all.stream()
                .filter(e -> {
                    if (search != null && !search.trim().isEmpty()) {
                        String q = search.trim().toLowerCase();
                        boolean matchId = e.getId().toLowerCase().contains(q);
                        boolean matchName = e.getName().toLowerCase().contains(q);
                        if (!matchId && !matchName) return false;
                    }
                    if (type != null && !type.trim().isEmpty() && !type.equalsIgnoreCase("ALL")) {
                        if (!e.getType().equalsIgnoreCase(type.trim())) return false;
                    }
                    if (deptId != null && deptId > 0) {
                        if (e.getDeptId() != deptId) return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    // Run monthly payroll for all employees in a single transaction
    public int runMonthlyPayroll(String monthYear) throws SQLException, InvalidEmployeeException {
        List<Employee> employees = getAll();
        String sql = "INSERT INTO payslip (emp_id, month_year, net_salary) VALUES (?, ?, ?)";
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int count = 0;
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    for (Employee emp : employees) {
                        ps.setString(1, emp.getId());
                        ps.setString(2, monthYear);
                        ps.setDouble(3, emp.calculateSalary());
                        ps.executeUpdate();
                        count++;
                    }
                }
                con.commit();
                return count;
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    // Give raise to all full-time employees in a department via stored procedure
    public void giveRaise(int dept, double percent) throws SQLException {
        String sql = "{CALL sp_give_raise(?, ?)}";
        try (Connection con = DBConnection.getConnection();
             CallableStatement cs = con.prepareCall(sql)) {
            cs.setInt(1, dept);
            cs.setDouble(2, percent);
            cs.execute();
        }
    }

    // Custom bubble sort on an array (highest salary first, no Collections.sort)
    public List<Employee> sortBySalaryDesc(List<Employee> list) {
        Employee[] arr = list.toArray(new Employee[0]);
        int n = arr.length;
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - i - 1; j++) {
                if (arr[j].calculateSalary() < arr[j + 1].calculateSalary()) {
                    Employee temp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = temp;
                }
            }
        }
        return Arrays.asList(arr);
    }

    // Java report utilizing Streams, lambdas, and method references
    public void printReport() throws SQLException, InvalidEmployeeException {
        List<Employee> employees = getAll();
        System.out.println("========================================");
        System.out.println("        E-ZORAX PAYROLL REPORT          ");
        System.out.println("========================================");

        // 1. Total payout using Stream and method reference
        double totalPayout = employees.stream()
                .mapToDouble(Employee::calculateSalary)
                .sum();
        System.out.printf("Total Company Payout  : %.2f%n", totalPayout);

        // 2. Highest paid employee using Optional and method reference
        Optional<Employee> highestPaid = employees.stream()
                .max(Comparator.comparingDouble(Employee::calculateSalary));
        if (highestPaid.isPresent()) {
            Employee top = highestPaid.get();
            System.out.printf("Highest Paid Employee : %s (%.2f)%n", top.getName(), top.calculateSalary());
        } else {
            System.out.println("Highest Paid Employee : None");
        }

        // 3. Payout grouped by employee type using groupingBy and summingDouble
        System.out.println("\n--- Payout by Employee Type ---");
        Map<String, Double> payoutByType = employees.stream()
                .collect(Collectors.groupingBy(
                        Employee::getType,
                        Collectors.summingDouble(Employee::calculateSalary)
                ));
        payoutByType.forEach((type, sum) -> 
            System.out.printf("  %-12s : %.2f%n", type, sum)
        );

        // 4. Employee count from static method
        System.out.println("----------------------------------------");
        System.out.printf("Employee Object Count : %d%n", Employee.getCount());
        System.out.println("========================================");
    }

    // Executes any SELECT query and displays formatted output using ResultSetMetaData
    public void printQuery(String sql) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            List<String> headers = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                headers.add(meta.getColumnLabel(i));
            }

            List<List<String>> rows = new ArrayList<>();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    String val = rs.getString(i);
                    row.add(val == null ? "NULL" : val);
                }
                rows.add(row);
            }

            int[] widths = new int[columnCount];
            for (int i = 0; i < columnCount; i++) {
                widths[i] = headers.get(i).length();
                for (List<String> row : rows) {
                    widths[i] = Math.max(widths[i], row.get(i).length());
                }
                widths[i] += 4; // padding
            }

            StringBuilder lineSep = new StringBuilder();
            for (int w : widths) {
                lineSep.append("-".repeat(w));
            }

            System.out.println(lineSep);
            StringBuilder headerLine = new StringBuilder();
            for (int i = 0; i < columnCount; i++) {
                headerLine.append(String.format("%-" + widths[i] + "s", headers.get(i)));
            }
            System.out.println(headerLine);
            System.out.println(lineSep);

            for (List<String> row : rows) {
                StringBuilder rowLine = new StringBuilder();
                for (int i = 0; i < columnCount; i++) {
                    rowLine.append(String.format("%-" + widths[i] + "s", row.get(i)));
                }
                System.out.println(rowLine);
            }
            System.out.println(lineSep);
        }
    }
}
