package cn.edu.scuec.icpc.lab.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    List<Membership> findByPersonIdOrderByJoinedAtDesc(UUID personId);

    Optional<Membership> findByActiveKey(String activeKey);
}