-- ========================================================
-- E-Zorax: Stored Functions, Triggers, Procedures & Views
-- ========================================================

USE payrollpro;

-- 1. FUNCTION fn_fulltime_net
DROP FUNCTION IF EXISTS fn_fulltime_net;
DELIMITER //
CREATE FUNCTION fn_fulltime_net(p_basic DECIMAL(10,2))
RETURNS DECIMAL(12,2)
DETERMINISTIC
BEGIN
    DECLARE v_hra DECIMAL(10,2);
    DECLARE v_da DECIMAL(10,2);
    DECLARE v_pf DECIMAL(10,2);
    DECLARE v_net DECIMAL(12,2);
    
    -- HRA: 20%, DA: 10%, PF: 12%
    SET v_hra = ROUND(p_basic * 0.20, 2);
    SET v_da  = ROUND(p_basic * 0.10, 2);
    SET v_pf  = ROUND(p_basic * 0.12, 2);
    SET v_net = p_basic + v_hra + v_da - v_pf;
    
    RETURN v_net;
END //
DELIMITER ;

-- 2. TRIGGER trg_basic_audit
DROP TRIGGER IF EXISTS trg_basic_audit;
DELIMITER //
CREATE TRIGGER trg_basic_audit
AFTER UPDATE ON full_time
FOR EACH ROW
BEGIN
    IF OLD.basic <> NEW.basic THEN
        INSERT INTO salary_audit (emp_id, old_basic, new_basic, changed_on)
        VALUES (OLD.emp_id, OLD.basic, NEW.basic, CURRENT_TIMESTAMP);
    END IF;
END //
DELIMITER ;

-- 3. PROCEDURE sp_give_raise
DROP PROCEDURE IF EXISTS sp_give_raise;
DELIMITER //
CREATE PROCEDURE sp_give_raise(IN p_dept INT, IN p_percent DECIMAL(5,2))
BEGIN
    DECLARE v_done INT DEFAULT FALSE;
    DECLARE v_emp_id VARCHAR(10);
    DECLARE v_basic DECIMAL(10,2);
    
    -- Cursor to iterate over all full-time employees in the specified department
    DECLARE cur CURSOR FOR
        SELECT f.emp_id, f.basic
        FROM full_time f
        INNER JOIN employee e ON f.emp_id = e.emp_id
        WHERE e.dept_id = p_dept;
        
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;
    
    OPEN cur;
    
    raise_loop: LOOP
        FETCH cur INTO v_emp_id, v_basic;
        IF v_done THEN
            LEAVE raise_loop;
        END IF;
        
        UPDATE full_time
        SET basic = ROUND(v_basic * (1 + (p_percent / 100.0)), 2)
        WHERE emp_id = v_emp_id;
    END LOOP raise_loop;
    
    CLOSE cur;
END //
DELIMITER ;

-- 4. VIEW v_salary
CREATE OR REPLACE VIEW v_salary (emp_id, name, dept_name, emp_type, net_salary) AS
SELECT 
    e.emp_id,
    e.name,
    d.dept_name,
    e.emp_type,
    CASE 
        WHEN e.emp_type = 'FULL_TIME' THEN fn_fulltime_net(ft.basic)
        WHEN e.emp_type = 'PART_TIME' THEN ROUND(pt.hours_worked * pt.hourly_rate, 2)
        WHEN e.emp_type = 'CONTRACT'  THEN ROUND(c.fee_per_project * c.projects_done, 2)
        ELSE 0.00
    END AS net_salary
FROM employee e
INNER JOIN department d ON e.dept_id = d.dept_id
LEFT JOIN full_time ft ON e.emp_id = ft.emp_id
LEFT JOIN part_time pt ON e.emp_id = pt.emp_id
LEFT JOIN contract c ON e.emp_id = c.emp_id;

