package cn.edu.scuec.icpc.lab.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TermRepository extends JpaRepository<Term, UUID> {

    List<Term> findAllByOrderByCodeDesc();

    Optional<Term> findByCode(String code);
}