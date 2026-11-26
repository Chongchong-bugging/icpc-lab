package cn.edu.scuec.icpc.lab.identity;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, UUID> {

    List<RoleAssignment> findByPersonIdAndRevokedAtIsNull(UUID personId);
}