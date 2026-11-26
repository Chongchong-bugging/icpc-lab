package cn.edu.scuec.icpc.lab.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionRepository extends JpaRepository<Position, UUID> {

    List<Position> findAllByOrderByLevelDescCodeAsc();

    Optional<Position> findByCode(String code);
}