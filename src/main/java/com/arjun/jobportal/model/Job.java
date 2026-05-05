package com.arjun.jobportal.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(min = 3, max = 120)
    private String title;

    @NotBlank
    @Size(min = 20, max = 2000)
    @Column(length = 2000)
    private String description;

    @NotBlank
    private String skillsRequired;

    @NotBlank
    private String category;

    @NotBlank
    private String location;

    private String experience;
    private String salary;
    private boolean active = true;
    @Enumerated(EnumType.STRING)
    private JobApprovalStatus approvalStatus = JobApprovalStatus.PENDING;
    private LocalDateTime postedAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser employer;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobApplication> applications = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSkillsRequired() { return skillsRequired; }
    public void setSkillsRequired(String skillsRequired) { this.skillsRequired = skillsRequired; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getExperience() { return experience; }
    public void setExperience(String experience) { this.experience = experience; }
    public String getSalary() { return salary; }
    public void setSalary(String salary) { this.salary = salary; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public JobApprovalStatus getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(JobApprovalStatus approvalStatus) { this.approvalStatus = approvalStatus; }
    public LocalDateTime getPostedAt() { return postedAt; }
    public void setPostedAt(LocalDateTime postedAt) { this.postedAt = postedAt; }
    public AppUser getEmployer() { return employer; }
    public void setEmployer(AppUser employer) { this.employer = employer; }
    public List<JobApplication> getApplications() { return applications; }
}
