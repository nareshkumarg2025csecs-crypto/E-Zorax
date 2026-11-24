package model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public class PartTimeEmployee extends Employee {
    private final int hoursWorked;
    private final double hourlyRate;

    public PartTimeEmployee(String id, String name, int deptId, int hoursWorked, double hourlyRate)
            throws InvalidEmployeeException {
        super(id, name, deptId);
        if (hoursWorked < 0 || hourlyRate <= 0) {
            throw new InvalidEmployeeException("Hours and rate must be valid");
        }
        this.hoursWorked = hoursWorked;
        this.hourlyRate = hourlyRate;
    }

    public int getHoursWorked() {
        return hoursWorked;
    }

    public double getHourlyRate() {
        return hourlyRate;
    }

    @Override
    public double calculateSalary() {
        return hoursWorked * hourlyRate;
    }

    @Override
    public String getType() {
        return "PART_TIME";
    }

    @Override
    public void printBreakdown() {
        System.out.printf("Hours Worked  : %d%n", hoursWorked);
        System.out.printf("Hourly Rate   : %.2f%n", hourlyRate);
    }

    @Override
    public void saveDetails(Connection con) throws SQLException {
        String sql = "INSERT INTO part_time (emp_id, hours_worked, hourly_rate) VALUES (?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, getId());
            ps.setInt(2, hoursWorked);
            ps.setDouble(3, hourlyRate);
            ps.executeUpdate();
        }
    }

    @Override
    public void updateDetails(Connection con) throws SQLException {
        String sql = "UPDATE part_time SET hours_worked = ?, hourly_rate = ? WHERE emp_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, hoursWorked);
            ps.setDouble(2, hourlyRate);
            ps.setString(3, getId());
            ps.executeUpdate();
        }
    }

    @Override
    public Map<String, Double> getBreakdown() {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("Hours Worked", (double) hoursWorked);
        map.put("Hourly Rate", hourlyRate);
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
        map.put("hoursWorked", hoursWorked);
        map.put("hourlyRate", hourlyRate);
        map.put("breakdown", getBreakdown());
        return map;
    }
}
