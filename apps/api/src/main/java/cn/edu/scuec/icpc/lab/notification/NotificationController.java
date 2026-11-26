package cn.edu.scuec.icpc.lab.notification;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('notification:read:self')")
    public List<NotificationService.NotificationView> mine(@RequestParam(defaultValue = "false") boolean unreadOnly) {
        return notificationService.mine(unreadOnly);
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAuthority('notification:read:self')")
    public NotificationService.UnreadView unread() {
        return notificationService.unread();
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("hasAuthority('notification:read:self')")
    public void markRead(@PathVariable UUID id) {
        notificationService.markRead(id);
    }

    @PostMapping("/read-all")
    @PreAuthorize("hasAuthority('notification:read:self')")
    public void markAllRead() {
        notificationService.markAllRead();
    }
}