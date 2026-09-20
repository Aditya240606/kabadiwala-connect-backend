package com.kabadiwala.backend.recycler;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "recyclers")
@EntityListeners(AuditingEntityListener.class)
public class Recycler {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "facility_name", nullable = false, length = 150)
    private String facilityName;

    @Column(name = "location_address", columnDefinition = "TEXT")
    private String locationAddress;

    @Column(length = 100)
    private String city;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";

    @Column(name = "accepted_category_codes", nullable = false)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.ARRAY)
    private List<String> acceptedCategoryCodes = new java.util.ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Recycler() {}

    public Recycler(String facilityName, String city, String contactPhone, List<String> acceptedCategories) {
        this.facilityName = facilityName;
        this.city = city;
        this.contactPhone = contactPhone;
        this.acceptedCategoryCodes = acceptedCategories != null ? new java.util.ArrayList<>(acceptedCategories) : new java.util.ArrayList<>();
        this.status = "ACTIVE";
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getFacilityName() {
        return facilityName;
    }

    public void setFacilityName(String facilityName) {
        this.facilityName = facilityName;
    }

    public String getLocationAddress() {
        return locationAddress;
    }

    public void setLocationAddress(String locationAddress) {
        this.locationAddress = locationAddress;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getAcceptedCategoryList() {
        return acceptedCategoryCodes == null ? List.of() : acceptedCategoryCodes;
    }

    public void setAcceptedCategoryList(List<String> categories) {
        this.acceptedCategoryCodes = categories == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(categories);
    }

    public List<String> getAcceptedCategoryCodes() {
        return acceptedCategoryCodes;
    }

    public void setAcceptedCategoryCodes(List<String> acceptedCategoryCodes) {
        this.acceptedCategoryCodes = acceptedCategoryCodes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
