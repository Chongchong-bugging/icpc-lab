package cn.edu.scuec.icpc.lab.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SemesterRegistrationRepository extends JpaRepository<SemesterRegistration, UUID> {

    Optional<SemesterRegistration> findByPersonIdAndSemesterId(UUID personId, UUID semesterId);

    List<SemesterRegistration> findBySemesterIdAndAssessment(UUID semesterId, String assessment);

    List<SemesterRegistration> findBySemesterIdOrderByCreatedAtAsc(UUID semesterId);

    List<SemesterRegistration> findByPersonIdOrderByCreatedAtDesc(UUID personId);
}