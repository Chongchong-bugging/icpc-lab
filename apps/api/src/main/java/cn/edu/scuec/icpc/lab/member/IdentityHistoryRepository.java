package cn.edu.scuec.icpc.lab.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IdentityHistoryRepository extends JpaRepository<IdentityHistory, UUID> {

    List<IdentityHistory> findByPersonIdOrderByChangedAtDesc(UUID personId);
}