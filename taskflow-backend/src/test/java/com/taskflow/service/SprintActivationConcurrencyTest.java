package com.taskflow.service;

import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.Role;
import com.taskflow.entity.Sprint;
import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;
import com.taskflow.enums.SprintStatus;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.SprintRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.support.TestDatabaseCleaner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Concurrent activations must be serialized per project. */
@SpringBootTest
@ActiveProfiles("test")
class SprintActivationConcurrencyTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private SprintRepository sprintRepository;
    @Autowired private SprintService sprintService;
    @Autowired private PasswordEncoder passwordEncoder;

    private final LocalDate today = LocalDate.now();
    private Organization org;
    private User manager;
    private Project projectOne;
    private Project projectTwo;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        org = organizationRepository.save(new Organization("Sprint Org", "sprint-concurrency", "Software", "UTC", "MON-FRI"));
        Role adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        Role pmRole = roleRepository.save(new Role(RoleName.PROJECT_MANAGER, "PM"));
        roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));
        roleRepository.save(new Role(RoleName.QA_TESTER, "QA"));
        manager = userRepository.save(new User(org, "EMP-PM", "pm@sprint.test",
                passwordEncoder.encode("StrongPass123!"), "Project Manager", "PM", pmRole));
        assertThat(adminRole).isNotNull();
        projectOne = projectRepository.save(new Project(org, "SPC1", "Project One", "", manager,
                today.minusDays(1), today.plusDays(60)));
        projectTwo = projectRepository.save(new Project(org, "SPC2", "Project Two", "", manager,
                today.minusDays(1), today.plusDays(60)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Sprint plannedSprint(Project project, String name) {
        return sprintRepository.save(new Sprint(project, name, "goal",
                today, today.plusDays(14), manager));
    }

    private void authenticate(User user) {
        CustomUserDetails principal = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private int[] concurrentlyActivate(Long projectId, List<Long> sprintIds) throws Exception {
        int threads = sprintIds.size();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        try {
            for (Long sprintId : sprintIds) {
                executor.submit(() -> {
                    authenticate(manager);
                    ready.countDown();
                    start.await();
                    try {
                        sprintService.startSprint(projectId, sprintId);
                        successes.incrementAndGet();
                    } catch (Exception ignored) {
                        failures.incrementAndGet();
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                    return null;
                });
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(45, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }
        return new int[]{successes.get(), failures.get()};
    }

    @Test
    void normalActivationAndSequentialSecondActivation() {
        authenticate(manager);
        Sprint first = plannedSprint(projectOne, "Sprint One");
        Sprint second = plannedSprint(projectOne, "Sprint Two");

        sprintService.startSprint(projectOne.getId(), first.getId());
        assertThat(sprintRepository.findByProjectIdAndStatusOrderByStartDateDesc(
                projectOne.getId(), SprintStatus.ACTIVE)).hasSize(1);

        assertThat(catchThrowable(() ->
                sprintService.startSprint(projectOne.getId(), second.getId()))).isNotNull();
        assertThat(sprintRepository.findById(second.getId()).orElseThrow().getStatus())
                .isEqualTo(SprintStatus.PLANNED);
    }

    @Test
    void concurrentActivationOfSameProjectProducesSingleActiveSprint() throws Exception {
        Sprint first = plannedSprint(projectOne, "Concurrent One");
        Sprint second = plannedSprint(projectOne, "Concurrent Two");

        int[] result = concurrentlyActivate(projectOne.getId(), List.of(first.getId(), second.getId()));

        assertThat(result[0]).isEqualTo(1);
        assertThat(result[1]).isEqualTo(1);
        assertThat(sprintRepository.findByProjectIdAndStatusOrderByStartDateDesc(
                projectOne.getId(), SprintStatus.ACTIVE)).hasSize(1);
        long planned = sprintRepository.findByProjectIdAndStatusOrderByStartDateDesc(
                projectOne.getId(), SprintStatus.PLANNED).size();
        assertThat(planned).isEqualTo(1);
    }

    @Test
    void differentProjectsCanActivateIndependently() throws Exception {
        Sprint one = plannedSprint(projectOne, "Independent One");
        Sprint two = plannedSprint(projectTwo, "Independent Two");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        try {
            for (int i = 0; i < 2; i++) {
                final Long projectId = i == 0 ? projectOne.getId() : projectTwo.getId();
                final Long sprintId = i == 0 ? one.getId() : two.getId();
                executor.submit(() -> {
                    authenticate(manager);
                    ready.countDown();
                    start.await();
                    try {
                        sprintService.startSprint(projectId, sprintId);
                        successes.incrementAndGet();
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                    return null;
                });
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(45, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }
        assertThat(successes.get()).isEqualTo(2);
        assertThat(sprintRepository.findByProjectIdAndStatusOrderByStartDateDesc(
                projectOne.getId(), SprintStatus.ACTIVE)).hasSize(1);
        assertThat(sprintRepository.findByProjectIdAndStatusOrderByStartDateDesc(
                projectTwo.getId(), SprintStatus.ACTIVE)).hasSize(1);
    }

    @Test
    void transactionIntegrityIsPreservedAfterFailedActivation() throws Exception {
        Sprint first = plannedSprint(projectOne, "Integrity One");
        Sprint second = plannedSprint(projectOne, "Integrity Two");

        int[] result = concurrentlyActivate(projectOne.getId(), List.of(first.getId(), second.getId()));

        assertThat(result[0] + result[1]).isEqualTo(2);
        assertThat(sprintRepository.count()).isEqualTo(2);
        assertThat(sprintRepository.findByProjectIdAndStatusOrderByStartDateDesc(
                projectOne.getId(), SprintStatus.ACTIVE)).hasSize(1);
    }
}
