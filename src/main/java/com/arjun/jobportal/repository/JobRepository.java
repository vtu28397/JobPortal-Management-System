package com.arjun.jobportal.repository;

import com.arjun.jobportal.model.AppUser;
import com.arjun.jobportal.model.Job;
import com.arjun.jobportal.model.JobApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {
    List<Job> findByActiveTrueOrderByPostedAtDesc();
    List<Job> findByActiveTrueAndApprovalStatusOrderByPostedAtDesc(JobApprovalStatus approvalStatus);
    List<Job> findByEmployerOrderByPostedAtDesc(AppUser employer);
    List<Job> findByApprovalStatusOrderByPostedAtDesc(JobApprovalStatus approvalStatus);
    List<Job> findByCategoryContainingIgnoreCaseAndLocationContainingIgnoreCaseAndExperienceContainingIgnoreCaseAndActiveTrueOrderByPostedAtDesc(
            String category, String location, String experience);

    @Query("""
            select j from Job j
            where j.active = true
              and j.approvalStatus = com.arjun.jobportal.model.JobApprovalStatus.APPROVED
              and lower(j.category) like lower(concat('%', :category, '%'))
              and lower(j.location) like lower(concat('%', :location, '%'))
              and lower(coalesce(j.experience, '')) like lower(concat('%', :experience, '%'))
              and lower(coalesce(j.employer.companyName, j.employer.fullName, '')) like lower(concat('%', :company, '%'))
            order by j.postedAt desc
            """)
    List<Job> searchApprovedJobs(@Param("category") String category,
                                 @Param("location") String location,
                                 @Param("experience") String experience,
                                 @Param("company") String company);
}
