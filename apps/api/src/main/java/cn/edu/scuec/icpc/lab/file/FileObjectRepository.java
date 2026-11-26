package cn.edu.scuec.icpc.lab.file;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileObjectRepository extends JpaRepository<FileObject, UUID> {
}