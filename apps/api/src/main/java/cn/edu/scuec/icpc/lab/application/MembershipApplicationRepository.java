package cn.edu.scuec.icpc.lab.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipApplicationRepository extends JpaRepository<MembershipApplication, UUID> {

    List<MembershipApplication> findByApplicantPersonIdOrderByCreatedAtDesc(UUID applicantPersonId);

    Optional<MembershipApplication> findByApplicantPersonIdAndOpenType(UUID applicantPersonId, String openType);

    List<MembershipApplication> findByStatusInOrderBySubmittedAtAsc(List<String> statuses);
}