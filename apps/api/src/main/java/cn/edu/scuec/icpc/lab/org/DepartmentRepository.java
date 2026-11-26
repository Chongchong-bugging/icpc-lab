package cn.edu.scuec.icpc.lab.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    List<Department> findAllByOrderBySortOrderAsc();

    Optional<Department> findByCode(String code);
}