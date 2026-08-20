package com.bloodbank.bloodbank.entity.enums;

public final class DomainEnums {

    private DomainEnums() {}

    public enum Urgency { Critical, High, Medium, Low }

    public enum RequestStatus { Pending, Approved, Processing, Completed, Rejected, Cancelled }

    public enum UnitStatus { Quarantine, Available, Reserved, Issued, Expired, Discarded }

    public enum DonorStatus { Eligible, Not_Eligible, Deferred, Pending_Screening }

    public enum CollectionStatus { In_Progress, Collected, Testing, Tested, Stored, Failed }

    public enum TestOverallStatus { Pending, Completed, Passed, Failed }

    public enum Gender { Male, Female, Other }

    public enum IdType { Ghana_Card, Passport, Driver_License, National_ID }

    public enum HospitalCapacity { Small, Medium, Large }

    public enum HospitalStatus { Active, Suspended, Inactive }

    public enum StaffDepartment { Testing, Collection, Storage, Administration }

    public enum StaffShift { Day, Night }

    public enum StaffStatus { Active, Inactive }

    public enum BloodBankStatus { Open, Closed }

    public enum ScreeningStatus { In_Progress, Completed, Failed, Deferred }

    public enum EligibilityResult { Eligible, Deferred, Permanent_Deferral }

    public enum DeliveryStatus { Scheduled, In_Transit, Delivered, Failed }

    public enum PatientStatus { Critical, Stable, Improving }

    public enum AppointmentStatus { Pending, Scheduled, Confirmed, Checked_In, In_Screening, Completed, Cancelled, No_Show }

    public enum AppointmentRequestStatus { Pending, Approved, Rejected }

    public enum RewardLevel { Bronze, Silver, Gold, Platinum, Diamond }

    public enum CompensationMethod { Cash, Mobile_Money, Bank_Transfer }

    public enum PaymentStatus { Pending, Paid }

    public enum WithdrawalStatus { Pending, Completed, Failed }

    public enum TransactionType { Revenue, Expense }

    public enum TransactionStatus { Pending, Completed, Failed }

    public enum NotificationType { critical, urgent, warning, info, success }

    public enum ActionType { approve, reject, create, update, collection, testing, auth, delete }

    public enum CollectionSessionStatus { Active, Completed, Cancelled }

    public enum TestItemStatus { Complete, In_Progress }

    public enum SupplyRequestStatus { Submitted, Acknowledged, In_Transit, Delivered, Cancelled, Rejected }

    public enum EntityType { DONOR, HOSPITAL, REQUEST, COLLECTION, BLOOD_UNIT, APPOINTMENT, TRANSACTION, SUPPLY_REQUEST, SERVICE_CHARGE }
}
