# BloodBank Pro — Backend Specification

**Version:** 1.0  
**Date:** August 6, 2026  
**Source:** Derived from frontend application audit (`src/app/`)  
**Status:** Specification (no backend server exists today)

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Current State vs Target State](#2-current-state-vs-target-state)
3. [System Architecture](#3-system-architecture)
4. [User Roles & Authorization](#4-user-roles--authorization)
5. [Data Models](#5-data-models)
6. [API Endpoints](#6-api-endpoints)
7. [Business Rules & Workflows](#7-business-rules--workflows)
8. [Authentication & Security](#8-authentication--security)
9. [Notifications](#9-notifications)
10. [Background Jobs & Scheduled Tasks](#10-background-jobs--scheduled-tasks)
11. [External Integrations](#11-external-integrations)
12. [Error Handling](#12-error-handling)
13. [Environment Configuration](#13-environment-configuration)
14. [Appendix](#14-appendix)

---

## 1. Executive Summary

**BloodBank Pro** is a multi-portal blood bank management system supporting five user roles:

| Portal | Role | Primary Functions |
|--------|------|-------------------|
| Admin | `admin` | System oversight, staff/hospital management, reports, finances, activity logs |
| Staff | `staff` | Collections, inventory, request approval, donor management, payments |
| Specialist | `specialist` | Donor screening sessions, schedule management, screening records |
| Donor | `donor` | Registration, appointment scheduling, donation history, rewards |
| Hospital | `hospital` | Blood requests, geo-search, request tracking, profile |

The current codebase is a **frontend-only React/Vite prototype**. All domain data lives in `src/app/utils/mockData.ts`; authentication is client-side mock auth; persistence is limited to browser `localStorage`. This document specifies the **complete backend** required to productionize the application.

---

## 2. Current State vs Target State

### Current State (Prototype)

| Layer | Implementation |
|-------|----------------|
| Data | Static arrays in `mockData.ts` (~1,450 lines) |
| Auth | `AuthContext` — password `demo123`, session in `localStorage` key `bloodbank_user` |
| Staff RBAC | `StaffAuthContext` — hardcoded Senior Staff user with permission flags |
| Mutations | React `useState` + optional `localStorage` (`activityLogs`, `completedDonors`) |
| API | None — no HTTP calls, no server |
| Database | None |
| External services | None (UI references email/SMS/maps are cosmetic) |

### Target State

| Layer | Recommended Implementation |
|-------|--------------------------|
| API | REST API (Node/Express, Python/FastAPI, or similar) |
| Database | PostgreSQL (relational, audit-friendly) |
| Auth | JWT access tokens + refresh tokens; bcrypt password hashing |
| RBAC | Role-based + permission matrix enforced server-side |
| Real-time | WebSockets or SSE for live monitoring and notifications (optional) |
| Jobs | Cron/worker for expiry alerts, eligibility checks, report generation |
| Storage | Object storage for documents (ID scans, receipts) |

---

## 3. System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     React Frontend (Vite)                        │
│  Portals: Admin | Staff | Specialist | Donor | Hospital         │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTPS / REST (+ optional WebSocket)
┌────────────────────────────▼────────────────────────────────────┐
│                      API Gateway / Load Balancer                 │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                     Application Server                           │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │   Auth   │ │  Donors  │ │Inventory │ │ Requests │  ...      │
│  │ Service  │ │ Service  │ │ Service  │ │ Service  │           │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘           │
│  Middleware: JWT auth, RBAC, rate limit, audit log, validation   │
└──────┬──────────────┬──────────────┬──────────────┬─────────────┘
       │              │              │              │
┌──────▼──────┐ ┌─────▼─────┐ ┌─────▼─────┐ ┌─────▼─────┐
│ PostgreSQL  │ │   Redis   │ │  S3/Blob  │ │ Job Queue │
│  (primary)  │ │ (cache/   │ │ (files)   │ │ (Bull/    │
│             │ │  sessions)│ │           │ │  Celery)  │
└─────────────┘ └───────────┘ └───────────┘ └───────────┘
       │
┌──────▼──────────────────────────────────────────────────────┐
│ External: Email (SendGrid), SMS (Twilio), Maps (Google),       │
│           Payment (Mobile Money API), PDF generation           │
└───────────────────────────────────────────────────────────────┘
```

### Recommended API Base URL

```
Production:  https://api.bloodbank.pro/v1
Development: http://localhost:3001/api/v1
```

### Standard Response Envelope

```json
{
  "success": true,
  "data": { },
  "meta": {
    "page": 1,
    "limit": 20,
    "total": 150
  },
  "error": null
}
```

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Donor must be at least 18 years old",
    "details": [{ "field": "dateOfBirth", "message": "..." }]
  }
}
```

---

## 4. User Roles & Authorization

### 4.1 Portal Roles (`UserRole`)

| Role | ID Prefix | Description |
|------|-----------|-------------|
| `admin` | `admin*` | Full system access |
| `staff` | `GS*`, `STF-*` | Day-to-day operations |
| `specialist` | `SP*` | Screening and lab workflows |
| `donor` | `D*`, `DNR*` | Self-service donor portal |
| `hospital` | `H*`, `hosp*` | Hospital blood request portal |

### 4.2 Staff Sub-Roles (`StaffRole`)

| Role | Permissions |
|------|-------------|
| **Admin** | All permissions including `canManageStaff` |
| **Senior Staff** | Approve/reject requests, manage inventory/donors, conduct withdrawals, view reports |
| **Junior Staff** | Manage inventory/donors, conduct withdrawals (no approve/reject, no reports) |
| **Technician** | `canConductWithdrawals` only |

### 4.3 Permission Matrix

| Permission | Admin | Senior Staff | Junior Staff | Technician |
|------------|:-----:|:------------:|:------------:|:----------:|
| `canApproveRequests` | ✓ | ✓ | ✗ | ✗ |
| `canRejectRequests` | ✓ | ✓ | ✗ | ✗ |
| `canManageInventory` | ✓ | ✓ | ✓ | ✗ |
| `canManageDonors` | ✓ | ✓ | ✓ | ✗ |
| `canConductWithdrawals` | ✓ | ✓ | ✓ | ✓ |
| `canViewReports` | ✓ | ✓ | ✗ | ✗ |
| `canManageStaff` | ✓ | ✗ | ✗ | ✗ |

### 4.4 Sensitive Action Re-Authentication

Approve/reject blood requests require **step-up verification** (mirrors `StaffVerificationModal`):

- Staff must re-enter their **Staff ID** (must match authenticated session)
- Staff must provide an **authorization password** (separate from login password)
- Server validates both before mutating request status
- Action is recorded in activity logs with IP address

---

## 5. Data Models

### 5.1 Reference Data

#### BloodType

| Field | Type | Values |
|-------|------|--------|
| `id` | string | UUID or sequential |
| `name` | string | Whole Blood, Red Blood Cells, Plasma, Platelets, Cryoprecipitate |
| `code` | string | `WB`, `RBC`, `PLS`, `PLT`, `CRYO` |
| `defaultVolumeMl` | number | 450 (WB/RBC), 600 (PLS), 300 (PLT) |
| `shelfLifeDays` | number | See [Appendix A](#appendix-a-blood-component-shelf-life) |

#### BloodGroup

Enum: `A+`, `A-`, `B+`, `B-`, `AB+`, `AB-`, `O+`, `O-`

---

### 5.2 User & Auth

#### User (base)

```typescript
interface User {
  id: string;
  email: string;
  passwordHash: string;
  role: 'admin' | 'staff' | 'specialist' | 'donor' | 'hospital';
  name: string;
  status: 'active' | 'inactive' | 'suspended';
  createdAt: datetime;
  updatedAt: datetime;
  lastLoginAt: datetime | null;
}
```

#### Staff (extends User)

```typescript
interface Staff {
  id: string;                    // e.g. GS001, SP001
  userId: string;
  name: string;
  role: 'Specialist' | 'General Staff';
  staffRole: 'Admin' | 'Senior Staff' | 'Junior Staff' | 'Technician';
  email: string;
  phone: string;
  department: string;            // Testing, Collection, Storage, Administration
  status: 'Active' | 'Inactive';
  joinDate: date;
  shift: 'Day' | 'Night';
  certifications: string[];
  authorizationPasswordHash: string;  // for step-up auth on approve/reject
}
```

#### HospitalUser (extends User)

```typescript
interface HospitalUser {
  id: string;
  userId: string;
  hospitalId: string;            // FK → Hospital
}
```

---

### 5.3 Donor

```typescript
interface Donor {
  id: string;                    // D001, DNR######
  userId: string | null;         // FK → User (after registration)
  
  // Personal
  firstName: string;
  middleName: string | null;
  lastName: string;
  dateOfBirth: date;
  gender: 'Male' | 'Female' | 'Other';
  maritalStatus: string | null;
  nationality: string;
  
  // Contact
  email: string;
  phone: string;
  alternatePhone: string | null;
  address: string;
  city: string;
  region: string;
  postalCode: string | null;
  location: string;              // display string e.g. "New York, NY"
  
  // Identification
  idType: string;                // National ID, Passport, Driver's License
  idNumber: string;
  idExpiryDate: date;
  
  // Physical
  height: number;                // cm (140–220)
  weight: number;                // kg (min 50)
  bloodGroup: BloodGroup | 'Pending';
  
  // Medical (from screening)
  chronicDiseases: string | null;
  allergies: string | null;
  currentMedications: string | null;
  smokingStatus: string;
  alcoholConsumption: string;
  occupation: string | null;
  
  // Emergency contact
  emergencyContactName: string;
  emergencyContactRelationship: string;
  emergencyContactPhone: string;
  emergencyContactAddress: string | null;
  
  // Donation tracking
  lastDonation: date | null;
  totalDonations: number;
  status: 'Eligible' | 'Not Eligible' | 'Pending Screening';
  nextEligible: date | null;
  registeredDate: date;
  
  createdAt: datetime;
  updatedAt: datetime;
}
```

**Relationships:**
- Donor → many `Collection`
- Donor → many `Appointment`
- Donor → many `DonationHistory`
- Donor → many `BloodUnit`
- Donor → many `ScreeningRecord`

---

### 5.4 Hospital

```typescript
interface Hospital {
  id: string;                    // H001
  name: string;
  registrationNumber: string;
  registrationDate: date;
  location: string;
  address: string;
  phone: string;
  emergencyPhone: string | null;
  email: string;
  website: string | null;
  capacity: 'Small' | 'Medium' | 'Large';
  beds: number | null;
  departments: string[];
  latitude: number;
  longitude: number;
  operatingHours: string;
  status: 'Active' | 'Inactive';
  accreditation: string | null;
  licenses: string[];
  
  primaryContact: {
    name: string;
    title: string;
    phone: string;
    email: string;
  };
  
  bloodBankCoordinator: {
    name: string;
    phone: string;
    email: string;
  };
  
  // Computed/aggregated (not stored, or materialized)
  totalRequests: number;
  pendingRequests: number;
}
```

---

### 5.5 Patient

```typescript
interface Patient {
  id: string;                    // P001
  name: string;
  bloodGroup: BloodGroup;
  hospitalId: string;            // FK → Hospital
  hospital: string;                // denormalized name
  diagnosis: string;
  requiredUnits: number;
  status: 'Critical' | 'Stable' | 'Improving';
  admissionDate: date;
  age: number;
  gender: string;
}
```

---

### 5.6 Blood Request

```typescript
interface BloodRequest {
  id: string;                    // REQ001
  hospitalId: string;            // FK → Hospital
  hospital: string;
  bloodBankId: string | null;    // FK → BloodBank (fulfilling center)
  patientId: string | null;      // FK → Patient
  patientName: string | null;
  diagnosis: string | null;
  
  bloodGroup: BloodGroup;
  bloodType: string;             // display name or code
  units: number;
  urgency: 'Critical' | 'High' | 'Medium' | 'Low';
  status: 'Pending' | 'Approved' | 'Processing' | 'Completed' | 'Rejected';
  
  requestDate: date;
  requiredBy: date;
  completedDate: date | null;
  
  requestedBy: string | null;    // doctor name
  department: string | null;
  deliveredBy: string | null;
  notes: string | null;
  
  createdAt: datetime;
  updatedAt: datetime;
}
```

**Status transitions:**

```
Pending → Approved → Processing → Completed
Pending → Rejected
```

---

### 5.7 Blood Collection

```typescript
interface Collection {
  id: string;                    // COL001
  donorId: string;               // FK → Donor
  donorName: string;
  bloodGroup: BloodGroup;
  bloodType: string;
  collectionDate: date;
  volume: number;                // ml
  status: 'Collected' | 'Testing' | 'Tested' | 'Stored' | 'Rejected';
  testResult: 'Pending' | 'Passed' | 'Failed';
  staffId: string;               // FK → Staff
  staffName: string;
  location: string;              // blood center name
  createdAt: datetime;
}
```

---

### 5.8 Blood Unit (Inventory)

```typescript
interface BloodUnit {
  id: string;                    // WB-001, RBC-001, etc.
  bloodGroup: BloodGroup;
  bloodType: 'WB' | 'RBC' | 'PLS' | 'PLT' | 'CRYO';
  donorId: string;               // FK → Donor
  collectionId: string;          // FK → Collection
  collectionDate: date;
  expiryDate: date;
  status: 'Available' | 'Reserved' | 'Issued' | 'Expired' | 'Discarded';
  location: string;              // storage location
  requestId: string | null;      // FK → BloodRequest (when reserved)
  createdAt: datetime;
}
```

#### Aggregated Inventory (computed view)

```typescript
interface InventorySummary {
  bloodGroup: BloodGroup;
  wholeBlood: number;
  redBloodCells: number;
  plasma: number;
  platelets: number;
  cryop: number;
  trend: 'up' | 'down' | 'stable' | 'critical';
}
```

**Expiry urgency levels (frontend logic):**
- **Expired:** `expiryDate < today`
- **Critical:** ≤ 7 days remaining
- **Warning:** ≤ 30 days remaining
- **Safe:** > 30 days remaining

---

### 5.9 Testing Result

```typescript
interface TestingResult {
  id: string;                    // TEST001
  collectionId: string;          // FK → Collection
  donorId: string;
  donorName: string;
  bloodGroup: BloodGroup;
  testDate: date;
  tests: TestItem[];
  overallStatus: 'Pending' | 'Passed' | 'Failed';
  technician: string;
}

interface TestItem {
  name: string;                  // HIV, Hepatitis B, Hepatitis C, Syphilis, Malaria, Blood Type Confirmation
  result: string;                // Negative, Positive, Pending, or blood type
  status: 'Complete' | 'In Progress' | 'Pending';
}
```

---

### 5.10 Screening Record

Full screening session data (from `ScreeningSession.tsx`):

```typescript
interface ScreeningRecord {
  id: string;
  appointmentId: string | null;
  donorId: string;               // generated on completion
  specialistId: string;          // FK → Staff
  
  // All ScreeningFormData fields (see Donor model)
  // Plus vitals & lab results:
  hemoglobinLevel: number;
  bloodPressureSystolic: number;
  bloodPressureDiastolic: number;
  temperature: number;
  rhFactor: string;
  hivTest: string;
  hepatitisBTest: string;
  hepatitisCTest: string;
  syphilisTest: string;
  malariaTest: string;
  overallTestStatus: string;
  testNotes: string | null;
  
  completedAt: datetime;
}
```

---

### 5.11 Appointment

```typescript
interface Appointment {
  id: string;                    // APT001
  code: string;                  // APPT-XXXXXX (human-readable)
  donorId: string;
  donorName: string;
  date: date;
  time: string;
  location: string;              // blood bank name
  bloodBankId: string;
  bloodType: string;
  status: 'Scheduled' | 'Confirmed' | 'Completed' | 'Cancelled' | 'No-Show';
  notes: string | null;
  createdAt: datetime;
}
```

#### Appointment Request (staff review queue)

```typescript
interface AppointmentRequest {
  id: string;                    // APTREQ001
  donorId: string;
  donorName: string;
  donorEmail: string;
  donorPhone: string;
  bloodGroup: BloodGroup;
  requestedDate: date;
  requestedTime: string;
  location: string;
  donationType: string;
  status: 'Pending' | 'Approved' | 'Rejected';
  notes: string | null;
  requestDate: date;
  lastDonation: date | null;
}
```

---

### 5.12 Delivery

```typescript
interface Delivery {
  id: string;                    // DEL001
  requestId: string;             // FK → BloodRequest
  bloodGroup: BloodGroup;
  bloodType: string;
  units: number;
  deliveryDate: date;
  deliveryTime: string;
  deliveredBy: string;
  receivedBy: string | null;
  status: 'Scheduled' | 'In Transit' | 'Delivered' | 'Cancelled';
  temperature: string;
  condition: string;
  notes: string | null;
}
```

---

### 5.13 Donor Compensation (Payment)

```typescript
interface Compensation {
  id: string;                    // COMP001
  donorId: string;
  donorName: string;
  donorPhone: string;
  bloodGroup: BloodGroup;
  collectionId: string;
  collectionDate: date;
  collectionTime: string;
  amount: number;                // default from system settings (e.g. 75)
  status: 'Pending' | 'Processing' | 'Paid' | 'Failed';
  paymentMethod: 'Cash' | 'Mobile Money' | 'Bank Transfer';
  bloodType: string;
  volume: number;
  staffId: string;
  staffName: string;
  location: string;
  paidDate: date | null;
  paidTime: string | null;
  createdAt: datetime;
}
```

---

### 5.14 Financial Transaction

```typescript
interface Transaction {
  id: string;                    // TXN001
  date: date;
  time: string;
  type: 'Revenue' | 'Expense';
  category: string;              // Hospital Payment, Donor Compensation, Staff Salaries, etc.
  description: string;
  amount: number;                // negative for expenses
  status: 'Completed' | 'Pending' | 'Failed';
  reference: string;             // REQ001, D012, etc.
  paymentMethod: string;
}
```

---

### 5.15 Activity Log (Audit Trail)

```typescript
interface ActivityLog {
  id: string;                    // LOG001
  timestamp: datetime;
  action: string;
  actionType: 'approve' | 'reject' | 'create' | 'update' | 'delete' | 'collection' | 'testing' | 'auth';
  description: string;
  staffId: string;
  staffName: string;
  staffRole: string;
  category: string;              // Request Management, Donor Management, Inventory, etc.
  details: string;
  ipAddress: string;
  
  // Optional contextual FKs
  requestId: string | null;
  hospitalName: string | null;
  hospitalId: string | null;
  donorId: string | null;
  donorName: string | null;
  collectionId: string | null;
  bloodGroup: BloodGroup | null;
  units: number | null;
  volume: number | null;
}
```

**Rules:** Append-only. Never update or delete audit records.

---

### 5.16 Notification

```typescript
interface Notification {
  id: string;
  userId: string;                // recipient
  type: 'critical' | 'urgent' | 'warning' | 'info' | 'success';
  title: string;
  message: string;
  timestamp: datetime;
  read: boolean;
  requestId: string | null;      // optional link
  channel: 'in_app' | 'email' | 'sms';
}
```

---

### 5.17 Blood Bank (Facility)

```typescript
interface BloodBank {
  id: string;                    // BB001
  name: string;
  location: string;
  address: string;
  phone: string;
  email: string | null;
  latitude: number;
  longitude: number;
  operatingHours: string;
  availableServices: string[];   // Donation, Testing, Storage, Emergency
  status: 'Open' | 'Closed';
  rating: number | null;
  acceptedDonationTypes: string[];
}
```

---

### 5.18 Donation History

```typescript
interface DonationHistory {
  id: string;                    // DH001
  donorId: string;
  date: date;
  bloodType: string;
  volume: number;
  location: string;
  reward: string | null;         // Bronze Medal, Certificate, Thank You Card
  collectionId: string | null;
}
```

---

### 5.19 System Settings

```typescript
interface SystemSettings {
  bloodBankName: string;
  contactEmail: string;
  contactPhone: string;
  address: string;
  donorCompensation: number;     // default amount per donation
  minDonationInterval: number;   // days (default 90)
  minAge: number;                // default 18
  maxAge: number;                // default 65
  minWeight: number;             // kg, default 50
}

interface NotificationSettings {
  lowInventoryAlert: boolean;
  expiryNotification: boolean;
  donorEligibility: boolean;
  requestNotification: boolean;
  emailNotifications: boolean;
  smsNotifications: boolean;
}

interface SecuritySettings {
  sessionTimeout: number;        // minutes
  passwordExpiry: number;        // days
  twoFactorAuth: boolean;
  maxLoginAttempts: number;
}
```

---

### 5.20 Entity Relationship Diagram

```
User ──┬── Staff
       ├── Donor ──┬── Appointment
       │           ├── Collection ──┬── TestingResult
       │           │                └── BloodUnit
       │           ├── DonationHistory
       │           ├── ScreeningRecord
       │           └── Compensation
       └── HospitalUser ── Hospital ──┬── Patient
                                        └── BloodRequest ──┬── Delivery
                                                           └── BloodUnit (reserved)

Staff ── ActivityLog (audit)
BloodBank ── Appointment, Collection (location)
```

---

## 6. API Endpoints

All endpoints require `Authorization: Bearer <access_token>` unless marked **Public**.

### 6.1 Authentication

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/auth/login` | Public | Login with email, password, role |
| POST | `/auth/logout` | Required | Invalidate refresh token |
| POST | `/auth/refresh` | Public | Exchange refresh token for new access token |
| GET | `/auth/me` | Required | Current user profile |
| POST | `/auth/register/donor` | Public | Donor self-registration |
| POST | `/auth/change-password` | Required | Change own password |
| POST | `/auth/verify-staff-action` | Staff | Step-up auth for approve/reject |

#### POST `/auth/login`

**Request:**
```json
{
  "email": "staff@bloodbank.com",
  "password": "********",
  "role": "staff"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "user": {
      "id": "staff1",
      "name": "Staff Member",
      "email": "staff@bloodbank.com",
      "role": "staff"
    },
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "expiresIn": 3600
  }
}
```

#### POST `/auth/register/donor`

**Request:**
```json
{
  "firstName": "John",
  "lastName": "Smith",
  "email": "john@email.com",
  "password": "********",
  "dateOfBirth": "1990-05-15",
  "gender": "Male",
  "idType": "National ID",
  "idNumber": "GHA-123456789",
  "agreeToTerms": true
}
```

**Validation rules:**
- Email: valid format, unique
- Password: min 6 characters
- Age: 18–65 (from `dateOfBirth`)
- `agreeToTerms`: must be `true`

**Response:** `201 Created` with donor ID and confirmation message trigger (email).

---

### 6.2 Donors

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/donors` | admin, staff | List donors (paginated, filterable) |
| GET | `/donors/:id` | admin, staff, donor (own) | Get donor by ID |
| POST | `/donors` | admin, staff | Register donor (admin modal) |
| PUT | `/donors/:id` | admin, staff, donor (own) | Update donor profile |
| DELETE | `/donors/:id` | admin | Soft-delete (requires admin password) |
| GET | `/donors/:id/history` | admin, staff, donor (own) | Donation history |
| GET | `/donors/:id/eligibility` | admin, staff, donor (own) | Eligibility check |
| GET | `/donors/:id/collections` | admin, staff | All collections for donor |

**Query params (GET `/donors`):**
- `search`, `bloodGroup`, `status`, `page`, `limit`, `sortBy`, `sortOrder`

---

### 6.3 Screening

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/screening/records` | specialist, admin, staff | List screening records |
| GET | `/screening/records/:id` | specialist, admin, staff | Get record detail |
| POST | `/screening/sessions` | specialist | Start screening session |
| PUT | `/screening/sessions/:id` | specialist | Save draft (multi-step) |
| POST | `/screening/sessions/:id/complete` | specialist | Complete screening, create donor |
| GET | `/screening/schedules` | specialist | Today's scheduled donors |
| POST | `/screening/schedules/:appointmentId/complete` | specialist | Mark donor as done |

#### POST `/screening/sessions/:id/complete`

Creates donor record with generated ID (`DNR######`), runs validation:
- Age 18–65
- Weight ≥ 50 kg
- Height 140–220 cm
- ID not expired
- Required emergency contact fields

---

### 6.4 Appointments

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/appointments` | admin, staff, specialist, donor (own) | List appointments |
| POST | `/appointments` | donor | Schedule new appointment |
| PUT | `/appointments/:id` | donor (own), staff | Reschedule |
| DELETE | `/appointments/:id` | donor (own), staff | Cancel appointment |
| GET | `/appointment-requests` | staff | Pending appointment requests |
| POST | `/appointment-requests/:id/approve` | staff | Approve request → create appointment |
| POST | `/appointment-requests/:id/reject` | staff | Reject request |

#### POST `/appointments`

**Request:**
```json
{
  "bloodBankId": "BB001",
  "date": "2026-03-15",
  "time": "10:00 AM",
  "bloodType": "Whole Blood",
  "notes": "Prefer morning slot"
}
```

**Response:** Includes generated `code` (e.g. `APPT-7B3M9K`).

---

### 6.5 Collections

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/collections` | admin, staff | List collections |
| GET | `/collections/:id` | admin, staff | Get collection detail |
| POST | `/collections` | staff | Record new collection |
| PUT | `/collections/:id` | staff | Update status/test result |
| GET | `/collections/stats` | admin, staff | Collection statistics |

**On collection complete (test passed):**
1. Create `BloodUnit` record(s) with computed `expiryDate`
2. Update donor `lastDonation`, `totalDonations`, `nextEligible`
3. Create `Compensation` record (status: Pending)
4. Append activity log

---

### 6.6 Testing

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/testing/results` | admin, staff, specialist | List test results |
| GET | `/testing/results/:id` | admin, staff, specialist | Get result detail |
| POST | `/testing/results` | staff, specialist | Create test record |
| PUT | `/testing/results/:id` | staff, specialist | Update individual tests |
| POST | `/testing/results/:id/complete` | staff, specialist | Finalize overall status |

**Standard test panel:** HIV, Hepatitis B, Hepatitis C, Syphilis, Malaria, Blood Type Confirmation

---

### 6.7 Inventory

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/inventory/summary` | admin, staff, hospital | Aggregated counts by blood group/type |
| GET | `/inventory/units` | admin, staff | List individual blood units |
| GET | `/inventory/units/:id` | admin, staff | Unit detail |
| PUT | `/inventory/units/:id` | staff | Update status/location |
| POST | `/inventory/units/:id/reserve` | staff | Reserve for request |
| POST | `/inventory/units/:id/issue` | staff | Issue to hospital delivery |
| POST | `/inventory/units/:id/discard` | staff | Mark expired/discarded |
| GET | `/inventory/expiring` | admin, staff | Units expiring within N days |
| GET | `/inventory/alerts` | admin, staff | Critical/low stock alerts |

---

### 6.8 Blood Requests

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/requests` | admin, staff, hospital (own) | List requests |
| GET | `/requests/:id` | admin, staff, hospital (own) | Request detail |
| POST | `/requests` | hospital | Submit new request |
| POST | `/requests/:id/approve` | staff (Senior+) | Approve → status Processing |
| POST | `/requests/:id/reject` | staff (Senior+) | Reject request |
| POST | `/requests/:id/complete` | staff | Mark delivery complete |
| GET | `/requests/:id/receipt` | admin, staff, hospital (own) | Generate receipt PDF |
| GET | `/requests/:id/label` | staff | Generate shipping label |

#### POST `/requests`

**Request:**
```json
{
  "bloodBankId": "BB001",
  "bloodGroup": "O-",
  "bloodType": "Red Blood Cells",
  "units": 4,
  "urgency": "Critical",
  "patientName": "Robert Taylor",
  "patientId": "P001",
  "diagnosis": "Emergency surgery - trauma",
  "requiredBy": "2026-02-24",
  "notes": "Emergency surgery case"
}
```

**Required fields:** `bloodBankId`, `bloodGroup`, `bloodType`, `units`, `urgency`

#### POST `/requests/:id/approve`

**Requires step-up verification:**
```json
{
  "staffId": "GS001",
  "authorizationPassword": "********"
}
```

**Side effects:**
- Status → `Processing`
- Create activity log
- Notify hospital (in-app + email/SMS)
- Optionally reserve inventory units

---

### 6.9 Deliveries

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/deliveries` | admin, staff, hospital (own) | List deliveries |
| GET | `/deliveries/:id` | admin, staff, hospital (own) | Delivery detail |
| POST | `/deliveries` | staff | Schedule delivery |
| PUT | `/deliveries/:id` | staff | Update status/details |
| POST | `/deliveries/:id/confirm` | hospital | Confirm receipt |

---

### 6.10 Hospitals

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/hospitals` | admin | List all hospitals |
| GET | `/hospitals/:id` | admin, hospital (own) | Hospital detail |
| POST | `/hospitals` | admin | Register hospital |
| PUT | `/hospitals/:id` | admin | Update hospital |
| DELETE | `/hospitals/:id` | admin | Deactivate hospital |
| GET | `/hospitals/:id/stats` | admin, hospital (own) | Request statistics |
| GET | `/hospitals/nearby` | hospital, donor | Geo-search blood banks/hospitals |

#### GET `/hospitals/nearby`

**Query params:** `latitude`, `longitude`, `radiusKm`, `type` (`blood_bank` | `hospital`)

**Response:** Sorted by distance with `distance` field.

---

### 6.11 Patients

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/patients` | admin, staff, hospital (own) | List patients |
| GET | `/patients/:id` | admin, staff, hospital (own) | Patient detail |
| POST | `/patients` | hospital, admin | Register patient |
| PUT | `/patients/:id` | hospital (own), admin | Update patient |

---

### 6.12 Staff Management

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/staff` | admin | List staff |
| GET | `/staff/:id` | admin, staff (own) | Staff detail |
| POST | `/staff` | admin | Create staff account |
| PUT | `/staff/:id` | admin, staff (own profile) | Update staff |
| DELETE | `/staff/:id` | admin | Deactivate staff |
| GET | `/staff/:id/permissions` | admin, staff (own) | Get permission set |

---

### 6.13 Payments & Finances

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/compensations` | admin, staff | List donor compensations |
| GET | `/compensations/:id` | admin, staff | Compensation detail |
| POST | `/compensations/:id/pay` | staff | Process payment |
| GET | `/finances/transactions` | admin | List transactions |
| GET | `/finances/summary` | admin | Revenue/expense summary |
| GET | `/finances/reports/monthly` | admin | Monthly financial report |
| POST | `/finances/transactions` | admin | Manual transaction entry |

#### POST `/compensations/:id/pay`

**Request:**
```json
{
  "paymentMethod": "Mobile Money",
  "notes": "Paid via MTN MoMo"
}
```

---

### 6.14 Notifications

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/notifications` | all authenticated | List user notifications |
| PUT | `/notifications/:id/read` | all authenticated | Mark as read |
| PUT | `/notifications/read-all` | all authenticated | Mark all as read |
| GET | `/notifications/unread-count` | all authenticated | Unread count |

---

### 6.15 Activity Logs

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/activity-logs` | admin | List audit logs (paginated) |
| GET | `/activity-logs/:id` | admin | Log detail |
| GET | `/activity-logs/export` | admin | Export CSV/PDF |

**Query params:** `category`, `actionType`, `staffId`, `dateFrom`, `dateTo`, `search`

---

### 6.16 Reports & Analytics

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/reports/dashboard` | admin | Admin dashboard KPIs |
| GET | `/reports/collections/monthly` | admin, staff (with permission) | Monthly collection chart |
| GET | `/reports/inventory/distribution` | admin, staff | Blood group distribution |
| GET | `/reports/requests/by-urgency` | admin, staff | Requests by urgency |
| GET | `/reports/demand-prediction` | admin | AI/ML demand forecast |
| GET | `/reports/live-monitoring` | admin | Real-time system metrics |

---

### 6.17 Blood Banks (Facilities)

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/blood-banks` | all authenticated | List blood banks |
| GET | `/blood-banks/:id` | all authenticated | Blood bank detail |
| POST | `/blood-banks` | admin | Create blood bank |
| PUT | `/blood-banks/:id` | admin | Update blood bank |

---

### 6.18 Rewards (Donor)

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/rewards/:donorId` | donor (own), admin, staff | Donor rewards/badges |
| GET | `/rewards/tiers` | donor | Reward tier definitions |

---

### 6.19 System Settings

| Method | Path | Role | Description |
|--------|------|------|-------------|
| GET | `/settings/system` | admin | Get system settings |
| PUT | `/settings/system` | admin | Update system settings |
| GET | `/settings/notifications` | admin | Get notification settings |
| PUT | `/settings/notifications` | admin | Update notification settings |
| GET | `/settings/security` | admin | Get security settings |
| PUT | `/settings/security` | admin | Update security settings |

---

### 6.20 Reference Data

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/reference/blood-types` | Public | List blood component types |
| GET | `/reference/blood-groups` | Public | List blood groups |
| GET | `/reference/regions` | Public | List regions (Ghana) |
| GET | `/reference/urgency-levels` | Public | Urgency level definitions |

---

## 7. Business Rules & Workflows

### 7.1 Donor Eligibility

| Rule | Value | Source |
|------|-------|--------|
| Minimum age | 18 years | DonorRegistration, ScreeningSession |
| Maximum age | 65 years | DonorRegistration, ScreeningSession |
| Minimum weight | 50 kg | ScreeningSession |
| Height range | 140–220 cm | ScreeningSession |
| Minimum donation interval | 90 days (configurable) | SystemSettings |
| ID must be valid | Expiry date > today | ScreeningSession |

**Eligibility computation:**
```
eligible = age in [18,65]
         AND weight >= 50
         AND daysSince(lastDonation) >= minDonationInterval
         AND overallTestStatus == 'Passed'
         AND no disqualifying medical conditions
```

### 7.2 Blood Request Workflow

```
┌──────────┐    submit     ┌─────────┐
│ Hospital │──────────────▶│ Pending │
└──────────┘               └────┬────┘
                                │
                    ┌───────────┼───────────┐
                    ▼                       ▼
              ┌──────────┐           ┌──────────┐
              │ Approved │           │ Rejected │
              └────┬─────┘           └──────────┘
                   │ (auto)
                   ▼
              ┌────────────┐
              │ Processing │ ← inventory reserved
              └─────┬──────┘
                    │ delivery confirmed
                    ▼
              ┌───────────┐
              │ Completed │
              └───────────┘
```

**Approve/reject:** Requires `canApproveRequests` / `canRejectRequests` + step-up auth.

**Notifications:**
- Hospital notified on approve, reject, processing update, delivery
- Admin notified on critical urgency requests

### 7.3 Collection → Inventory Pipeline

```
Donor arrives → Screening (specialist) → Collection (staff)
      → Testing (lab) → [Passed] → BloodUnit created → Inventory
                      → [Failed] → Collection rejected, donor notified
```

**On test pass:**
1. Create `BloodUnit` with `expiryDate = collectionDate + shelfLifeDays`
2. Set collection status → `Stored`
3. Create compensation record
4. Update aggregated inventory

### 7.4 Appointment Workflow

```
Donor schedules → [Optional: AppointmentRequest for staff review]
      → Appointment confirmed (code generated)
      → Specialist screening session
      → Mark complete → Staff collection portal
```

### 7.5 Compensation Workflow

```
Collection completed → Compensation (Pending)
      → Staff processes payment → Paid
      → Financial transaction recorded (Expense)
```

Default amount: **GH₵ 75** (configurable via `donorCompensation` setting).

### 7.6 Inventory Expiry Management

| Component | Shelf Life |
|-----------|------------|
| Whole Blood | 42 days |
| Red Blood Cells | 42 days |
| Platelets | 5–7 days |
| Plasma (frozen) | 365 days |
| Cryoprecipitate | 365 days |

**Automated actions (background jobs):**
- Daily: flag units expiring within 7 days → critical notification
- Daily: flag units expiring within 30 days → warning notification
- Daily: auto-discard expired units, update inventory counts

---

## 8. Authentication & Security

### 8.1 Token Strategy

| Token | Lifetime | Storage |
|-------|----------|---------|
| Access token (JWT) | 15–60 min | Memory / Authorization header |
| Refresh token | 7–30 days | HttpOnly secure cookie |

**JWT payload:**
```json
{
  "sub": "staff1",
  "role": "staff",
  "staffRole": "Senior Staff",
  "permissions": ["canApproveRequests", "..."],
  "iat": 1234567890,
  "exp": 1234571490
}
```

### 8.2 Password Requirements

| Context | Rule |
|---------|------|
| Donor registration | Min 6 characters |
| Staff/admin | Min 8 characters, complexity rules (production) |
| Authorization password | Separate credential for sensitive actions |

### 8.3 Security Settings (from Admin Settings)

| Setting | Default |
|---------|---------|
| Session timeout | 30 minutes |
| Password expiry | 90 days |
| Two-factor auth | Disabled (optional enable) |
| Max login attempts | 5 (then lockout) |

### 8.4 Route Protection

Frontend currently has `ProtectedRoute` component **not wired**. Backend must enforce all authorization regardless of frontend state.

**Middleware chain:**
1. `authenticate` — validate JWT
2. `authorize(roles[])` — check portal role
3. `requirePermission(permission)` — check staff permission
4. `auditLog` — record mutating actions

### 8.5 Delete Protection

Admin delete operations (donors, staff, hospitals) require **password confirmation** (mirrors `DeleteWithPasswordModal`).

---

## 9. Notifications

### 9.1 Trigger Events

| Event | Recipients | Channels |
|-------|------------|----------|
| Critical stock alert | admin, staff | in-app, email |
| Blood request submitted | staff | in-app |
| Request approved/rejected | hospital | in-app, email, SMS |
| Request processing/completed | hospital | in-app, email |
| Donor registration | donor | email |
| Appointment confirmed/cancelled | donor | email, SMS |
| Screening completed | donor, staff | in-app |
| Unit expiring (7 days) | staff | in-app, email |
| Unit expired | admin, staff | in-app |
| Donor eligibility restored | donor | in-app, email |
| Payment processed | donor | in-app, SMS |
| Low stock by blood group | admin | in-app, email |

### 9.2 Notification Preferences

Controlled via `NotificationSettings` — users can opt out of email/SMS per category (admin configures system defaults).

---

## 10. Background Jobs & Scheduled Tasks

| Job | Schedule | Description |
|-----|----------|-------------|
| `checkInventoryExpiry` | Daily 00:00 | Flag/discard expired units, send alerts |
| `checkLowStock` | Every 6 hours | Compare inventory vs thresholds |
| `updateDonorEligibility` | Daily 06:00 | Recalculate eligible status based on `nextEligible` |
| `generateDailyReport` | Daily 23:00 | Admin summary email |
| `cleanupExpiredSessions` | Hourly | Remove stale refresh tokens |
| `processPendingPayments` | Daily 09:00 | Batch process pending compensations |
| `demandPrediction` | Weekly | Update ML forecast model |
| `archiveActivityLogs` | Monthly | Archive logs older than retention period |

### 10.1 Current localStorage Sync (to replace)

| Key | Current Use | Backend Replacement |
|-----|-------------|---------------------|
| `bloodbank_user` | Auth session | JWT + `/auth/me` |
| `activityLogs` | Cross-portal audit | `POST` auto on mutations + `GET /activity-logs` |
| `completedDonors` | Specialist → staff sync | `PUT /screening/schedules/:id/complete` |

---

## 11. External Integrations

| Service | Purpose | Priority |
|---------|---------|----------|
| **Email** (SendGrid/SES) | Registration confirmation, request status, alerts | High |
| **SMS** (Twilio/Africa's Talking) | Critical request alerts, appointment reminders | Medium |
| **Maps/Geocoding** (Google Maps) | Blood bank geo-search, distance calculation | Medium |
| **Mobile Money** (MTN MoMo, etc.) | Donor compensation payouts | Medium |
| **PDF Generation** | Receipts, shipping labels, reports | Medium |
| **Object Storage** (S3/Azure Blob) | ID document uploads | Low |
| **ML Service** | Demand prediction analytics | Low |

### 11.1 Geo-Search API

Used by hospital `GeoLocationSearch` and donor `FindBloodBanks`:

```
GET /blood-banks/nearby?lat=40.7128&lng=-74.0060&radius=50
```

Returns blood banks sorted by Haversine distance with operating status.

---

## 12. Error Handling

### 12.1 HTTP Status Codes

| Code | Usage |
|------|-------|
| 200 | Success |
| 201 | Created |
| 400 | Validation error |
| 401 | Unauthenticated |
| 403 | Forbidden (role/permission) |
| 404 | Resource not found |
| 409 | Conflict (duplicate email, insufficient inventory) |
| 422 | Business rule violation (donor ineligible) |
| 429 | Rate limited |
| 500 | Internal server error |

### 12.2 Error Codes

| Code | Description |
|------|-------------|
| `VALIDATION_ERROR` | Request body failed validation |
| `AUTH_INVALID_CREDENTIALS` | Wrong email/password |
| `AUTH_TOKEN_EXPIRED` | JWT expired |
| `AUTH_INSUFFICIENT_PERMISSION` | Missing role or permission |
| `AUTH_STEP_UP_REQUIRED` | Sensitive action needs re-verification |
| `AUTH_STEP_UP_FAILED` | Wrong staff ID or authorization password |
| `DONOR_INELIGIBLE` | Donor fails eligibility check |
| `INVENTORY_INSUFFICIENT` | Not enough units to fulfill request |
| `INVENTORY_UNIT_EXPIRED` | Cannot issue expired unit |
| `REQUEST_INVALID_TRANSITION` | Invalid status change |
| `DUPLICATE_EMAIL` | Email already registered |
| `RESOURCE_NOT_FOUND` | Entity not found |

### 12.3 Audit on Failure

Failed login attempts and failed step-up verifications should be logged to activity logs with `actionType: 'auth'`.

---

## 13. Environment Configuration

```env
# Server
NODE_ENV=development
PORT=3001
API_BASE_URL=http://localhost:3001/api/v1
CORS_ORIGIN=http://localhost:5173

# Database
DATABASE_URL=postgresql://user:pass@localhost:5432/bloodbank
DATABASE_POOL_SIZE=10

# Redis
REDIS_URL=redis://localhost:6379

# JWT
JWT_SECRET=<random-256-bit-secret>
JWT_ACCESS_EXPIRY=3600
JWT_REFRESH_EXPIRY=604800

# Email
SMTP_HOST=smtp.sendgrid.net
SMTP_PORT=587
SMTP_USER=apikey
SMTP_PASS=<sendgrid-api-key>
EMAIL_FROM=noreply@bloodbank.pro

# SMS
SMS_PROVIDER=twilio
TWILIO_ACCOUNT_SID=
TWILIO_AUTH_TOKEN=
TWILIO_FROM_NUMBER=

# Storage
S3_BUCKET=bloodbank-documents
S3_REGION=us-east-1
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=

# Maps
GOOGLE_MAPS_API_KEY=

# System defaults
DEFAULT_DONOR_COMPENSATION=75
MIN_DONATION_INTERVAL_DAYS=90
MIN_DONOR_AGE=18
MAX_DONOR_AGE=65
MIN_DONOR_WEIGHT_KG=50

# Security
MAX_LOGIN_ATTEMPTS=5
LOCKOUT_DURATION_MINUTES=30
BCRYPT_ROUNDS=12
```

---

## 14. Appendix

### Appendix A: Blood Component Shelf Life

| Code | Component | Shelf Life | Storage |
|------|-----------|------------|---------|
| WB | Whole Blood | 42 days | 1–6°C |
| RBC | Red Blood Cells | 42 days | 1–6°C |
| PLT | Platelets | 5–7 days | 20–24°C agitated |
| PLS | Plasma | 365 days | ≤ −18°C frozen |
| CRYO | Cryoprecipitate | 365 days | ≤ −18°C frozen |

### Appendix B: Status Enumerations

**BloodRequest.status:** `Pending`, `Approved`, `Processing`, `Completed`, `Rejected`

**BloodUnit.status:** `Available`, `Reserved`, `Issued`, `Expired`, `Discarded`

**Collection.status:** `Collected`, `Testing`, `Tested`, `Stored`, `Rejected`

**Donor.status:** `Eligible`, `Not Eligible`, `Pending Screening`

**Compensation.status:** `Pending`, `Processing`, `Paid`, `Failed`

**Appointment.status:** `Scheduled`, `Confirmed`, `Completed`, `Cancelled`, `No-Show`

**Delivery.status:** `Scheduled`, `In Transit`, `Delivered`, `Cancelled`

**ActivityLog.actionType:** `approve`, `reject`, `create`, `update`, `delete`, `collection`, `testing`, `auth`

### Appendix C: Frontend Route → API Mapping

| Frontend Route | Primary API Dependencies |
|----------------|------------------------|
| `/admin` | `/reports/dashboard`, `/notifications` |
| `/admin/donors` | `/donors` CRUD |
| `/admin/inventory` | `/inventory/summary`, `/inventory/units` |
| `/admin/requests` | `/requests` |
| `/admin/activity-logs` | `/activity-logs` |
| `/admin/finances` | `/finances/*` |
| `/staff/requests` | `/requests`, `/requests/:id/approve\|reject` |
| `/staff/payments` | `/compensations` |
| `/specialist/session` | `/screening/sessions` |
| `/specialist/schedules` | `/screening/schedules` |
| `/donor/schedule` | `/appointments`, `/blood-banks` |
| `/hospital/request` | `/requests` POST |
| `/hospital/tracking` | `/requests`, `/deliveries` |
| `/hospital/geo-search` | `/blood-banks/nearby` |
| `/donor-registration` | `/auth/register/donor` |

### Appendix D: Source File Reference

| Concern | Frontend Source |
|---------|-----------------|
| All domain data/schemas | `src/app/utils/mockData.ts` |
| Portal authentication | `src/app/contexts/AuthContext.tsx` |
| Staff RBAC | `src/app/context/StaffAuthContext.tsx` |
| Step-up verification | `src/app/components/staff/StaffVerificationModal.tsx` |
| Request approve/reject | `src/app/pages/staff/Requests.tsx` |
| Screening form schema | `src/app/pages/specialist/ScreeningSession.tsx` |
| Donor registration validation | `src/app/pages/DonorRegistration.tsx` |
| Hospital blood request | `src/app/pages/hospital/BloodRequest.tsx` |
| System settings defaults | `src/app/pages/admin/Settings.tsx` |
| Route definitions | `src/app/App.tsx` |

### Appendix E: Implementation Phases (Suggested)

| Phase | Scope | Endpoints |
|-------|-------|-----------|
| **Phase 1 — Core** | Auth, donors, appointments | `/auth/*`, `/donors/*`, `/appointments/*` |
| **Phase 2 — Operations** | Collections, testing, inventory | `/collections/*`, `/testing/*`, `/inventory/*` |
| **Phase 3 — Fulfillment** | Requests, deliveries, hospitals | `/requests/*`, `/deliveries/*`, `/hospitals/*` |
| **Phase 4 — Admin** | Staff, finances, reports, settings | `/staff/*`, `/finances/*`, `/reports/*`, `/settings/*` |
| **Phase 5 — Enhancements** | Notifications, geo-search, ML, payments | External integrations, background jobs |

---

*This specification was generated from a full audit of the BloodBank Pro frontend codebase. It defines the backend required to replace mock data and client-side persistence with a production-ready system.*
