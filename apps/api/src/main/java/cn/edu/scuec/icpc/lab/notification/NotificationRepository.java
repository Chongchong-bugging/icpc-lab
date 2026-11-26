package cn.edu.scuec.icpc.lab.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByRecipientPersonIdOrderByCreatedAtDesc(UUID recipientPersonId);

    List<Notification> findByRecipientPersonIdAndReadAtIsNullOrderByCreatedAtDesc(UUID recipientPersonId);

    long countByRecipientPersonIdAndReadAtIsNull(UUID recipientPersonId);
}