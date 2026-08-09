package com.bloodbank.bloodbank.dto.request;

import com.bloodbank.bloodbank.entity.Staff;
import com.bloodbank.bloodbank.entity.StaffRole;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.StaffDepartment;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.StaffShift;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class StaffCreateRequest {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8)
    private String password;

    @NotBlank
    private String name;

    private String phone;
    private Long staffRoleId;
    private StaffDepartment department;
    private StaffShift shift;
    private List<String> certifications;
    private String portalRole;
    /** e.g. Junior Staff, Senior Staff, Technician */
    private String staffRoleName;

    public Staff toStaff() {
        Staff staff = Staff.builder()
                .name(name)
                .phone(phone)
                .department(department)
                .shift(shift)
                .certifications(certifications)
                .build();
        if (staffRoleId != null) {
            staff.setStaffRole(StaffRole.builder().id(staffRoleId).build());
        }
        return staff;
    }
}
