package com.arjun.jobportal.config;

import com.arjun.jobportal.model.*;
import com.arjun.jobportal.repository.ApplicationRepository;
import com.arjun.jobportal.repository.JobRepository;
import com.arjun.jobportal.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {
    @Bean
    CommandLineRunner seed(UserRepository users, JobRepository jobs, ApplicationRepository applications, PasswordEncoder encoder) {
        return args -> {
            if (users.count() > 0) {
                jobs.findAll().stream()
                        .filter(job -> job.getApprovalStatus() == null)
                        .forEach(job -> {
                            job.setApprovalStatus(JobApprovalStatus.APPROVED);
                            jobs.save(job);
                        });
                users.findByEmail("admin@jobportal.com")
                        .or(() -> users.findByEmail("arjunP"))
                        .or(() -> users.findByEmailOrUsername("arjunP", "arjunP"))
                        .ifPresent(admin -> {
                            admin.setEmail("admin@jobportal.com");
                            admin.setUsername("arjunP");
                            admin.setPhone("9876543210");
                            admin.setPassword(encoder.encode("28397"));
                            admin.setRole(Role.ADMIN);
                            users.save(admin);
                        });
                users.findByEmail("student@jobportal.com").ifPresent(student -> {
                    student.setPhone("9123456780");
                    users.save(student);
                });
                users.findByEmail("employer@jobportal.com").ifPresent(employer -> {
                    employer.setPhone("9988776655");
                    users.save(employer);
                });
                return;
            }

            AppUser employer = new AppUser();
            employer.setFullName("Priya Sharma");
            employer.setEmail("employer@jobportal.com");
            employer.setPassword(encoder.encode("password"));
            employer.setRole(Role.EMPLOYER);
            employer.setPhone("9988776655");
            employer.setCompanyName("BluePeak Technologies");
            employer.setLocation("Bengaluru");
            users.save(employer);

            AppUser student = new AppUser();
            student.setFullName("Arjun Kumar");
            student.setEmail("student@jobportal.com");
            student.setPassword(encoder.encode("password"));
            student.setRole(Role.STUDENT);
            student.setPhone("9123456780");
            student.setSkills("Java, Spring Boot, MySQL, HTML, CSS");
            student.setExperience("0-1 years");
            student.setLocation("Hyderabad");
            users.save(student);

            AppUser admin = new AppUser();
            admin.setFullName("Admin User");
            admin.setEmail("admin@jobportal.com");
            admin.setUsername("arjunP");
            admin.setPhone("9876543210");
            admin.setPassword(encoder.encode("28397"));
            admin.setRole(Role.ADMIN);
            users.save(admin);

            createJob(jobs, employer, "Java Spring Boot Developer", "Build secure REST APIs, Thymeleaf views, and database workflows for client products.", "Java, Spring Boot, JPA, MySQL", "IT Services", "Bengaluru", "0-2 years", "4-7 LPA");
            createJob(jobs, employer, "Frontend Developer Intern", "Create responsive dashboards, dynamic tables, forms, and polished user experiences.", "HTML, CSS, JavaScript, Thymeleaf", "Software Development", "Remote", "Fresher", "15k/month");
            createJob(jobs, employer, "Full Stack Trainee", "Work across Spring MVC controllers, database models, job workflows, and UI polish.", "Java, Spring MVC, H2, JavaScript", "Product Engineering", "Hyderabad", "0-1 years", "3-5 LPA");
            createJob(jobs, employer, "Backend Engineer", "Design application modules, validation, exception handling, and role-based access flows.", "Spring Security, REST, JPA", "Backend", "Pune", "1-3 years", "6-10 LPA");

            JobApplication application = new JobApplication();
            application.setStudent(student);
            application.setJob(jobs.findByActiveTrueOrderByPostedAtDesc().get(0));
            application.setCoverNote("I have built academic projects with Spring Boot and can join immediately.");
            applications.save(application);
        };
    }

    private void createJob(JobRepository jobs, AppUser employer, String title, String desc, String skills, String category, String location, String exp, String salary) {
        Job job = new Job();
        job.setEmployer(employer);
        job.setTitle(title);
        job.setDescription(desc);
        job.setSkillsRequired(skills);
        job.setCategory(category);
        job.setLocation(location);
        job.setExperience(exp);
        job.setSalary(salary);
        job.setApprovalStatus(JobApprovalStatus.APPROVED);
        job.setActive(true);
        jobs.save(job);
    }
}
