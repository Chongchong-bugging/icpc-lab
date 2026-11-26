package cn.edu.scuec.icpc.lab.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionAssignmentRepository extends JpaRepository<PositionAssignment, UUID> {

    List<PositionAssignment> findByPersonIdOrderByStartDateDesc(UUID personId);

    List<PositionAssignment> findByStatusOrderByStartDateDesc(String status);

    Optional<PositionAssignment> findByActiveKey(String activeKey);
}