-- ========================================================
-- E-Zorax: Employee Payroll Management System (IT19341)
-- Database Schema Definition & Sample Data
-- ========================================================

DROP DATABASE IF EXISTS payrollpro;
CREATE DATABASE payrollpro;
USE payrollpro;

-- 1. Department table
CREATE TABLE department (
    dept_id INT AUTO_INCREMENT PRIMARY KEY,
    dept_name VARCHAR(40) NOT NULL UNIQUE
);

-- 2. Employee master table
CREATE TABLE employee (
    emp_id VARCHAR(10) PRIMARY KEY,
    name VARCHAR(60) NOT NULL,
    dept_id INT NOT NULL,
    emp_type ENUM('FULL_TIME', 'PART_TIME', 'CONTRACT') NOT NULL,
    join_date DATE DEFAULT (CURRENT_DATE),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_emp_dept FOREIGN KEY (dept_id) REFERENCES department(dept_id)
);

-- 3. Full-time employee details
CREATE TABLE full_time (
    emp_id VARCHAR(10) PRIMARY KEY,
    basic DECIMAL(10, 2) NOT NULL,
    CONSTRAINT chk_ft_basic CHECK (basic > 0),
    CONSTRAINT fk_ft_emp FOREIGN KEY (emp_id) REFERENCES employee(emp_id) ON DELETE CASCADE
);

-- 4. Part-time employee details
CREATE TABLE part_time (
    emp_id VARCHAR(10) PRIMARY KEY,
    hours_worked INT NOT NULL,
    hourly_rate DECIMAL(8, 2) NOT NULL,
    CONSTRAINT chk_pt_hours CHECK (hours_worked >= 0),
    CONSTRAINT chk_pt_rate CHECK (hourly_rate > 0),
    CONSTRAINT fk_pt_emp FOREIGN KEY (emp_id) REFERENCES employee(emp_id) ON DELETE CASCADE
);

-- 5. Contract employee details
CREATE TABLE contract (
    emp_id VARCHAR(10) PRIMARY KEY,
    fee_per_project DECIMAL(10, 2) NOT NULL,
    projects_done INT NOT NULL,
    CONSTRAINT chk_c_fee CHECK (fee_per_project > 0),
    CONSTRAINT chk_c_projects CHECK (projects_done >= 0),
    CONSTRAINT fk_c_emp FOREIGN KEY (emp_id) REFERENCES employee(emp_id) ON DELETE CASCADE
);

-- 6. Payslip table
CREATE TABLE payslip (
    slip_id INT AUTO_INCREMENT PRIMARY KEY,
    emp_id VARCHAR(10) NOT NULL,
    month_year CHAR(7) NOT NULL,
    net_salary DECIMAL(12, 2) NOT NULL,
    generated_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_slip_emp FOREIGN KEY (emp_id) REFERENCES employee(emp_id) ON DELETE CASCADE,
    CONSTRAINT uq_emp_month UNIQUE (emp_id, month_year)
);

-- 7. Salary audit table
CREATE TABLE salary_audit (
    audit_id INT AUTO_INCREMENT PRIMARY KEY,
    emp_id VARCHAR(10) NOT NULL,
    old_basic DECIMAL(10, 2),
    new_basic DECIMAL(10, 2),
    changed_on TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- --------------------------------------------------------
-- Sample Data Insertion
-- --------------------------------------------------------

-- Departments
INSERT INTO department (dept_id, dept_name) VALUES
(1, 'IT'),
(2, 'HR'),
(3, 'Finance');

-- Employees
INSERT INTO employee (emp_id, name, dept_id, emp_type, join_date) VALUES
('E101', 'Kiran', 1, 'FULL_TIME', CURRENT_DATE),
('E102', 'Asha',  2, 'FULL_TIME', CURRENT_DATE),
('E103', 'Ravi',  1, 'PART_TIME', CURRENT_DATE),
('E104', 'Meena', 3, 'PART_TIME', CURRENT_DATE),
('E105', 'Dev',   1, 'CONTRACT',  CURRENT_DATE),
('E106', 'Priya', 3, 'CONTRACT',  CURRENT_DATE),
('E107', 'Arun',  3, 'FULL_TIME', CURRENT_DATE);

-- Full-Time Employee details
INSERT INTO full_time (emp_id, basic) VALUES
('E101', 40000.00),
('E102', 35000.00),
('E107', 55000.00);

-- Part-Time Employee details
INSERT INTO part_time (emp_id, hours_worked, hourly_rate) VALUES
('E103', 120, 250.00),
('E104', 80,  300.00);

-- Contract Employee details
INSERT INTO contract (emp_id, fee_per_project, projects_done) VALUES
('E105', 15000.00, 3),
('E106', 20000.00, 2);

