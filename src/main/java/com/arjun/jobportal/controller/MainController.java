package com.arjun.jobportal.controller;

import com.arjun.jobportal.exception.ResourceNotFoundException;
import com.arjun.jobportal.model.*;
import com.arjun.jobportal.repository.ApplicationRepository;
import com.arjun.jobportal.repository.JobRepository;
import com.arjun.jobportal.repository.UserRepository;
import com.arjun.jobportal.service.Msg91OtpService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Controller
public class MainController {
    private final UserRepository users;
    private final JobRepository jobs;
    private final ApplicationRepository applications;
    private final PasswordEncoder encoder;
    private final Msg91OtpService otpService;

    @Value("${app.resume.upload-dir}")
    private String uploadDir;

    @Value("${app.msg91.widget-id}")
    private String msg91WidgetId;

    @Value("${app.msg91.token-auth}")
    private String msg91TokenAuth;

    public MainController(UserRepository users,
                          JobRepository jobs,
                          ApplicationRepository applications,
                          PasswordEncoder encoder,
                          Msg91OtpService otpService) {
        this.users = users;
        this.jobs = jobs;
        this.applications = applications;
        this.encoder = encoder;
        this.otpService = otpService;
    }

    @ModelAttribute("loggedInUser")
    public AppUser loggedInUser(Authentication authentication) {
        if (!isRealUser(authentication)) {
            return null;
        }
        return users.findByEmailOrUsername(authentication.getName(), authentication.getName()).orElse(null);
    }

    @GetMapping("/")
    public String home(Model model) {
        List<Job> latestJobs = jobs.findByActiveTrueAndApprovalStatusOrderByPostedAtDesc(JobApprovalStatus.APPROVED);
        model.addAttribute("jobs", latestJobs.stream().limit(4).toList());
        model.addAttribute("jobCount", latestJobs.size());
        model.addAttribute("companyCount", users.findByRole(Role.EMPLOYER).size());
        model.addAttribute("studentCount", users.findByRole(Role.STUDENT).size());
        return "home";
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("msg91WidgetId", msg91WidgetId);
        model.addAttribute("msg91TokenAuth", msg91TokenAuth);
        return "login";
    }

    @PostMapping("/login/otp")
    public String loginWithOtp(@RequestParam String phone,
                               @RequestParam(required = false) String otpAccessToken,
                               @RequestParam(required = false) String otpVerifiedPhone,
                               HttpServletRequest request,
                               Model model) {
        String otpError = otpValidationError(phone, otpVerifiedPhone, otpAccessToken);
        if (otpError != null) {
            model.addAttribute("msg91WidgetId", msg91WidgetId);
            model.addAttribute("msg91TokenAuth", msg91TokenAuth);
            model.addAttribute("otpLoginError", otpError);
            return "login";
        }

        AppUser user = findUserByPhone(phone);
        if (user == null) {
            model.addAttribute("msg91WidgetId", msg91WidgetId);
            model.addAttribute("msg91TokenAuth", msg91TokenAuth);
            model.addAttribute("otpLoginError", "No account found with this mobile number.");
            return "login";
        }

        loginUser(user, request);
        return "redirect:/dashboard";
    }

    @GetMapping("/login/otp/check-phone")
    @ResponseBody
    public Map<String, Object> checkOtpLoginPhone(@RequestParam String phone) {
        boolean registered = findUserByPhone(phone) != null;
        return Map.of(
                "registered", registered,
                "message", registered ? "Mobile number found." : "No registered account uses this mobile number."
        );
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "access-denied";
    }

