package com.taskflow.repository;

import com.taskflow.entity.TestSuiteCase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestSuiteCaseRepository extends JpaRepository<TestSuiteCase, TestSuiteCase.PK> {
    java.util.List<TestSuiteCase> findBySuiteId(Long suiteId);
    boolean existsById(TestSuiteCase.PK id);
}
