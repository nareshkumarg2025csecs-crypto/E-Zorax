package app;

import model.ContractEmployee;
import model.Employee;
import model.FullTimeEmployee;
import model.InvalidEmployeeException;
import model.PartTimeEmployee;
import service.PayrollManager;

import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

public class PayrollApp {

    private final PayrollManager manager = new PayrollManager();
    private final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        PayrollApp app = new PayrollApp();
        app.run();
    }

    public void run() {
        boolean running = true;
        while (running) {
            printBanner();
            printMenu();
            int choice = readInt(scanner, "Enter your choice (1-8): ");
            try {
                switch (choice) {
                    case 1:
                        handleAddEmployee();
                        break;
                    case 2:
                        handlePayslip();
                        break;
                    case 3:
                        handleSortBySalary();
                        break;
                    case 4:
                        handleJavaReport();
                        break;
                    case 5:
                        handleSqlReports();
                        break;
                    case 6:
                        handleRunMonthlyPayroll();
                        break;
                    case 7:
                        handleDeptRaise();
                        break;
                    case 8:
                        running = false;
                        System.out.println("Exiting E-Zorax. Goodbye!");
                        break;
                    default:
                        System.out.println("Error: Invalid choice. Please select an option between 1 and 8.");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
            System.out.println();
        }
    }

    private void printBanner() {
        System.out.println("==================================================");
        System.out.println("           E-Zorax (Company: TechNova)            ");
        System.out.println("==================================================");
    }

    private void printMenu() {
        System.out.println("1. Add Employee");
        System.out.println("2. Payslip");
        System.out.println("3. Sort by Salary");
        System.out.println("4. Java Report");
        System.out.println("5. SQL Reports");
        System.out.println("6. Run Monthly Payroll");
        System.out.println("7. Dept Raise");
        System.out.println("8. Exit");
        System.out.println("--------------------------------------------------");
    }

    private void handleAddEmployee() throws SQLException, InvalidEmployeeException {
        System.out.println("\nSelect Employee Type:");
        System.out.println("1. Full-Time");
        System.out.println("2. Part-Time");
        System.out.println("3. Contract");
        int typeChoice = readInt(scanner, "Choice (1-3): ");

        String id = readString(scanner, "Enter Employee ID: ");
        String name = readString(scanner, "Enter Employee Name: ");
        int deptId = readInt(scanner, "Enter Department ID (1: IT, 2: HR, 3: Finance): ");

        Employee emp;
        switch (typeChoice) {
            case 1:
                double basic = readDouble(scanner, "Enter Basic Salary: ");
                emp = new FullTimeEmployee(id, name, deptId, basic);
                break;
            case 2:
                int hours = readInt(scanner, "Enter Hours Worked: ");
                double rate = readDouble(scanner, "Enter Hourly Rate: ");
                emp = new PartTimeEmployee(id, name, deptId, hours, rate);
                break;
            case 3:
                double fee = readDouble(scanner, "Enter Fee Per Project: ");
                int projects = readInt(scanner, "Enter Projects Done: ");
                emp = new ContractEmployee(id, name, deptId, fee, projects);
                break;
            default:
                throw new InvalidEmployeeException("Invalid employee type selected.");
        }

        manager.addEmployee(emp);
        System.out.printf("Employee %s (%s) added successfully!%n", emp.getName(), emp.getId());
    }

    private void handlePayslip() throws SQLException, InvalidEmployeeException {
        String id = readString(scanner, "Enter Employee ID: ");
        Employee emp = manager.findById(id);
        if (emp == null) {
            System.out.println("Error: Employee not found with ID: " + id);
            return;
        }

        emp.displayPayslip();
        System.out.printf("Bonus (10%%)         : %.2f%n", emp.calculateBonus(10));
        System.out.printf("Bonus (Fixed 5000)  : %.2f%n", emp.calculateBonus(5000.0));
        System.out.println("-------------------------------");
    }

    private void handleSortBySalary() throws SQLException, InvalidEmployeeException {
        List<Employee> all = manager.getAll();
        List<Employee> sorted = manager.sortBySalaryDesc(all);
        System.out.println("\n--- Employees Sorted by Salary (Descending) ---");
        int rank = 1;
        for (Employee emp : sorted) {
            System.out.printf("%d. %-10s (%-5s) - %-10s - Net Salary: %.2f%n",
                    rank++, emp.getName(), emp.getId(), emp.getType(), emp.calculateSalary());
        }
    }

    private void handleJavaReport() throws SQLException, InvalidEmployeeException {
        manager.printReport();
    }

    private void handleSqlReports() throws SQLException {
        System.out.println("\n--- SQL Report: Department Payout Summary ---");
        String sqlDept = "SELECT dept_name AS Department, SUM(net_salary) AS Total_Payout " +
                         "FROM v_salary GROUP BY dept_name ORDER BY dept_name";
        manager.printQuery(sqlDept);

        System.out.println("\n--- SQL Report: Employees Earning Above Company Average ---");
        String sqlAboveAvg = "SELECT emp_id AS Emp_ID, name AS Name, dept_name AS Department, net_salary AS Net_Salary " +
                             "FROM v_salary " +
                             "WHERE net_salary > (SELECT AVG(net_salary) FROM v_salary) " +
                             "ORDER BY net_salary DESC";
        manager.printQuery(sqlAboveAvg);
    }

    private void handleRunMonthlyPayroll() throws SQLException, InvalidEmployeeException {
        String monthYear = readString(scanner, "Enter Month-Year (YYYY-MM, e.g. 2026-10): ");
        int count = manager.runMonthlyPayroll(monthYear);
        System.out.printf("Successfully generated payslips for %d employees for %s.%n", count, monthYear);
    }

    private void handleDeptRaise() throws SQLException {
        int dept = readInt(scanner, "Enter Department ID: ");
        double percent = readDouble(scanner, "Enter Raise Percentage: ");
        manager.giveRaise(dept, percent);
        System.out.printf("Successfully applied a %.2f%% raise to Department %d.%n", percent, dept);
    }

    private static int readInt(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Error: Invalid integer input. Please enter a valid number.");
            }
        }
    }

    private static double readDouble(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Double.parseDouble(input);
            } catch (NumberFormatException e) {
                System.out.println("Error: Invalid decimal input. Please enter a valid number.");
            }
        }
    }

    private static String readString(Scanner scanner, String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }
}