    @GetMapping("/register")
    public String register(Model model) {
        AppUser user = new AppUser();
        user.setRole(Role.STUDENT);
        user.setExperience("Fresher");
        model.addAttribute("user", user);
        model.addAttribute("roles", List.of(Role.STUDENT, Role.EMPLOYER));
        model.addAttribute("msg91WidgetId", msg91WidgetId);
        model.addAttribute("msg91TokenAuth", msg91TokenAuth);
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("user") AppUser user,
                           BindingResult result,
                           @RequestParam(required = false) String otpAccessToken,
                           @RequestParam(required = false) String otpVerifiedPhone,
                           Model model) {
        if (users.existsByEmail(user.getEmail())) {
            result.rejectValue("email", "duplicate", "Email is already registered");
        }
        if (user.getUsername() != null && !user.getUsername().isBlank() && users.existsByUsername(user.getUsername())) {
            result.rejectValue("username", "duplicate", "Username is already taken");
        }
        if (phoneAlreadyRegistered(user.getPhone())) {
            result.rejectValue("phone", "duplicate", "Mobile number is already registered");
        }
        if (user.getRole() == null || user.getRole() == Role.ADMIN) {
            user.setRole(Role.STUDENT);
        }
        String otpError = otpValidationError(user.getPhone(), otpVerifiedPhone, otpAccessToken);
        if (otpError != null) {
            result.rejectValue("phone", "otp", otpError);
        }
        if (result.hasErrors()) {
            model.addAttribute("roles", List.of(Role.STUDENT, Role.EMPLOYER));
            model.addAttribute("msg91WidgetId", msg91WidgetId);
            model.addAttribute("msg91TokenAuth", msg91TokenAuth);
            return "register";
        }
        if (user.getUsername() != null) {
            user.setUsername(user.getUsername().trim());
        }
        user.setPhone(onlyDigits(user.getPhone()));
        user.setPassword(encoder.encode(user.getPassword()));
        users.save(user);
        return "redirect:/register?registered";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication) {
        AppUser user = currentUser(authentication);
        if (user.getRole() == Role.EMPLOYER) {
            return "redirect:/employer/dashboard";
        }
        if (user.getRole() == Role.ADMIN) {
            return "redirect:/admin/dashboard";
        }
        return "redirect:/student/dashboard";
    }

    @GetMapping("/jobs")
    public String jobList(@RequestParam(defaultValue = "") String category,
                          @RequestParam(defaultValue = "") String location,
                          @RequestParam(defaultValue = "") String experience,
                          @RequestParam(defaultValue = "") String company,
                          Model model) {
        List<Job> filtered = jobs.searchApprovedJobs(category, location, experience, company);
        model.addAttribute("jobs", filtered);
        model.addAttribute("category", category);
        model.addAttribute("location", location);
        model.addAttribute("experience", experience);
        model.addAttribute("company", company);
        return "jobs";
    }

    @GetMapping("/jobs/{id}")
    public String jobDetail(@PathVariable Long id, Model model, Authentication authentication) {
        Job job = jobs.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        if (!job.isActive() || job.getApprovalStatus() != JobApprovalStatus.APPROVED) {
            if (!canPreviewJob(job, authentication)) {
                throw new ResourceNotFoundException("Job is not available yet");
            }
        }
        model.addAttribute("job", job);
        if (isRealUser(authentication)) {
            AppUser user = currentUser(authentication);
            model.addAttribute("currentUser", user);
            if (user.getRole() == Role.STUDENT) {
                model.addAttribute("alreadyApplied", applications.findByStudentAndJob(user, job).isPresent());
                model.addAttribute("hasResume", user.getResumePath() != null && !user.getResumePath().isBlank());
            }
        }
        return "job-detail";
    }

    @PostMapping("/applications/{jobId}")
    public String apply(@PathVariable Long jobId, @RequestParam String coverNote, Authentication authentication) {
        AppUser student = currentUser(authentication);
        requireRole(student, Role.STUDENT);
        Job job = jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        if (!job.isActive() || job.getApprovalStatus() != JobApprovalStatus.APPROVED) {
            throw new ResourceNotFoundException("This job is no longer active");
        }
        if (student.getResumePath() == null || student.getResumePath().isBlank()) {
            return "redirect:/student/dashboard?resumeRequired";
        }
        applications.findByStudentAndJob(student, job).orElseGet(() -> {
            JobApplication application = new JobApplication();
            application.setStudent(student);
            application.setJob(job);
            application.setCoverNote(coverNote);
            application.setResumePath(student.getResumePath());
            return applications.save(application);
        });
        return "redirect:/student/dashboard?applied";
    }

