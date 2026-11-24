package model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public class FullTimeEmployee extends Employee {
    private final double basic;

    public FullTimeEmployee(String id, String name, int deptId, double basic) throws InvalidEmployeeException {
        super(id, name, deptId);
        if (basic <= 0) {
            throw new InvalidEmployeeException("Basic must be positive");
        }
        this.basic = basic;
    }

    public double getBasic() {
        return basic;
    }

    @Override
    public double calculateSalary() {
        double hra = basic * 0.20;
        double da = basic * 0.10;
        double pf = basic * 0.12;
        return basic + hra + da - pf;
    }

    @Override
    public String getType() {
        return "FULL_TIME";
    }

    @Override
    public void printBreakdown() {
        System.out.printf("Basic Salary  : %.2f%n", basic);
        System.out.printf("HRA (20%%)     : %.2f%n", basic * 0.20);
        System.out.printf("DA (10%%)      : %.2f%n", basic * 0.10);
        System.out.printf("PF (12%%)      : %.2f%n", basic * 0.12);
    }

    @Override
    public void saveDetails(Connection con) throws SQLException {
        String sql = "INSERT INTO full_time (emp_id, basic) VALUES (?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, getId());
            ps.setDouble(2, basic);
            ps.executeUpdate();
        }
    }

    @Override
    public void updateDetails(Connection con) throws SQLException {
        String sql = "UPDATE full_time SET basic = ? WHERE emp_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, basic);
            ps.setString(2, getId());
            ps.executeUpdate();
        }
    }

    @Override
    public Map<String, Double> getBreakdown() {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("Basic Salary", basic);
        map.put("HRA (20%)", basic * 0.20);
        map.put("DA (10%)", basic * 0.10);
        map.put("PF (12%)", basic * 0.12);
        return map;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", getId());
        map.put("name", getName());
        map.put("deptId", getDeptId());
        map.put("deptName", getDeptName() != null ? getDeptName() : "");
        map.put("type", getType());
        map.put("netSalary", calculateSalary());
        map.put("updatedAt", getUpdatedAt() != null ? getUpdatedAt() : "");
        map.put("basic", basic);
        map.put("breakdown", getBreakdown());
        return map;
    }
}
