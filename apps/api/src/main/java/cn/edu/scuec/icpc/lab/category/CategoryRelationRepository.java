package cn.edu.scuec.icpc.lab.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryRelationRepository extends JpaRepository<CategoryRelation, UUID> {

    List<CategoryRelation> findAllByCategoryId(UUID categoryId);

    List<CategoryRelation> findAllByPrerequisiteId(UUID prerequisiteId);

    boolean existsByCategoryIdAndPrerequisiteId(UUID categoryId, UUID prerequisiteId);
}