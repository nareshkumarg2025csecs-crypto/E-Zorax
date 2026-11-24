package model;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

public abstract class Employee {
    public static final String COMPANY = "TechNova";
    private static int count = 0;

    private final String id;
    private final String name;
    private final int deptId;
    private String deptName;
    private String updatedAt;

    public Employee(String id, String name, int deptId) throws InvalidEmployeeException {
        if (id == null || id.trim().isEmpty()) {
            throw new InvalidEmployeeException("Employee ID cannot be empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new InvalidEmployeeException("Employee name cannot be empty");
        }
        this.id = id.trim().toUpperCase();
        this.name = name.trim();
        this.deptId = deptId;
        count++;
    }

    public static int getCount() {
        return count;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getDeptId() {
        return deptId;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Abstract methods for polymorphic behavior
    public abstract double calculateSalary();
    public abstract String getType();
    public abstract void printBreakdown();
    public abstract void saveDetails(Connection con) throws SQLException;
    public abstract void updateDetails(Connection con) throws SQLException;
    public abstract Map<String, Double> getBreakdown();
    public abstract Map<String, Object> toMap();

    // Overloaded calculateBonus methods
    public double calculateBonus(int percent) {
        return calculateSalary() * (percent / 100.0);
    }

    public double calculateBonus(double fixedAmount) {
        return fixedAmount;
    }

    // Concrete payslip display
    public void displayPayslip() {
        System.out.println("----------- PAYSLIP -----------");
        System.out.printf("Employee Name : %s (ID: %s)%n", name, id);
        System.out.printf("Employee Type : %s%n", getType());
        System.out.printf("Department ID : %d%n", deptId);
        System.out.println("--- Breakdown ---");
        printBreakdown();
        System.out.println("-----------------");
        System.out.printf("Net Salary : %.2f%n", calculateSalary());
        System.out.println("-------------------------------");
    }
}
