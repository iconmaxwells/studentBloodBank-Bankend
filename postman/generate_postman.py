#!/usr/bin/env python3
"""Generate BloodBank Postman collection and environment files."""
import json
import uuid
from copy import deepcopy

COLLECTION_VARS = [
    "baseUrl", "accessToken", "refreshToken", "adminToken", "staffToken",
    "donorToken", "hospitalToken", "specialistToken", "donorId", "hospitalId",
    "requestId", "collectionId", "bloodUnitId", "compensationId",
]

DEFAULT_AUTH = {
    "type": "bearer",
    "bearer": [{"key": "token", "value": "{{accessToken}}", "type": "string"}],
}

def login_test(token_var):
    lines = [
        "pm.test('Status is 200', function () {",
        "    pm.response.to.have.status(200);",
        "});",
        "var json = pm.response.json();",
        "pm.test('Login successful', function () {",
        "    pm.expect(json.success).to.be.true;",
        "    pm.expect(json.data.accessToken).to.be.a('string');",
        "});",
        "if (json.success && json.data) {",
        "    pm.collectionVariables.set('accessToken', json.data.accessToken);",
        "    pm.collectionVariables.set('refreshToken', json.data.refreshToken);",
        f"    pm.collectionVariables.set('{token_var}', json.data.accessToken);",
        "}",
    ]
    return "\n".join(lines)


def uid():
    return str(uuid.uuid4())


def url(path, query=None):
    """Build Postman URL object. path is relative to baseUrl (e.g. /health)."""
    raw_path = path.lstrip("/")
    segments = raw_path.split("/") if raw_path else []
    u = {
        "raw": "{{baseUrl}}/" + raw_path + (("?" + query) if query else ""),
        "host": ["{{baseUrl}}"],
        "path": segments,
    }
    if query:
        u["query"] = [{"key": q.split("=")[0], "value": q.split("=")[1] if "=" in q else ""} for q in query.split("&")]
    return u


def req(name, method, path, body=None, query=None, auth=True, tests=None, desc=None):
    r = {
        "name": name,
        "request": {
            "method": method,
            "header": [{"key": "Content-Type", "value": "application/json"}] if body else [],
            "url": url(path, query),
        },
        "response": [],
    }
    if auth:
        r["request"]["auth"] = deepcopy(DEFAULT_AUTH)
    if body is not None:
        r["request"]["body"] = {"mode": "raw", "raw": body if isinstance(body, str) else json.dumps(body, indent=2)}
    if desc:
        r["request"]["description"] = desc
    if tests:
        r["event"] = [{"listen": "test", "script": {"exec": tests.split("\n"), "type": "text/javascript"}}]
    return r


def folder(name, items, desc=None):
    f = {"name": name, "item": items}
    if desc:
        f["description"] = desc
    return f


def login(name, email, role, token_var):
    body = json.dumps({"email": email, "password": "demo123", "role": role}, indent=2)
    return req(
        name, "POST", "auth/login", body=body, auth=False,
        tests=login_test(token_var),
    )


