package com.taskflow.controller;

import com.taskflow.config.StorageProperties;
import com.taskflow.entity.Bug;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.BugSeverity;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.RoleName;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.repository.BugAttachmentRepository;
import com.taskflow.repository.BugRepository;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.JwtService;
import com.taskflow.service.BugAttachmentService;
import com.taskflow.support.TestDatabaseCleaner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class BugAttachmentHardeningIntegrationTest {

    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01, 0x02};

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private BugRepository bugRepository;
    @Autowired private BugAttachmentRepository attachmentRepository;
    @Autowired private BugAttachmentService attachmentService;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private StorageProperties storageProperties;

    private Bug bug;
    private User admin;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        Organization org = organizationRepository.save(
                new Organization("Hardening Org", "attach-hardening", "Software", "UTC", "MON-FRI"));
        Role adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        roleRepository.save(new Role(RoleName.PROJECT_MANAGER, "PM"));
        roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));
        roleRepository.save(new Role(RoleName.QA_TESTER, "QA"));
        admin = userRepository.save(new User(org, "EMP-0001", "admin@attach.test",
                passwordEncoder.encode("StrongPass123!"), "Admin", "Admin", adminRole));
        Project project = projectRepository.save(new Project(org, "HRD", "Project", "", admin,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30)));
        bug = bugRepository.save(new Bug(project, "HRD-B1", null, "Bug", "desc", "steps",
                BugSeverity.MEDIUM, PriorityLevel.MEDIUM, admin, null));
        authenticate(admin);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(User user) {
        CustomUserDetails principal = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private MockMultipartFile png(String filename) {
        return new MockMultipartFile("file", filename, "image/png", PNG);
    }

    @Test
    void validUploadAndDownloadPreserveSanitizedFilename() throws Exception {
        var response = attachmentService.upload(bug.getId(), png("report.txt"));
        assertThat(attachmentRepository.findById(response.id())).isPresent();
        assertThat(attachmentService.filename(bug.getId(), response.id())).isEqualTo("report.txt");
        assertThat(attachmentService.contentType(bug.getId(), response.id())).isEqualTo("image/png");
        assertThat(attachmentService.download(bug.getId(), response.id())).isNotNull();

        Path dir = Paths.get(storageProperties.localDir()).toAbsolutePath().normalize()
                .resolve("bugs").resolve(String.valueOf(bug.getId()));
        var saved = attachmentRepository.findById(response.id()).orElseThrow();
        assertThat(Files.exists(dir.resolve(saved.getStoredFilename()))).isTrue();
    }

    @Test
    void maliciousFilenameIsSanitized() {
        var response = attachmentService.upload(bug.getId(),
                png("../../evil\"file\r\nInjected:header.png"));
        String filename = attachmentService.filename(bug.getId(), response.id());
        assertThat(filename).doesNotContain("/", "\\", "\"", "\r", "\n", ":");
        assertThat(filename).contains("evil", "file", "Injected", "header");
    }

    @Test
    void unicodeFilenameIsPreserved() {
        var response = attachmentService.upload(bug.getId(), png("Rapport_été_2026.png"));
        assertThat(attachmentService.filename(bug.getId(), response.id()))
                .isEqualTo("Rapport_été_2026.png");
    }

    @Test
    void controlCharactersAreRemoved() {
        var response = attachmentService.upload(bug.getId(), png("bad\u0000name\u0007.txt"));
        assertThat(attachmentService.filename(bug.getId(), response.id())).doesNotContain("\u0000", "\u0007");
    }

    @Test
    void unsupportedContentTypeIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "evil.exe",
                "application/x-msdownload", PNG);
        assertThatThrownBy(() -> attachmentService.upload(bug.getId(), file))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(attachmentRepository.count()).isZero();
    }

    @Test
    void contentMismatchIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.png",
                "image/png", "this is plain text".getBytes());
        assertThatThrownBy(() -> attachmentService.upload(bug.getId(), file))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(attachmentRepository.count()).isZero();
    }

    @Test
    void missingFileCanStillBeDeleted() throws Exception {
        var response = attachmentService.upload(bug.getId(), png("gone.png"));
        var saved = attachmentRepository.findById(response.id()).orElseThrow();
        Files.deleteIfExists(Paths.get(saved.getStoragePath()).resolve(saved.getStoredFilename()));

        attachmentService.delete(bug.getId(), response.id());
        assertThat(attachmentRepository.findById(response.id())).isEmpty();
    }

    @Test
    void duplicateDeleteIsSafe() {
        var response = attachmentService.upload(bug.getId(), png("once.png"));
        attachmentService.delete(bug.getId(), response.id());
        assertThatThrownBy(() -> attachmentService.delete(bug.getId(), response.id()))
                .isInstanceOf(com.taskflow.exception.ResourceNotFoundException.class);
    }

    @Test
    void filesystemDeletionFailureDoesNotBlockDatabaseDeletion() throws Exception {
        var response = attachmentService.upload(bug.getId(), png("folder.png"));
        var saved = attachmentRepository.findById(response.id()).orElseThrow();
        Path file = Paths.get(saved.getStoragePath()).resolve(saved.getStoredFilename());
        Files.deleteIfExists(file);
        Files.createDirectories(file);
        Files.writeString(file.resolve("child.txt"), "keeps directory non-empty");

        attachmentService.delete(bug.getId(), response.id());

        assertThat(attachmentRepository.findById(response.id())).isEmpty();
        assertThat(Files.exists(file.resolve("child.txt"))).isTrue();
    }

    @Test
    void uploadFailureDuringStorageLeavesNoDatabaseRecord() throws Exception {
        MultipartFile failing = org.mockito.Mockito.mock(MultipartFile.class);
        org.mockito.Mockito.when(failing.isEmpty()).thenReturn(false);
        org.mockito.Mockito.when(failing.getBytes()).thenThrow(new java.io.IOException("disk failure"));

        assertThatThrownBy(() -> attachmentService.upload(bug.getId(), failing))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(attachmentRepository.count()).isZero();
    }

    @Test
    void unauthorizedDeleteIsRejectedAndFileRemains() throws Exception {
        var response = attachmentService.upload(bug.getId(), png("keep.png"));
        var saved = attachmentRepository.findById(response.id()).orElseThrow();
        Path file = Paths.get(saved.getStoragePath()).resolve(saved.getStoredFilename());

        Organization otherOrg = organizationRepository.save(
                new Organization("Other", "attach-other", "Finance", "UTC", "MON-FRI"));
        Role otherRole = roleRepository.findById(roleRepository.findAll().stream()
                .filter(r -> r.getName() == RoleName.ADMIN).findFirst().orElseThrow().getId()).orElseThrow();
        User otherAdmin = userRepository.save(new User(otherOrg, "EMP-B1", "other@attach.test",
                passwordEncoder.encode("StrongPass123!"), "Other Admin", "Admin", otherRole));
        authenticate(otherAdmin);

        assertThatThrownBy(() -> attachmentService.delete(bug.getId(), response.id()))
                .isInstanceOf(com.taskflow.exception.UnauthorizedResourceAccessException.class);
        assertThat(attachmentRepository.findById(response.id())).isPresent();
        assertThat(Files.exists(file)).isTrue();
        assertThat(jwtService).isNotNull();
    }
}
