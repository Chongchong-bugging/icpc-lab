package cn.edu.scuec.icpc.lab.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlgorithmCategoryRepository extends JpaRepository<AlgorithmCategory, UUID> {

    List<AlgorithmCategory> findAllByOrderBySortOrderAscNameAsc();

    List<AlgorithmCategory> findAllByParentIdOrderBySortOrderAscNameAsc(UUID parentId);

    Optional<AlgorithmCategory> findByCode(String code);

    Optional<AlgorithmCategory> findByNameKey(String nameKey);

    boolean existsByParentIdAndActiveTrue(UUID parentId);

    long countByParentIdAndActiveTrue(UUID parentId);

    long countByActiveTrue();
}