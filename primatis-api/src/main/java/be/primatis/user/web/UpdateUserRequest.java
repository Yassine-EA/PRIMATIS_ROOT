package be.primatis.user.web;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;
import java.util.Set;
/**
 * Requête de modification d'un utilisateur.
 */
public class UpdateUserRequest {

    private String firstName;
    private boolean firstNamePresent;

    private String lastName;
    private boolean lastNamePresent;

    private String phoneNumber;
    private boolean phoneNumberPresent;

    private Set<String> roles;

    private LocalDate registrationDate;
    private boolean registrationDatePresent;

    private LocalDate memberExpirationDate;
    private boolean memberExpirationDatePresent;

    private String blockedReason;
    private boolean blockedReasonPresent;

    public UpdateUserRequest() {
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
        this.firstNamePresent = true;
    }

    @JsonIgnore
    public boolean isFirstNamePresent() {
        return firstNamePresent;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
        this.lastNamePresent = true;
    }

    @JsonIgnore
    public boolean isLastNamePresent() {
        return lastNamePresent;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        this.phoneNumberPresent = true;
    }

    @JsonIgnore
    public boolean isPhoneNumberPresent() {
        return phoneNumberPresent;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public LocalDate getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(LocalDate registrationDate) {
        this.registrationDate = registrationDate;
        this.registrationDatePresent = true;
    }

    @JsonIgnore
    public boolean isRegistrationDatePresent() {
        return registrationDatePresent;
    }

    public LocalDate getMemberExpirationDate() {
        return memberExpirationDate;
    }

    public void setMemberExpirationDate(LocalDate memberExpirationDate) {
        this.memberExpirationDate = memberExpirationDate;
        this.memberExpirationDatePresent = true;
    }

    @JsonIgnore
    public boolean isMemberExpirationDatePresent() {
        return memberExpirationDatePresent;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public void setBlockedReason(String blockedReason) {
        this.blockedReason = blockedReason;
        this.blockedReasonPresent = true;
    }

    @JsonIgnore
    public boolean isBlockedReasonPresent() {
        return blockedReasonPresent;
    }
}