def build_collection():
    items = []

    # --- Auth ---
    auth_items = [
        req("Register Donor", "POST", "auth/register/donor", auth=False, body={
            "firstName": "Jane", "lastName": "Donor", "email": "newdonor@example.com",
            "password": "demo12345", "dateOfBirth": "1995-06-15", "gender": "Female",
            "idType": "Ghana_Card", "idNumber": "GHA-123456789-0", "agreeToTerms": True,
        }),
        login("Login - Admin", "admin@bloodbank.com", "admin", "adminToken"),
        login("Login - Staff", "staff@bloodbank.com", "staff", "staffToken"),
        login("Login - Donor", "donor@email.com", "donor", "donorToken"),
        login("Login - Hospital", "hospital@stmarys.com", "hospital", "hospitalToken"),
        login("Login - Specialist", "specialist@bloodbank.com", "specialist", "specialistToken"),
        req("Refresh Token", "POST", "auth/refresh", auth=False, body={"refreshToken": "{{refreshToken}}"}),
        req("Logout", "POST", "auth/logout", body={}),
        req("Change Password", "POST", "auth/change-password", body={
            "currentPassword": "demo123", "newPassword": "demo12345",
        }),
        req("Verify Action", "POST", "auth/verify-action", body={"password": "demo123"}),
        req("Verify Staff Action", "POST", "auth/verify-staff-action", body={"password": "demo123"}),
        req("Verify Admin Password", "POST", "auth/verify-admin-password", body={"password": "demo123"}),
        req("Get Current User", "GET", "auth/me"),
    ]
    items.append(folder("Auth", auth_items, "Authentication endpoints. Run a role login first to populate tokens."))

    # --- Health ---
    items.append(folder("Health", [req("Health Check", "GET", "health", auth=False)]))

    # --- Reference Data ---
    items.append(folder("Reference Data", [
        req("Blood Types", "GET", "reference/blood-types", auth=False),
        req("Blood Groups", "GET", "reference/blood-groups", auth=False),
        req("Regions", "GET", "reference/regions", auth=False),
        req("Urgency Levels", "GET", "reference/urgency-levels", auth=False),
    ], "Public reference data endpoints (no auth required)."))

    # --- Admin ---
    items.append(folder("Admin", [
        req("Dashboard Stats", "GET", "admin/dashboard/stats"),
        req("Dashboard Charts", "GET", "admin/dashboard/charts"),
        req("Monitoring Inventory", "GET", "admin/monitoring/inventory"),
        req("Monitoring Alerts", "GET", "admin/monitoring/alerts"),
    ]))

    # --- Donors ---
    items.append(folder("Donors", [
        req("List Donors", "GET", "donors", query="page=1&limit=20"),
        req("Get Donor by ID", "GET", "donors/{{donorId}}"),
        req("Create Donor", "POST", "donors", body={
            "email": "createdonor@example.com", "password": "demo12345",
            "firstName": "John", "lastName": "Smith", "dateOfBirth": "1990-01-15",
            "gender": "Male", "idType": "Ghana_Card", "idNumber": "GHA-987654321-0",
            "address": "123 Main St", "city": "Accra", "region": "Greater Accra",
        }),
        req("Update Donor", "PATCH", "donors/{{donorId}}", body={"phone": "+233201234567"}),
        req("Delete Donor", "DELETE", "donors/{{donorId}}"),
        req("Donor History", "GET", "donors/{{donorId}}/history", query="page=1&limit=20"),
        req("Donor Eligibility", "GET", "donors/{{donorId}}/eligibility"),
        req("Donor Collections", "GET", "donors/{{donorId}}/collections", query="page=1&limit=20"),
    ]))

    # --- Hospitals ---
    items.append(folder("Hospitals", [
        req("List Hospitals", "GET", "hospitals", query="page=1&limit=20"),
        req("Nearby Hospitals", "GET", "hospitals/nearby", query="lat=5.6037&lng=-0.1870&radiusKm=25"),
        req("Get Hospital by ID", "GET", "hospitals/{{hospitalId}}"),
        req("Create Hospital", "POST", "hospitals", body={
            "email": "newhospital@example.com", "password": "demo12345", "name": "City General Hospital",
            "registrationNumber": "HSP-2024-001", "location": "Accra", "address": "456 Hospital Rd",
            "latitude": 5.6037, "longitude": -0.1870, "phone": "+233302123456",
            "capacity": "Large", "beds": 200,
        }),
        req("Update Hospital", "PATCH", "hospitals/{{hospitalId}}", body={"phone": "+233302999888"}),
        req("Delete Hospital", "DELETE", "hospitals/{{hospitalId}}"),
        req("Hospital Stats", "GET", "hospitals/{{hospitalId}}/stats"),
        req("Hospital Requests", "GET", "hospitals/{{hospitalId}}/requests", query="page=1&limit=20"),
    ]))

    # --- Staff Management ---
    staff_mgmt = [
        req("List Staff", "GET", "staff", query="page=1&limit=20"),
        req("Get Staff by ID", "GET", "staff/00000000-0000-0000-0000-000000000001"),
        req("Create Staff", "POST", "staff", body={
            "email": "newstaff@bloodbank.com", "password": "demo12345", "name": "Alice Johnson",
            "phone": "+233201111111", "department": "Collection", "shift": "Day", "portalRole": "staff",
        }),
        req("Update Staff", "PATCH", "staff/00000000-0000-0000-0000-000000000001", body={"phone": "+233202222222"}),
        req("Delete Staff", "DELETE", "staff/00000000-0000-0000-0000-000000000001"),
        req("Staff Permissions", "GET", "staff/00000000-0000-0000-0000-000000000001/permissions"),
    ]
    items.append(folder("Staff Management", staff_mgmt))

    # --- Staff Portal ---
    items.append(folder("Staff Portal", [
        req("Dashboard", "GET", "staff/dashboard"),
        req("Search Donors", "GET", "staff/donors", query="page=1&limit=20&search="),
        req("Create Supply Request", "POST", "staff/supply-requests", body={
            "bloodGroup": "O+", "bloodProductType": "RBC", "unitsRequested": 5, "urgency": "High",
            "notes": "Emergency supply needed",
        }),
        req("List Payments", "GET", "staff/payments", query="page=1&limit=20"),
        req("Mark Payment Paid", "PATCH", "staff/payments/00000000-0000-0000-0000-000000000001/mark-paid",
            body={"method": "Mobile_Money"}),
    ]))

    # --- Blood Banks ---
    items.append(folder("Blood Banks", [
        req("List Blood Banks", "GET", "blood-banks"),
        req("Nearby Blood Banks", "GET", "blood-banks/nearby", query="lat=5.6037&lng=-0.1870&radiusKm=25"),
        req("Get Blood Bank by ID", "GET", "blood-banks/00000000-0000-0000-0000-000000000001"),
        req("Create Blood Bank", "POST", "blood-banks", body={
            "name": "Central Blood Bank", "location": "Accra", "address": "789 Blood Lane",
            "latitude": 5.6037, "longitude": -0.1870, "phone": "+233303123456",
            "operatingHours": "Mon-Fri 8:00-17:00", "status": "Open",
        }),
        req("Update Blood Bank", "PATCH", "blood-banks/00000000-0000-0000-0000-000000000001",
            body={"phone": "+233303999888"}),
    ]))

    # --- Screening ---
    items.append(folder("Screening", [
        req("List Screening Records", "GET", "screening/records", query="page=1&limit=20"),
        req("List Screenings (legacy)", "GET", "screening", query="page=1&limit=20"),
        req("Export Screenings", "GET", "screening/export", query="format=csv"),
        req("Get Screening Record", "GET", "screening/records/00000000-0000-0000-0000-000000000001"),
        req("Today's Schedules", "GET", "screening/schedules", query="page=1&limit=20"),
        req("Start Screening Session", "POST", "screening/sessions", body={
            "donorId": "{{donorId}}", "screeningDate": "2026-07-11",
            "vitals": {"bloodPressure": "120/80", "pulse": 72, "temperature": 36.6},
            "medicalHistory": {"recentIllness": False, "medications": []},
        }),
        req("Update Screening Session", "PATCH", "screening/sessions/00000000-0000-0000-0000-000000000001",
            body={"notes": "Updated screening notes"}),
        req("Complete Screening Session", "POST", "screening/sessions/00000000-0000-0000-0000-000000000001/complete", body={
            "eligibilityResult": "Eligible", "bloodGroup": "O+", "notes": "Donor cleared for donation",
        }),
        req("Complete Schedule", "POST", "screening/schedules/00000000-0000-0000-0000-000000000001/complete", body={}),
    ]))

    # --- Collections ---
    items.append(folder("Collections", [
        req("List Collections", "GET", "collections", query="page=1&limit=20"),
        req("Get Collection by ID", "GET", "collections/{{collectionId}}"),
        req("Create Collection", "POST", "collections", body={
            "donorId": "{{donorId}}", "staffId": "00000000-0000-0000-0000-000000000001",
            "bloodGroup": "O+", "bloodProductType": "WB", "volumeMl": 450,
            "collectionDate": "2026-07-11", "collectionTime": "10:30:00",
            "location": "Main Collection Center", "bagNumber": "BAG-2026-001",
            "preScreeningVitals": {"bloodPressure": "118/78", "hemoglobin": 14.2},
        }),
        req("Update Collection", "PATCH", "collections/{{collectionId}}", body={"notes": "Collection completed successfully"}),
        req("Collection Stats", "GET", "collections/stats"),
        req("List Sessions", "GET", "collections/sessions", query="page=1&limit=20"),
        req("Get Active Session", "GET", "collections/sessions/active"),
        req("Start Session", "POST", "collections/sessions", body={
            "staffId": "00000000-0000-0000-0000-000000000001", "location": "Collection Room A",
        }),
        req("Update Session", "PATCH", "collections/sessions/00000000-0000-0000-0000-000000000001",
            body={"notes": "Session in progress"}),
    ]))

    # --- Testing ---
    items.append(folder("Testing", [
        req("List Tests (legacy)", "GET", "testing", query="page=1&limit=20"),
        req("Get Test by ID (legacy)", "GET", "testing/00000000-0000-0000-0000-000000000001"),
        req("Create Test (legacy)", "POST", "testing", body={"collectionId": "{{collectionId}}"}),
        req("Update Test Results (legacy)", "PATCH", "testing/00000000-0000-0000-0000-000000000001", body={
            "tests": {"hiv": "Negative", "hepatitisB": "Negative", "hepatitisC": "Negative", "syphilis": "Negative"},
        }),
        req("Complete Test (legacy)", "POST", "testing/00000000-0000-0000-0000-000000000001/complete",
            body={"overallStatus": "Passed"}),
    ]))

    # --- Testing Results (spec) ---
    items.append(folder("Testing Results", [
        req("List Test Results", "GET", "testing/results", query="page=1&limit=20"),
        req("Get Test Result", "GET", "testing/results/00000000-0000-0000-0000-000000000001"),
        req("Create Test Result", "POST", "testing/results", body={"collectionId": "{{collectionId}}"}),
        req("Update Test Results", "PATCH", "testing/results/00000000-0000-0000-0000-000000000001", body={
            "tests": {"hiv": "Negative", "hepatitisB": "Negative", "hepatitisC": "Negative", "syphilis": "Negative"},
        }),
        req("Complete Test Result", "POST", "testing/results/00000000-0000-0000-0000-000000000001/complete",
            body={"overallStatus": "Passed"}),
    ]))

    # --- Inventory ---
    items.append(folder("Inventory", [
        req("Inventory Summary", "GET", "inventory/summary"),
        req("List Units", "GET", "inventory/units", query="page=1&limit=20"),
        req("Get Unit by ID", "GET", "inventory/units/{{bloodUnitId}}"),
        req("Update Unit", "PATCH", "inventory/units/{{bloodUnitId}}", body={"storageLocation": "Cold Room B-12"}),
        req("Reserve Unit", "POST", "inventory/units/{{bloodUnitId}}/reserve", body={"requestId": "{{requestId}}"}),
        req("Issue Unit", "POST", "inventory/units/{{bloodUnitId}}/issue", body={}),
        req("Release Unit", "POST", "inventory/units/{{bloodUnitId}}/release", body={}),
        req("Discard Unit", "POST", "inventory/units/{{bloodUnitId}}/discard", body={"reason": "Expired"}),
        req("Expiring Units", "GET", "inventory/expiring", query="withinDays=7"),
        req("Inventory Alerts", "GET", "inventory/alerts"),
    ]))

    # --- Requests ---
    items.append(folder("Requests", [
        req("List Requests", "GET", "requests", query="page=1&limit=20"),
        req("Get Request by ID", "GET", "requests/{{requestId}}"),
        req("Create Blood Request", "POST", "requests", body={
            "hospitalId": "{{hospitalId}}", "bloodGroup": "O+", "bloodProductType": "RBC",
            "unitsRequested": 3, "urgency": "High", "requiredBy": "2026-07-15",
            "patientName": "Patient A", "diagnosis": "Surgery", "department": "Emergency",
            "requestedBy": "Dr. Smith", "notes": "Urgent blood needed for surgery",
        }),
        req("Update Request", "PATCH", "requests/{{requestId}}", body={"notes": "Updated request notes"}),
        req("Approve Request", "POST", "requests/{{requestId}}/approve", body={}),
        req("Reject Request", "POST", "requests/{{requestId}}/reject", body={"reason": "Insufficient inventory"}),
        req("Process Request", "POST", "requests/{{requestId}}/process", body={}),
        req("Complete Request", "POST", "requests/{{requestId}}/complete", body={}),
        req("Request Progress", "GET", "requests/{{requestId}}/progress"),
        req("Request Receipt", "GET", "requests/{{requestId}}/receipt", query="format=json"),
        req("Request Shipping Label", "GET", "requests/{{requestId}}/label", query="format=pdf"),
    ]))

    # --- Deliveries ---
    items.append(folder("Deliveries", [
        req("List Deliveries", "GET", "deliveries", query="page=1&limit=20"),
        req("Get Delivery by ID", "GET", "deliveries/00000000-0000-0000-0000-000000000001"),
        req("Create Delivery", "POST", "deliveries", body={
            "requestId": "{{requestId}}", "hospitalId": "{{hospitalId}}",
            "bloodGroup": "O+", "bloodProductType": "RBC", "units": 2,
            "deliveryDate": "2026-07-12", "deliveryTime": "14:00:00",
            "deliveredBy": "Courier Team A",
        }),
        req("Update Delivery", "PATCH", "deliveries/00000000-0000-0000-0000-000000000001",
            body={"status": "In_Transit"}),
        req("Confirm Delivery", "POST", "deliveries/00000000-0000-0000-0000-000000000001/confirm",
            body={"receivedBy": "Nurse Jane"}),
    ]))

    # --- Patients ---
    items.append(folder("Patients", [
        req("List Patients", "GET", "patients", query="page=1&limit=20"),
        req("Get Patient by ID", "GET", "patients/00000000-0000-0000-0000-000000000001"),
        req("Create Patient", "POST", "patients", body={
            "hospitalId": "{{hospitalId}}", "name": "John Patient", "age": 45,
            "bloodGroup": "A+", "diagnosis": "Anemia", "status": "Critical",
        }),
        req("Update Patient", "PATCH", "patients/00000000-0000-0000-0000-000000000001",
            body={"status": "Stable"}),
        req("Delete Patient", "DELETE", "patients/00000000-0000-0000-0000-000000000001"),
    ]))

    # --- Appointments ---
    items.append(folder("Appointments", [
        req("List Appointments", "GET", "appointments", query="page=1&limit=20"),
        req("Get Appointment by ID", "GET", "appointments/00000000-0000-0000-0000-000000000001"),
        req("Create Appointment", "POST", "appointments", body={
            "donorId": "{{donorId}}", "date": "2026-07-20", "time": "09:00:00",
            "bloodProductType": "WB", "notes": "Regular donation appointment",
        }),
        req("Update Appointment", "PATCH", "appointments/00000000-0000-0000-0000-000000000001",
            body={"notes": "Rescheduled notes"}),
        req("Cancel Appointment", "POST", "appointments/00000000-0000-0000-0000-000000000001/cancel", body={}),
        req("Confirm Appointment", "POST", "appointments/00000000-0000-0000-0000-000000000001/confirm", body={}),
        req("List Appointment Requests", "GET", "appointment-requests", query="page=1&limit=20"),
        req("Approve Appointment Request", "POST", "appointment-requests/00000000-0000-0000-0000-000000000001/approve",
            body={"staffResponse": "Approved for next available slot"}),
        req("Reject Appointment Request", "POST", "appointment-requests/00000000-0000-0000-0000-000000000001/reject",
            body={"reason": "Donor not eligible"}),
    ]))

    # --- Notifications ---
    items.append(folder("Notifications", [
        req("List Notifications", "GET", "notifications", query="page=1&limit=20"),
        req("Unread Count", "GET", "notifications/unread-count"),
        req("Mark as Read", "PATCH", "notifications/00000000-0000-0000-0000-000000000001/read"),
        req("Mark All Read", "POST", "notifications/read-all", body={}),
    ]))

    # --- Compensations ---
    items.append(folder("Compensations", [
        req("List Compensations", "GET", "compensations", query="page=1&limit=20"),
        req("Get Compensation", "GET", "compensations/00000000-0000-0000-0000-000000000001"),
        req("Pay Compensation", "POST", "compensations/00000000-0000-0000-0000-000000000001/pay", body={
            "paymentMethod": "Mobile_Money", "notes": "Paid via MTN MoMo",
        }),
    ]))

    # --- Finances ---
    items.append(folder("Finances", [
        req("Finance Summary", "GET", "finances/summary"),
        req("List Transactions", "GET", "finances/transactions", query="page=1&limit=20"),
        req("Create Transaction", "POST", "finances/transactions", body={
            "type": "Expense", "amount": 1500.00, "description": "Lab supplies purchase",
            "category": "Supplies", "status": "Completed",
        }),
        req("Monthly Chart", "GET", "finances/charts/monthly", query="months=12"),
        req("Monthly Report (spec)", "GET", "finances/reports/monthly", query="months=12"),
        req("Breakdown Chart", "GET", "finances/charts/breakdown"),
        req("Export Finances", "GET", "finances/export", query="format=csv"),
    ]))

    # --- Reports ---
    items.append(folder("Reports", [
        req("Dashboard KPIs (spec)", "GET", "reports/dashboard"),
        req("Collections Monthly (spec)", "GET", "reports/collections/monthly"),
        req("Inventory Distribution (spec)", "GET", "reports/inventory/distribution"),
        req("Requests by Urgency (spec)", "GET", "reports/requests/by-urgency", query="from=2026-01-01&to=2026-07-11"),
        req("Demand Prediction (spec)", "GET", "reports/demand-prediction"),
        req("Live Monitoring (spec)", "GET", "reports/live-monitoring"),
        req("Collections Report", "GET", "reports/collections", query="from=2026-01-01&to=2026-07-11"),
        req("Inventory Report", "GET", "reports/inventory"),
        req("Requests Report", "GET", "reports/requests", query="from=2026-01-01&to=2026-07-11"),
        req("Donors Report", "GET", "reports/donors"),
        req("Custom Report", "GET", "reports/custom", query="from=2026-01-01&to=2026-07-11&type=summary"),
        req("Generate Report", "POST", "reports/generate", body={
            "type": "inventory", "format": "pdf", "from": "2026-01-01", "to": "2026-07-11",
        }),
        req("Get Report Job", "GET", "reports/jobs/00000000-0000-0000-0000-000000000001"),
        req("Download Report Job", "GET", "reports/jobs/00000000-0000-0000-0000-000000000001/download"),
    ]))

    # --- Activity Logs ---
    items.append(folder("Activity Logs", [
        req("List Activity Logs", "GET", "activity-logs", query="page=1&limit=20&sort=-timestamp"),
        req("Get Activity Log by ID", "GET", "activity-logs/00000000-0000-0000-0000-000000000001"),
        req("Export Activity Logs", "GET", "activity-logs/export", query="format=csv"),
    ]))

    # --- Settings ---
    items.append(folder("Settings", [
        req("Get System Settings", "GET", "settings/system"),
        req("Update System Settings", "PATCH", "settings/system", body={"organizationName": "BloodBank Ghana"}),
        req("Get Notification Settings", "GET", "settings/notifications"),
        req("Update Notification Settings", "PATCH", "settings/notifications", body={
            "emailAlerts": True, "smsAlerts": False, "lowInventoryThreshold": 10,
        }),
        req("Get Security Settings", "GET", "settings/security"),
        req("Update Security Settings", "PATCH", "settings/security", body={
            "sessionTimeoutMinutes": 30, "requireMfa": False, "passwordMinLength": 8,
        }),
    ]))

    # --- Donor Portal ---
    items.append(folder("Donor Portal", [
        req("Dashboard", "GET", "donor/dashboard"),
        req("Profile", "GET", "donor/profile"),
        req("Update Profile", "PATCH", "donor/profile", body={"phone": "+233201234567"}),
        req("Donation History", "GET", "donor/donation-history", query="page=1&limit=20"),
        req("Rewards", "GET", "donor/rewards"),
        req("Redeem Reward", "POST", "donor/rewards/redeem", body={"rewardId": "gold-badge"}),
        req("Compensation", "GET", "donor/compensation", query="page=1&limit=20"),
    ]))

    # --- Rewards (spec) ---
    items.append(folder("Rewards", [
        req("Reward Tiers", "GET", "rewards/tiers"),
        req("Donor Rewards", "GET", "rewards/{{donorId}}"),
    ]))

    # --- Hospital Portal ---
    items.append(folder("Hospital Portal", [
        req("Dashboard", "GET", "hospital/dashboard"),
        req("Profile", "GET", "hospital/profile"),
        req("Update Profile", "PATCH", "hospital/profile", body={"phone": "+233302123456"}),
        req("Requests", "GET", "hospital/requests", query="page=1&limit=20"),
        req("Deliveries", "GET", "hospital/deliveries", query="page=1&limit=20"),
        req("Notifications", "GET", "hospital/notifications", query="page=1&limit=20"),
        req("Inventory Availability", "GET", "hospital/inventory-availability"),
    ]))

    # --- Specialist Portal ---
    items.append(folder("Specialist Portal", [
        req("Dashboard", "GET", "specialist/dashboard"),
        req("Schedules", "GET", "specialist/schedules", query="page=1&limit=20"),
        req("Records", "GET", "specialist/records", query="page=1&limit=20"),
        req("Start Session", "POST", "specialist/sessions", body={
            "donorId": "{{donorId}}", "screeningDate": "2026-07-11",
            "vitals": {"bloodPressure": "120/80", "pulse": 72},
        }),
        req("Update Session", "PATCH", "specialist/sessions/00000000-0000-0000-0000-000000000001",
            body={"notes": "Screening session updated"}),
    ]))

    # --- WebSocket note as a placeholder request ---
    ws_item = {
        "name": "WebSocket - Inventory (reference)",
        "request": {
            "method": "GET",
            "header": [],
            "url": {
                "raw": "ws://localhost:8081/ws/inventory?token={{accessToken}}",
                "protocol": "ws",
                "host": ["localhost"],
                "port": "8081",
                "path": ["ws", "inventory"],
                "query": [{"key": "token", "value": "{{accessToken}}"}],
            },
            "description": "WebSocket endpoint for real-time inventory updates. Use a WebSocket client (not standard HTTP). Connect to: ws://localhost:8081/ws/inventory?token={{accessToken}}",
        },
        "response": [],
    }
    items.append(folder("WebSocket", [ws_item], "Real-time inventory WebSocket. Requires a WebSocket client."))

    description = """# BloodBank API Collection

## Import Instructions
1. Open Postman and click **Import**
2. Import both files from the `postman/` folder:
   - `BloodBank-API.postman_collection.json`
   - `BloodBank-Local.postman_environment.json`
3. Select the **BloodBank Local** environment from the environment dropdown
4. Ensure the backend is running at `http://localhost:8081`

## Demo Flow
1. Run **Health > Health Check** to verify the server is up
2. Run **Auth > Login - Admin** (or any role login) — tokens are saved automatically to collection variables
3. Use role-specific tokens (`adminToken`, `staffToken`, etc.) or set `accessToken` manually
4. Replace placeholder UUIDs (`donorId`, `hospitalId`, `requestId`, etc.) with real IDs from list responses
5. For WebSocket inventory updates, connect to `ws://localhost:8081/ws/inventory?token={{accessToken}}`

## Demo Credentials (password: demo123)
| Role | Email | Role field |
|------|-------|------------|
| Admin | admin@bloodbank.com | admin |
| Staff | staff@bloodbank.com | staff |
| Donor | donor@email.com | donor |
| Hospital | hospital@stmarys.com | hospital |
| Specialist | specialist@bloodbank.com | specialist |

## Notes
- Default auth uses Bearer `{{accessToken}}` — run a login request first
- All API paths are relative to `{{baseUrl}}` (`http://localhost:8081/api/v1`)
- Responses follow `{ success, data, error, meta }` envelope format
"""

    collection = {
        "info": {
            "_postman_id": uid(),
            "name": "BloodBank API",
            "description": description,
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
        },
        "auth": deepcopy(DEFAULT_AUTH),
        "variable": [{"key": k, "value": "http://localhost:8081/api/v1" if k == "baseUrl" else ""} for k in COLLECTION_VARS],
        "item": items,
    }
    return collection


