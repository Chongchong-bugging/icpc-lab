package cn.edu.scuec.icpc.lab.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationVersionRepository extends JpaRepository<ApplicationVersion, UUID> {

    List<ApplicationVersion> findByApplicationIdOrderByVersionNoDesc(UUID applicationId);

    Optional<ApplicationVersion> findByApplicationIdAndVersionNo(UUID applicationId, int versionNo);
}