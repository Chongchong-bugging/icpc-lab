package cn.edu.scuec.icpc.lab.problem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findAllByOrderByNameAsc();

    List<Tag> findAllByTypeOrderByNameAsc(String type);

    Optional<Tag> findByNameAndType(String name, String type);

    boolean existsByNameAndType(String name, String type);
}