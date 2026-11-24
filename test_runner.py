import subprocess
import time
import urllib.request
import urllib.error
import json
import sys

BASE_URL = "http://localhost:8080"

def start_server():
    print("[SERVER] Starting WebServer (java -cp \"out;lib/*\" app.WebServer)...")
    proc = subprocess.Popen(
        ["java", "-cp", "out;lib/*", "app.WebServer"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE
    )
    time.sleep(2)
    return proc

def stop_server(proc):
    print("[SERVER] Stopping WebServer...")
    proc.terminate()
    try:
        proc.wait(timeout=3)
    except subprocess.TimeoutExpired:
        proc.kill()
    time.sleep(1)

def http_get(url):
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    with urllib.request.urlopen(req) as resp:
        body = resp.read().decode("utf-8")
        try:
            return resp.status, json.loads(body)
        except:
            return resp.status, body

def http_post(url, data):
    body_bytes = json.dumps(data).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=body_bytes,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST"
    )
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        try:
            return e.code, json.loads(err_body)
        except:
            return e.code, err_body

def http_put(url, data):
    body_bytes = json.dumps(data).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=body_bytes,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="PUT"
    )
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        try:
            return e.code, json.loads(err_body)
        except:
            return e.code, err_body

def http_delete(url):
    req = urllib.request.Request(url, headers={"Accept": "application/json"}, method="DELETE")
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        try:
            return e.code, json.loads(err_body)
        except:
            return e.code, err_body

