package cn.edu.scuec.icpc.lab.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApplicationReviewRepository extends JpaRepository<ApplicationReview, UUID> {

    List<ApplicationReview> findByApplicationIdOrderByDecidedAtAsc(UUID applicationId);
}