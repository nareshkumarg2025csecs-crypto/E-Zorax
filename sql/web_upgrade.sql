-- ========================================================
-- E-Zorax: Web UI Upgrade Script
-- Idempotent check and addition of updated_at column
-- ========================================================

USE payrollpro;

DROP PROCEDURE IF EXISTS upgrade_web;
DELIMITER //
CREATE PROCEDURE upgrade_web()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS 
        WHERE TABLE_SCHEMA = 'payrollpro' 
          AND TABLE_NAME = 'employee' 
          AND COLUMN_NAME = 'updated_at'
    ) THEN
        ALTER TABLE employee ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
    END IF;
END //
DELIMITER ;

CALL upgrade_web();
DROP PROCEDURE IF EXISTS upgrade_web;
