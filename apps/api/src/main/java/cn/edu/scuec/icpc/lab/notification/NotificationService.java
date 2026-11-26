package cn.edu.scuec.icpc.lab.notification;

import cn.edu.scuec.icpc.lab.common.ApiException;
import cn.edu.scuec.icpc.lab.security.CurrentUser;
import jakarta.transaction.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 站内通知（FR-29）。P0 唯一通知渠道；不做短信/邮件（SCOPE 红线）。 */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final JdbcTemplate jdbc;
    private final CurrentUser currentUser;

    public NotificationService(NotificationRepository repository, JdbcTemplate jdbc, CurrentUser currentUser) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    @Transactional
    public void notify(UUID personId, String type, String title, String body, String bizType, UUID bizId) {
        Notification notification = new Notification();
        notification.setRecipientPersonId(personId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setBizType(bizType);
        notification.setBizId(bizId);
        repository.save(notification);
    }

    /** 通知所有持有某权限且授权有效的人（如申请提交后提醒审批人）。 */
    @Transactional
    public void notifyPermissionHolders(String permission, String type, String title, String body,
                                        String bizType, UUID bizId) {
        List<UUID> personIds = jdbc.queryForList("""
                select distinct ra.person_id
                  from role_assignment ra
                  join role r on r.id = ra.role_id
                  join role_permission rp on rp.role_id = r.id
                 where rp.permission_code = ?
                   and ra.revoked_at is null
                   and r.active = true
                   and (ra.valid_from is null or ra.valid_from <= current_timestamp)
                   and (ra.valid_until is null or ra.valid_until > current_timestamp)
                """, UUID.class, permission);
        for (UUID personId : personIds) {
            notify(personId, type, title, body, bizType, bizId);
        }
    }

    public List<NotificationView> mine(boolean unreadOnly) {
        UUID personId = currentUser.principal().personId();
        List<Notification> rows = unreadOnly
                ? repository.findByRecipientPersonIdAndReadAtIsNullOrderByCreatedAtDesc(personId)
                : repository.findByRecipientPersonIdOrderByCreatedAtDesc(personId);
        return rows.stream().map(NotificationView::of).toList();
    }

    public UnreadView unread() {
        UUID personId = currentUser.principal().personId();
        return new UnreadView(repository.countByRecipientPersonIdAndReadAtIsNull(personId));
    }

    @Transactional
    public void markRead(UUID id) {
        Notification notification = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("通知不存在"));
        if (!notification.getRecipientPersonId().equals(currentUser.principal().personId())) {
            throw ApiException.forbidden("FORBIDDEN", "无权操作他人通知");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(OffsetDateTime.now());
            repository.save(notification);
        }
    }

    @Transactional
    public void markAllRead() {
        UUID personId = currentUser.principal().personId();
        for (Notification notification : repository.findByRecipientPersonIdAndReadAtIsNullOrderByCreatedAtDesc(personId)) {
            notification.setReadAt(OffsetDateTime.now());
            repository.save(notification);
        }
    }

    public record NotificationView(UUID id, String type, String title, String body, String bizType, UUID bizId,
                                   boolean read, OffsetDateTime createdAt) {
        static NotificationView of(Notification n) {
            return new NotificationView(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getBizType(),
                    n.getBizId(), n.getReadAt() != null, n.getCreatedAt());
        }
    }

    public record UnreadView(long unread) {
    }
}