    @GetMapping("/student/dashboard")
    public String studentDashboard(Model model, Authentication authentication) {
        AppUser student = currentUser(authentication);
        requireRole(student, Role.STUDENT);
        model.addAttribute("user", student);
        model.addAttribute("applications", applications.findByStudentOrderByAppliedAtDesc(student));
        model.addAttribute("recommendedJobs", jobs.findByActiveTrueAndApprovalStatusOrderByPostedAtDesc(JobApprovalStatus.APPROVED).stream().limit(3).toList());
        return "student-dashboard";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam String fullName,
                                @RequestParam String phone,
                                @RequestParam String location,
                                @RequestParam String skills,
                                @RequestParam String experience,
                                @RequestParam(required = false) MultipartFile resume,
                                Authentication authentication) throws IOException {
        AppUser user = currentUser(authentication);
        requireRole(user, Role.STUDENT);
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setLocation(location);
        user.setSkills(skills);
        user.setExperience(experience);
        if (resume != null && !resume.isEmpty()) {
            Files.createDirectories(Path.of(uploadDir));
            String filename = UUID.randomUUID() + "-" + resume.getOriginalFilename();
            Path target = Path.of(uploadDir, filename);
            Files.copy(resume.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            user.setResumePath(target.toString());
        }
        users.save(user);
        return "redirect:/student/dashboard?profileUpdated";
    }

    @GetMapping("/employer/dashboard")
    public String employerDashboard(Model model, Authentication authentication) {
        AppUser employer = currentUser(authentication);
        requireRole(employer, Role.EMPLOYER);
        List<Job> postedJobs = jobs.findByEmployerOrderByPostedAtDesc(employer);
        model.addAttribute("user", employer);
        model.addAttribute("jobs", postedJobs);
        model.addAttribute("applicationCount", applications.countByJobEmployer(employer));
        return "employer-dashboard";
    }

    @GetMapping("/employer/jobs/new")
    public String newJob(Model model, Authentication authentication) {
        requireRole(currentUser(authentication), Role.EMPLOYER);
        model.addAttribute("job", new Job());
        return "job-form";
    }

    @GetMapping("/employer/jobs/{id}/edit")
    public String editJob(@PathVariable Long id, Model model, Authentication authentication) {
        AppUser employer = currentUser(authentication);
        requireRole(employer, Role.EMPLOYER);
        Job job = jobs.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        requireJobOwner(job, employer);
        model.addAttribute("job", job);
        return "job-form";
    }

    @PostMapping("/employer/jobs")
    public String saveJob(@Valid @ModelAttribute Job job, BindingResult result, Authentication authentication) {
        if (result.hasErrors()) {
            return "job-form";
        }
        AppUser employer = currentUser(authentication);
        requireRole(employer, Role.EMPLOYER);
        if (job.getId() != null) {
            Job existing = jobs.findById(job.getId()).orElseThrow(() -> new ResourceNotFoundException("Job not found"));
            requireJobOwner(existing, employer);
            existing.setTitle(job.getTitle());
            existing.setDescription(job.getDescription());
            existing.setSkillsRequired(job.getSkillsRequired());
            existing.setCategory(job.getCategory());
            existing.setLocation(job.getLocation());
            existing.setExperience(job.getExperience());
            existing.setSalary(job.getSalary());
            existing.setApprovalStatus(JobApprovalStatus.PENDING);
            jobs.save(existing);
        } else {
            job.setEmployer(employer);
            job.setApprovalStatus(JobApprovalStatus.PENDING);
            jobs.save(job);
        }
        return "redirect:/employer/dashboard?saved";
    }

    @PostMapping("/employer/jobs/{id}/delete")
    public String deleteJob(@PathVariable Long id, Authentication authentication) {
        AppUser employer = currentUser(authentication);
        requireRole(employer, Role.EMPLOYER);
        Job job = jobs.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        requireJobOwner(job, employer);
        job.setActive(false);
        jobs.save(job);
        return "redirect:/employer/dashboard?deleted";
    }

    @GetMapping("/employer/applicants")
    public String applicants(Model model, Authentication authentication) {
        AppUser employer = currentUser(authentication);
        requireRole(employer, Role.EMPLOYER);
        model.addAttribute("applications", applications.findByJobEmployerOrderByAppliedAtDesc(employer));
        return "applicants";
    }

    @PostMapping("/employer/applications/{id}/status")
    public String updateStatus(@PathVariable Long id, @RequestParam ApplicationStatus status, Authentication authentication) {
        AppUser employer = currentUser(authentication);
        requireRole(employer, Role.EMPLOYER);
        JobApplication application = applications.findById(id).orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        requireJobOwner(application.getJob(), employer);
        application.setStatus(status);
        applications.save(application);
        return "redirect:/employer/applicants?statusUpdated";
    }

    @GetMapping("/employer/applications/{id}/resume")
    public ResponseEntity<Resource> downloadApplicantResume(@PathVariable Long id, Authentication authentication) {
        AppUser employer = currentUser(authentication);
        requireRole(employer, Role.EMPLOYER);
        JobApplication application = applications.findById(id).orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        requireJobOwner(application.getJob(), employer);
        return resumeResponse(application.getResumePath());
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model, Authentication authentication) {
        requireRole(currentUser(authentication), Role.ADMIN);
        model.addAttribute("users", users.findAll());
        model.addAttribute("jobs", jobs.findAll());
        model.addAttribute("pendingJobs", jobs.findByApprovalStatusOrderByPostedAtDesc(JobApprovalStatus.PENDING));
        model.addAttribute("applications", applications.findAll());
        return "admin-dashboard";
    }

    @PostMapping("/admin/jobs/{id}/approval")
    public String updateJobApproval(@PathVariable Long id, @RequestParam JobApprovalStatus status, Authentication authentication) {
        requireRole(currentUser(authentication), Role.ADMIN);
        Job job = jobs.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        job.setApprovalStatus(status);
        job.setActive(status == JobApprovalStatus.APPROVED);
        jobs.save(job);
        return "redirect:/admin/dashboard?approvalUpdated";
    }

    @PostMapping("/admin/jobs/{id}/delete")
    public String adminDeleteJob(@PathVariable Long id, Authentication authentication) {
        requireRole(currentUser(authentication), Role.ADMIN);
        jobs.deleteById(id);
        return "redirect:/admin/dashboard?jobDeleted";
    }

    @PostMapping("/admin/applications/{id}/delete")
    public String adminDeleteApplication(@PathVariable Long id, Authentication authentication) {
        requireRole(currentUser(authentication), Role.ADMIN);
        applications.deleteById(id);
        return "redirect:/admin/dashboard?applicationDeleted";
    }

    private AppUser currentUser(Authentication authentication) {
        if (!isRealUser(authentication)) {
            throw new AccessDeniedException("Login required");
        }
        return users.findByEmailOrUsername(authentication.getName(), authentication.getName())
                .or(() -> users.findAll().stream()
                        .filter(user -> onlyDigits(user.getPhone()).equals(onlyDigits(authentication.getName())))
                        .findFirst())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private boolean isRealUser(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private void requireRole(AppUser user, Role role) {
        if (user.getRole() != role) {
            throw new AccessDeniedException("You do not have permission to access this page");
        }
    }

    private void requireJobOwner(Job job, AppUser employer) {
        if (job.getEmployer() == null || !job.getEmployer().getId().equals(employer.getId())) {
            throw new AccessDeniedException("You can manage only your own jobs");
        }
    }

    private boolean phoneMatches(String phone, String verifiedPhone) {
        String requested = onlyDigits(phone);
        String verified = onlyDigits(verifiedPhone);
        return !requested.isBlank() && (verified.equals(requested) || verified.equals("91" + requested));
    }

    private String otpValidationError(String phone, String verifiedPhone, String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return "Enter the OTP and click Verify before submitting.";
        }
        if (!phoneMatches(phone, verifiedPhone)) {
            return "Verified mobile number does not match the mobile number entered.";
        }
        if (!otpService.isVerified(accessToken, phone)) {
            return "OTP was verified on screen, but MSG91 server verification failed. Send OTP again and verify once more.";
        }
        return null;
    }

    private String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private AppUser findUserByPhone(String phone) {
        String requested = onlyDigits(phone);
        return users.findAll().stream()
                .filter(user -> onlyDigits(user.getPhone()).equals(requested))
                .findFirst()
                .orElse(null);
    }

    private boolean phoneAlreadyRegistered(String phone) {
        return findUserByPhone(phone) != null;
    }

    private void loginUser(AppUser user, HttpServletRequest request) {
        String loginName = user.getUsername() != null && !user.getUsername().isBlank()
                ? user.getUsername()
                : user.getEmail();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                loginName,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    private boolean canPreviewJob(Job job, Authentication authentication) {
        if (!isRealUser(authentication)) {
            return false;
        }
        AppUser user = currentUser(authentication);
        return user.getRole() == Role.ADMIN
                || (user.getRole() == Role.EMPLOYER
                && job.getEmployer() != null
                && Objects.equals(job.getEmployer().getId(), user.getId()));
    }

    private ResponseEntity<Resource> resumeResponse(String resumePath) {
        if (resumePath == null || resumePath.isBlank()) {
            throw new ResourceNotFoundException("Resume not attached");
        }
        Path path = Path.of(resumePath).normalize();
        FileSystemResource resource = new FileSystemResource(path);
        if (!resource.exists()) {
            throw new ResourceNotFoundException("Resume file not found");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path.getFileName() + "\"")
                .body(resource);
    }
}