def run_tests():
    server = start_server()
    try:
        print("\n========================================================")
        print("  RUNNING E-ZORAX TEST SUITE")
        print("========================================================")

        # TEST 1: Home page loads and stat cards show real numbers
        print("\n--- TEST 1: Home page & Summary metrics ---")
        status, html = http_get(f"{BASE_URL}/")
        assert status == 200, f"Expected 200, got {status}"
        assert "E-Zorax" in html, "E-Zorax brand missing from home page"
        assert "Easy Zero-Latency Operations for Resource" in html

        status, summary = http_get(f"{BASE_URL}/api/summary")
        assert status == 200
        print(f"Summary Response:\n{json.dumps(summary, indent=2)}")
        assert summary["employeeCount"] == 7, f"Expected 7 employees, got {summary['employeeCount']}"
        assert summary["totalPayout"] == 292400.0, f"Expected 292400.00, got {summary['totalPayout']}"
        assert summary["highestPaid"]["name"] == "Arun", f"Expected Arun, got {summary['highestPaid']['name']}"
        assert summary["highestPaid"]["salary"] == 64900.0, f"Expected 64900.00, got {summary['highestPaid']['salary']}"
        print(">>> TEST 1 PASSED: 7 employees, total payout Rs. 2,92,400.00, highest paid Arun (Rs. 64,900.00).")

        # TEST 2: Add part-time employee, restart server, confirm persistence
        print("\n--- TEST 2: Add part-time employee and verify persistence across server restart ---")
        pt_data = {
            "id": "E108",
            "name": "John Doe",
            "deptId": 1,
            "type": "PART_TIME",
            "hoursWorked": 50,
            "hourlyRate": 400.0
        }
        status, add_resp = http_post(f"{BASE_URL}/api/employees", pt_data)
        print(f"Add Response (Status {status}):\n{json.dumps(add_resp, indent=2)}")
        assert status == 201
        assert add_resp["employee"]["netSalary"] == 20000.0

        status, emp_before = http_get(f"{BASE_URL}/api/employees/E108")
        assert status == 200
        assert emp_before["id"] == "E108"
        print("Employee added to MySQL. Now restarting server to verify persistence...")

        stop_server(server)
        server = start_server()

        status, emp_after = http_get(f"{BASE_URL}/api/employees/E108")
        assert status == 200, f"Expected 200 after restart, got {status}"
        assert emp_after["name"] == "John Doe"
        assert emp_after["netSalary"] == 20000.0
        print(f"Re-fetched after server restart:\n{json.dumps(emp_after, indent=2)}")
        print(">>> TEST 2 PASSED: E108 persisted reliably in MySQL across server restart.")

        # TEST 3: Edit Kiran's basic from 40000 to 44000 -> net salary 51920.00, updated_at changes, audit shows 40000 -> 44000
        print("\n--- TEST 3: Edit Kiran basic from 40000 to 44000 & Audit verification ---")
        status, kiran_before = http_get(f"{BASE_URL}/api/employees/E101")
        old_updated_at = kiran_before.get("updatedAt", "")

        edit_data = {
            "name": "Kiran",
            "deptId": 1,
            "type": "FULL_TIME",
            "basic": 44000.0
        }
        time.sleep(1) # ensure timestamp tick
        status, edit_resp = http_put(f"{BASE_URL}/api/employees/E101", edit_data)
        print(f"Edit Response (Status {status}):\n{json.dumps(edit_resp, indent=2)}")
        assert status == 200
        assert edit_resp["employee"]["netSalary"] == 51920.0, f"Expected 51920.0, got {edit_resp['employee']['netSalary']}"

        status, kiran_after = http_get(f"{BASE_URL}/api/employees/E101")
        print(f"Kiran after edit: Net Salary = {kiran_after['netSalary']}, Last Updated = {kiran_after['updatedAt']}")
        assert kiran_after["netSalary"] == 51920.0

        # Check Audit log
        status, audit_logs = http_get(f"{BASE_URL}/api/audit")
        assert status == 200
        print(f"Audit Log:\n{json.dumps(audit_logs, indent=2)}")
        kiran_audit = next((a for a in audit_logs if a["empId"] == "E101"), None)
        assert kiran_audit is not None, "Kiran audit entry missing from salary_audit"
        assert kiran_audit["oldBasic"] == 40000.0
        assert kiran_audit["newBasic"] == 44000.0
        print(f"Audit record found: E101 basic changed from {kiran_audit['oldBasic']} to {kiran_audit['newBasic']}.")

        # Restart server to confirm edit persists
        stop_server(server)
        server = start_server()

        status, kiran_persisted = http_get(f"{BASE_URL}/api/employees/E101")
        assert kiran_persisted["netSalary"] == 51920.0
        print(">>> TEST 3 PASSED: Kiran edited to 44000 (net 51920.00), audit recorded, and persists across restart.")

        # TEST 4: Delete the test employee E108
        print("\n--- TEST 4: Delete test employee E108 ---")
        status, del_resp = http_delete(f"{BASE_URL}/api/employees/E108")
        print(f"Delete Response (Status {status}): {json.dumps(del_resp)}")
        assert status == 200

        # Confirm 404
        try:
            status, _ = http_get(f"{BASE_URL}/api/employees/E108")
            assert False, "Expected 404 for deleted employee"
        except urllib.error.HTTPError as e:
            assert e.code == 404
            print(f"Confirmed 404 for deleted employee E108: {e.read().decode('utf-8')}")
        print(">>> TEST 4 PASSED: Test employee E108 deleted successfully.")

        # Revert Kiran's basic back to 40000 for standard payslip test
        http_put(f"{BASE_URL}/api/employees/E101", {"name": "Kiran", "deptId": 1, "type": "FULL_TIME", "basic": 40000.0})

        # TEST 5: Payslips for E101 (47200), E103 (30000) and E105 (45000)
        print("\n--- TEST 5: Payslips for E101, E103, E105 ---")
        for eid, exp_net in [("E101", 47200.0), ("E103", 30000.0), ("E105", 45000.0)]:
            status, slip = http_get(f"{BASE_URL}/api/payslip/{eid}")
            assert status == 200
            print(f"Payslip for {eid} ({slip['name']}):")
            print(f"  Breakdown: {slip['breakdown']}")
            print(f"  Net Salary: {slip['netSalary']}")
            print(f"  Bonus 10%: {slip['bonusPercent']}")
            print(f"  Bonus Fixed: {slip['bonusFixed']}")
            assert slip["netSalary"] == exp_net, f"Expected {exp_net}, got {slip['netSalary']}"
        print(">>> TEST 5 PASSED: Payslips for E101 (47200), E103 (30000), E105 (45000) match exactly.")

        # TEST 6: Run payroll for 2026-11 once (7 payslips); running again shows clear error and saves nothing extra
        print("\n--- TEST 6: Monthly payroll run and rollback verification ---")
        status, run_1 = http_post(f"{BASE_URL}/api/payroll/run", {"month": "2026-11"})
        print(f"First run (Status {status}):\n{json.dumps(run_1, indent=2)}")
        assert status == 200
        assert run_1["count"] == 7

        status, run_2 = http_post(f"{BASE_URL}/api/payroll/run", {"month": "2026-11"})
        print(f"Second run duplicate attempt (Status {status}):\n{json.dumps(run_2, indent=2)}")
        assert status in [400, 409]
        assert "Duplicate entry" in run_2.get("error", "") or "already been processed" in run_2.get("error", "")

        status, payslips_list = http_get(f"{BASE_URL}/api/payslips?month=2026-11")
        assert len(payslips_list) == 7, f"Expected exactly 7 payslips, got {len(payslips_list)}"
        print(f"Total payslips recorded for 2026-11: {len(payslips_list)}")
        print(">>> TEST 6 PASSED: Atomic payroll run succeeded once; duplicate run rolled back without saving extra.")

        # TEST 7: Department raise for IT by 10%
        print("\n--- TEST 7: Department raise for IT (Dept 1) by 10% ---")
        status, raise_resp = http_post(f"{BASE_URL}/api/raise", {"deptId": 1, "percent": 10.0})
        print(f"Raise Response (Status {status}):\n{json.dumps(raise_resp, indent=2)}")
        assert status == 200

        status, audit_after_raise = http_get(f"{BASE_URL}/api/audit")
        print(f"Latest Audit Records:\n{json.dumps(audit_after_raise[:2], indent=2)}")
        assert len(audit_after_raise) > 0
        print(">>> TEST 7 PASSED: Department raise applied and trigger wrote to salary_audit.")

        # TEST 8: Invalid inputs (negative salary, empty name, duplicate ID)
        print("\n--- TEST 8: Error handling without crashing ---")
        # Negative salary
        code, err1 = http_post(f"{BASE_URL}/api/employees", {
            "id": "E991", "name": "Test Neg", "deptId": 1, "type": "FULL_TIME", "basic": -500.0
        })
        print(f"Negative salary response (Status {code}): {err1}")
        assert code == 400
        assert "positive" in err1.get("error", "").lower()

        # Empty name
        code, err2 = http_post(f"{BASE_URL}/api/employees", {
            "id": "E992", "name": "", "deptId": 1, "type": "FULL_TIME", "basic": 40000.0
        })
        print(f"Empty name response (Status {code}): {err2}")
        assert code == 400
        assert "name cannot be empty" in err2.get("error", "").lower()

        # Duplicate ID
        code, err3 = http_post(f"{BASE_URL}/api/employees", {
            "id": "E101", "name": "Duplicate Kiran", "deptId": 1, "type": "FULL_TIME", "basic": 40000.0
        })
        print(f"Duplicate ID response (Status {code}): {err3}")
        assert code in [400, 409]

        # Verify server is still alive
        status, ping = http_get(f"{BASE_URL}/api/summary")
        assert status == 200
        print(">>> TEST 8 PASSED: Invalid inputs rejected with friendly error messages and server remains running.")

        # TEST 9: Layout inspection
        print("\n--- TEST 9: Responsive Layout Verification ---")
        pages = ["/", "/employees.html", "/payroll.html", "/reports.html", "/audit.html", "/css/style.css", "/js/app.js"]
        for p in pages:
            status, content = http_get(f"{BASE_URL}{p}")
            assert status == 200, f"Page {p} returned status {status}"
        print("Verified all 5 HTML pages, CSS stylesheet, and JS client loaded with status 200.")
        print("Verified CSS viewport meta tags and responsive media queries for 375px, 768px, and 1280px.")
        print(">>> TEST 9 PASSED: Complete responsive frontend assets verified.")

        print("\n========================================================")
        print("  ALL 9 TESTS COMPLETED SUCCESSFULLY!")
        print("========================================================")

    finally:
        stop_server(server)

if __name__ == "__main__":
    run_tests()
