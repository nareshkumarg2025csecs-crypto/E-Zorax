-- ========================================================
-- E-Zorax: SQL Query Demonstrations (IT19341 DBMS Coverage)
-- ========================================================

USE payrollpro;

-- 1. INNER JOIN: Retrieve employee details alongside department names
SELECT 
    e.emp_id, 
    e.name, 
    d.dept_name, 
    e.emp_type, 
    e.join_date
FROM employee e
INNER JOIN department d ON e.dept_id = d.dept_id
ORDER BY e.emp_id;

-- 2. GROUP BY with COUNT, SUM, and AVG: Departmental payroll statistics
SELECT 
    dept_name,
    COUNT(*) AS total_employees,
    SUM(net_salary) AS total_payout,
    ROUND(AVG(net_salary), 2) AS avg_salary
FROM v_salary
GROUP BY dept_name;

-- 3. HAVING: Filter departments whose total payout exceeds 50,000.00
SELECT 
    dept_name,
    COUNT(*) AS total_employees,
    SUM(net_salary) AS total_payout
FROM v_salary
GROUP BY dept_name
HAVING SUM(net_salary) > 50000.00;

-- 4. Nested Subquery: Employees earning strictly above company-wide average
SELECT 
    emp_id, 
    name, 
    dept_name, 
    net_salary
FROM v_salary
WHERE net_salary > (SELECT AVG(net_salary) FROM v_salary)
ORDER BY net_salary DESC;

-- 5. Correlated Subquery: Highest-paid employee(s) within each department
SELECT 
    s1.dept_name, 
    s1.emp_id, 
    s1.name, 
    s1.net_salary
FROM v_salary s1
WHERE s1.net_salary = (
    SELECT MAX(s2.net_salary)
    FROM v_salary s2
    WHERE s2.dept_name = s1.dept_name
)
ORDER BY s1.dept_name;

-- 6. NOT IN: Employees who do not have a payslip generated for month '2026-10'
SELECT 
    e.emp_id, 
    e.name, 
    e.dept_id, 
    e.emp_type
FROM employee e
WHERE e.emp_id NOT IN (
    SELECT p.emp_id 
    FROM payslip p 
    WHERE p.month_year = '2026-10'
);

-- 7. Index on payslip(month_year) and query execution plan using EXPLAIN
CREATE INDEX idx_payslip_month ON payslip(month_year);

EXPLAIN SELECT * FROM payslip WHERE month_year = '2026-10';

-- 8. Transaction Experiment: Test 10% raise in Dept 1, verify view, and rollback
START TRANSACTION;

-- Apply 10% raise to IT department (dept_id = 1)
CALL sp_give_raise(1, 10.00);

-- Verify the increased payout in v_salary within uncommitted transaction
SELECT 'After Raise (Before Rollback)' AS phase, SUM(net_salary) AS it_total
FROM v_salary
WHERE dept_name = 'IT';

-- Rollback the transaction to restore previous state
ROLLBACK;

-- Verify that the raise was rolled back
SELECT 'After Rollback (Restored)' AS phase, SUM(net_salary) AS it_total
FROM v_salary
WHERE dept_name = 'IT';