def build_environment():
    return {
        "id": uid(),
        "name": "BloodBank Local",
        "values": [
            {"key": "baseUrl", "value": "http://localhost:8081/api/v1", "type": "default", "enabled": True},
            {"key": "accessToken", "value": "", "type": "secret", "enabled": True},
            {"key": "refreshToken", "value": "", "type": "secret", "enabled": True},
            {"key": "adminToken", "value": "", "type": "secret", "enabled": True},
            {"key": "staffToken", "value": "", "type": "secret", "enabled": True},
            {"key": "donorToken", "value": "", "type": "secret", "enabled": True},
            {"key": "hospitalToken", "value": "", "type": "secret", "enabled": True},
            {"key": "specialistToken", "value": "", "type": "secret", "enabled": True},
            {"key": "donorId", "value": "", "type": "default", "enabled": True},
            {"key": "hospitalId", "value": "", "type": "default", "enabled": True},
            {"key": "requestId", "value": "", "type": "default", "enabled": True},
            {"key": "collectionId", "value": "", "type": "default", "enabled": True},
            {"key": "bloodUnitId", "value": "", "type": "default", "enabled": True},
            {"key": "compensationId", "value": "", "type": "default", "enabled": True},
        ],
        "_postman_variable_scope": "environment",
        "_postman_exported_at": "2026-07-11T00:00:00.000Z",
        "_postman_exported_using": "BloodBank Generator",
    }


def count_requests(items):
    total = 0
    for item in items:
        if "item" in item:
            total += count_requests(item["item"])
        else:
            total += 1
    return total


def main():
    collection = build_collection()
    env = build_environment()
    total = count_requests(collection["item"])

    coll_path = "BloodBank-API.postman_collection.json"
    env_path = "BloodBank-Local.postman_environment.json"

    with open(coll_path, "w", encoding="utf-8") as f:
        json.dump(collection, f, indent=2, ensure_ascii=False)

    with open(env_path, "w", encoding="utf-8") as f:
        json.dump(env, f, indent=2, ensure_ascii=False)

    print(f"Created {coll_path} with {total} requests")
    print(f"Created {env_path}")


if __name__ == "__main__":
    main()
