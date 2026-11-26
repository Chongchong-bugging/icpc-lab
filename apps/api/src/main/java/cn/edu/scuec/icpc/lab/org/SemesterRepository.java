package cn.edu.scuec.icpc.lab.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SemesterRepository extends JpaRepository<Semester, UUID> {

    List<Semester> findAllByOrderBySortOrderAsc();

    Optional<Semester> findByCode(String code);
}