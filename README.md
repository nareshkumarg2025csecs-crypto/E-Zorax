# E-Zorax: Employee Payroll Management System

**Company / Product:** E-Zorax ("Easy Zero-Latency Operations for Resource & Analytics matriX")  
**Course:** CS23332 – Object-Oriented Programming with Java & Database Management Systems  
**Institution:** Rajalakshmi Engineering College, Thandalam  
**Academic Team:** Jaish R, Jashvanth R, Ayaaz Shaarav K M, Naresh Kumar G (Second Year, Section C, CSE - Cyber Security, November 2026)  
**Architecture:** Multi-Tier Architecture (Vanilla HTML/CSS/JS Web Frontend & Console UI &rarr; Built-in JDK HttpServer &rarr; PayrollManager &rarr; JDBC &rarr; MySQL 8+)  

---

## 1. Project Overview

**E-Zorax** is an enterprise-grade payroll management and compensation analytics platform. Originally built as an interactive console application enforcing strict Object-Oriented design (pure polymorphism, zero `instanceof`), it now features an elegant, minimalistic, corporate **White & Blue Web UI** powered by the lightweight, built-in JDK `com.sun.net.httpserver.HttpServer` and a RESTful JSON API.

### Core Architecture
```text
[ Browser (HTML5 / CSS3 / ES6 Fetch) ]
                  |
         HTTP JSON / REST (Port 8080)
                  |
    [ app.WebServer (JDK HttpServer) ]
                  |
       [ service.PayrollManager ] <----> [ app.PayrollApp (Console) ]
                  |
       [ model.Employee Subclasses ]
                  |
             [ JDBC Driver ]
                  |
           [ MySQL Database ]
   (Tables, Triggers, Procedures, Views)
```

---

## 2. Directory & File Structure

```text
PayrollPro/ (d:\e-zorax\)
├── lib/
│   ├── mysql-connector-j.jar          # MySQL Connector/J JDBC driver
│   └── gson.jar                       # Google Gson 2.10.1 library
├── sql/
│   ├── schema.sql                     # DDL, constraints, sample data, and updated_at
│   ├── programs.sql                   # fn_fulltime_net, trg_basic_audit, sp_give_raise, v_salary
│   ├── queries.sql                    # JOIN, GROUP BY, subqueries, index explain, transactions
│   └── web_upgrade.sql                # Adds employee.updated_at timestamp column
├── src/
│   ├── db/
│   │   └── DBConnection.java          # Single source of truth for DB credentials (root / Naresh@8346)
│   ├── model/
│   │   ├── Employee.java              # Abstract base class (polymorphism, static count, breakdown)
│   │   ├── FullTimeEmployee.java      # Basic + 20% HRA + 10% DA - 12% PF (1.18 * basic)
│   │   ├── PartTimeEmployee.java      # Hours worked * Hourly rate
│   │   ├── ContractEmployee.java      # Fee per project * Projects done
│   │   └── InvalidEmployeeException.java # Checked validation exception
│   ├── service/
│   │   └── PayrollManager.java        # Atomic transactions, bubble sort, Streams, MySQL routines
│   └── app/
│       ├── PayrollApp.java            # Console interactive application
│       └── WebServer.java             # JDK HTTP server serving static files and JSON REST API
├── web/
│   ├── index.html                     # Corporate homepage with live stats, features, & team
│   ├── employees.html                 # Dynamic employee directory, filters, add/edit modal, payslip
│   ├── payroll.html                   # Transactional payroll run, department raise, history
│   ├── reports.html                   # Visual CSS bar charts, departmental distribution, subqueries
│   ├── audit.html                     # Real-time salary audit log from trigger trg_basic_audit
│   ├── css/
│   │   └── style.css                  # White & Blue design system (variables, responsive, Inter font)
│   └── js/
│       └── app.js                     # ES6 fetch API client, Indian currency formatting (Rs.), modals
├── out/                               # Compiled Java bytecode (.class)
├── run_web.bat                        # One-click Windows compilation & server launch
└── README.md                          # Full documentation
```

---

## 3. Database Architecture & Setup

### Connection Credentials
- **Host:** `localhost` | **Port:** `3306`
- **Username:** `root` | **Password:** `Naresh@8346`
- **Database:** `payrollpro`

Credentials are kept **ONLY** in `src/db/DBConnection.java`.

### Running SQL Scripts
Execute in Command Prompt or PowerShell:
```cmd
mysql -u root -pNaresh@8346 < sql\schema.sql
mysql -u root -pNaresh@8346 < sql\programs.sql
mysql -u root -pNaresh@8346 < sql\web_upgrade.sql
mysql -u root -pNaresh@8346 < sql\queries.sql
```

---

## 4. How to Compile and Run (Windows)

### Option A: Using the Windows Batch File (Recommended)
Simply double-click `run_web.bat` or run:
```cmd
.\run_web.bat
```
Then open your browser at **http://localhost:8080**.

### Option B: Manual Command Line
```powershell
# 1. Compile all source files
javac -cp "lib/*" -d out src/db/*.java src/model/*.java src/service/*.java src/app/*.java

# 2. Run the Web Server (Port 8080)
java -cp "out;lib/*" app.WebServer

# 3. (Optional) Run the Console Application
java -cp "out;lib/*" app.PayrollApp
```

---

## 5. REST API Documentation

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/summary` | Returns headcount, monthly payout, highest earner, and department stats. |
| `GET` | `/api/departments` | Returns list of active company departments. |
| `GET` | `/api/employees?search=&type=&dept=` | Returns filtered employees list with computed net salaries. |
| `GET` | `/api/employees/{id}` | Returns single employee details with classification specifics. |
| `POST` | `/api/employees` | Inserts new employee into MySQL within a transaction. |
| `PUT` | `/api/employees/{id}` | Updates name, department, and salary parameters in MySQL. |
| `DELETE` | `/api/employees/{id}` | Cascades delete across employee and child tables. |
| `GET` | `/api/payslip/{id}` | Generates full payslip breakdown with 10% and fixed Rs. 5,000 bonuses. |
| `POST` | `/api/payroll/run` | Executes monthly payroll in a single transaction; rolls back on duplicates. |
| `GET` | `/api/payslips?month=YYYY-MM` | Retrieves historical payslips for the specified month. |
| `POST` | `/api/raise` | Invokes stored procedure `sp_give_raise` with cursor; fires audit trigger. |
| `GET` | `/api/audit` | Retrieves salary change audit log records from `salary_audit`. |

---

## 6. Academic Team & Credits

- **Course:** CS23332 – Object-Oriented Programming & DBMS
- **Institution:** Rajalakshmi Engineering College, Thandalam
- **Class:** Second Year, Section C, Department of CSE (Cyber Security)
- **Team Members:**
  - **Jaish R**
  - **Jashvanth R**
  - **Ayaaz Shaarav K M**
  - **Naresh Kumar G**
- **Date:** November 2026
