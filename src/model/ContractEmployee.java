package model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public class ContractEmployee extends Employee {
    private final double feePerProject;
    private final int projectsDone;

    public ContractEmployee(String id, String name, int deptId, double feePerProject, int projectsDone)
            throws InvalidEmployeeException {
        super(id, name, deptId);
        if (feePerProject <= 0 || projectsDone < 0) {
            throw new InvalidEmployeeException("Fee and projects must be valid");
        }
        this.feePerProject = feePerProject;
        this.projectsDone = projectsDone;
    }

    public double getFeePerProject() {
        return feePerProject;
    }

    public int getProjectsDone() {
        return projectsDone;
    }

    @Override
    public double calculateSalary() {
        return feePerProject * projectsDone;
    }

    @Override
    public String getType() {
        return "CONTRACT";
    }

    @Override
    public void printBreakdown() {
        System.out.printf("Fee / Project : %.2f%n", feePerProject);
        System.out.printf("Projects Done : %d%n", projectsDone);
    }

    @Override
    public void saveDetails(Connection con) throws SQLException {
        String sql = "INSERT INTO contract (emp_id, fee_per_project, projects_done) VALUES (?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, getId());
            ps.setDouble(2, feePerProject);
            ps.setInt(3, projectsDone);
            ps.executeUpdate();
        }
    }

    @Override
    public void updateDetails(Connection con) throws SQLException {
        String sql = "UPDATE contract SET fee_per_project = ?, projects_done = ? WHERE emp_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, feePerProject);
            ps.setInt(2, projectsDone);
            ps.setString(3, getId());
            ps.executeUpdate();
        }
    }

    @Override
    public Map<String, Double> getBreakdown() {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("Fee Per Project", feePerProject);
        map.put("Projects Done", (double) projectsDone);
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
        map.put("feePerProject", feePerProject);
        map.put("projectsDone", projectsDone);
        map.put("breakdown", getBreakdown());
        return map;
    }
}
