package cn.edu.scuec.icpc.lab.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryAttributeRepository extends JpaRepository<CategoryAttribute, UUID> {

    List<CategoryAttribute> findAllByCategoryIdOrderBySortOrderAscCodeAsc(UUID categoryId);

    List<CategoryAttribute> findAllByCategoryIdAndActiveTrueOrderBySortOrderAscCodeAsc(UUID categoryId);

    Optional<CategoryAttribute> findByCategoryIdAndCode(UUID categoryId, String code);
}