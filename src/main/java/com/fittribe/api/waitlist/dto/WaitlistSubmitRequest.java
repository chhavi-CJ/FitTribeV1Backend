package com.fittribe.api.waitlist.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class WaitlistSubmitRequest {

    @NotBlank @Email
    private String email;

    @NotBlank
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Must be a 10-digit Indian mobile number")
    private String phone;

    private String referredByCode;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getReferredByCode() { return referredByCode; }
    public void setReferredByCode(String referredByCode) { this.referredByCode = referredByCode; }
}
