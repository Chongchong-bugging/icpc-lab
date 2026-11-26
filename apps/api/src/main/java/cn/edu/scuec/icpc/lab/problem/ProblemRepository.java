package cn.edu.scuec.icpc.lab.problem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProblemRepository extends JpaRepository<Problem, UUID> {

    boolean existsBySourceKeyAndTitleKey(String sourceKey, String titleKey);

    Optional<Problem> findBySourceKeyAndTitleKey(String sourceKey, String titleKey);
}