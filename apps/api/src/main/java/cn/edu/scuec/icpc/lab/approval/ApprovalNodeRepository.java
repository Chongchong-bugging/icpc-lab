package cn.edu.scuec.icpc.lab.approval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApprovalNodeRepository extends JpaRepository<ApprovalNode, UUID> {

    List<ApprovalNode> findByInstanceIdOrderByLevelAscCreatedAtAsc(UUID instanceId);

    List<ApprovalNode> findByAssigneePersonIdAndDecisionOrderByCreatedAtDesc(UUID assigneePersonId, String decision);
}