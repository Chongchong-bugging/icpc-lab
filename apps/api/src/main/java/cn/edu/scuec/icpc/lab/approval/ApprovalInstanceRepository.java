package cn.edu.scuec.icpc.lab.approval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalInstanceRepository extends JpaRepository<ApprovalInstance, UUID> {

    List<ApprovalInstance> findByBizTypeAndBizIdOrderByCreatedAtDesc(String bizType, UUID bizId);

    Optional<ApprovalInstance> findFirstByBizTypeAndBizIdAndStatusOrderByCreatedAtDesc(String bizType, UUID bizId, String status);
}