package cn.edu.scuec.icpc.lab.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DisciplineRecordRepository extends JpaRepository<DisciplineRecord, UUID> {

    List<DisciplineRecord> findByPersonIdOrderByDecidedAtDesc(UUID personId);
}