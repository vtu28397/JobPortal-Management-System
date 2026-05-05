package com.arjun.jobportal.repository;

import com.arjun.jobportal.model.AppUser;
import com.arjun.jobportal.model.Job;
import com.arjun.jobportal.model.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<JobApplication, Long> {
    List<JobApplication> findByStudentOrderByAppliedAtDesc(AppUser student);
    List<JobApplication> findByJobEmployerOrderByAppliedAtDesc(AppUser employer);
    Optional<JobApplication> findByStudentAndJob(AppUser student, Job job);
    long countByJobEmployer(AppUser employer);
}
