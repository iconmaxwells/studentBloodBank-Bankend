package com.bloodbank.bloodbank.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum BloodProductType {
    WB("WB"),
    RBC("RBC"),
    PLS("PLS"),
    PLT("PLT"),
    CRYO("CRYO");

    private final String value;

    BloodProductType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static BloodProductType fromValue(String value) {
        for (BloodProductType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown blood product type: " + value);
    }
}
