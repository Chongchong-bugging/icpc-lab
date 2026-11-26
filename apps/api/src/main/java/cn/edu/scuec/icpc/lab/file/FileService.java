package cn.edu.scuec.icpc.lab.file;

import cn.edu.scuec.icpc.lab.audit.AuditService;
import cn.edu.scuec.icpc.lab.common.ApiException;
import cn.edu.scuec.icpc.lab.config.FileProperties;
import cn.edu.scuec.icpc.lab.security.AccountPrincipal;
import cn.edu.scuec.icpc.lab.security.CurrentUser;
import cn.edu.scuec.icpc.lab.security.ScopeGrant;
import cn.edu.scuec.icpc.lab.security.ScopeType;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 附件存取（WP-P0-05 / TECH-13）。
 * - 本体落盘（不存库），元数据入库；
 * - 上传/下载均需权限 + 范围；下载走短时签名链接；
 * - 归档而不是物理删除（DOM-11）。
 */
@Service
public class FileService {

    private static final Pattern SAFE_EXTENSION = Pattern.compile("[a-z0-9]{1,10}");
    private static final String DOWNLOAD_PATH = "/api/v1/files/download?token=";

    private final FileProperties properties;
    private final FileObjectRepository repository;
    private final FileTokenService tokenService;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public FileService(FileProperties properties, FileObjectRepository repository,
                       FileTokenService tokenService, CurrentUser currentUser, AuditService auditService) {
        this.properties = properties;
        this.repository = repository;
        this.tokenService = tokenService;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    @PostConstruct
    void initStorage() {
        try {
            Files.createDirectories(root());
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建附件目录：" + root(), ex);
        }
    }

    @Transactional
    public FileView store(MultipartFile file, String visibility, String objectType, UUID objectId) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "附件不能为空");
        }
        if (file.getSize() > properties.maxSizeBytes()) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "附件超过大小限制");
        }
        String normalizedVisibility = visibility == null ? FileObject.SCOPED : visibility.toUpperCase(Locale.ROOT);
        if (!normalizedVisibility.equals(FileObject.PRIVATE)
                && !normalizedVisibility.equals(FileObject.SCOPED)
                && !normalizedVisibility.equals(FileObject.INTERNAL)) {
            throw ApiException.badRequest("INVALID_VISIBILITY", "可见性取值不合法");
        }
        AccountPrincipal principal = currentUser.principal();
        String storedName = UUID.randomUUID() + extension(file.getOriginalFilename());
        Path target = resolve(storedName);
        String sha256 = copyWithDigest(file, target);

        FileObject object = new FileObject();
        object.setOwnerPersonId(principal.personId());
        object.setOriginalName(originalName(file.getOriginalFilename()));
        object.setStoredName(storedName);
        object.setContentType(file.getContentType());
        object.setSizeBytes(file.getSize());
        object.setSha256(sha256);
        object.setVisibility(normalizedVisibility);
        object.setObjectType(objectType);
        object.setObjectId(objectId);
        repository.save(object);
        auditService.record("file.upload", "file_object", object.getId(), null,
                new FileView(object.getId(), object.getOriginalName(), object.getContentType(),
                        object.getSizeBytes(), object.getSha256(), object.getVisibility(),
                        object.getObjectType(), object.getObjectId(), object.getCreatedAt(), null).toAudit(),
                null);
        return view(object);
    }

    @Transactional(readOnly = true)
    public FileView view(UUID id) {
        FileObject object = requireReadable(id);
        return view(object);
    }

    @Transactional(readOnly = true)
    public FileObject requireReadable(UUID id) {
        FileObject object = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("附件不存在"));
        if (object.getArchivedAt() != null) {
            throw ApiException.notFound("附件已归档");
        }
        AccountPrincipal principal = currentUser.principal();
        if (principal.personId().equals(object.getOwnerPersonId())) {
            return object;
        }
        boolean granted = principal.hasPermission("file:download");
        if (!granted) {
            throw ApiException.forbidden("FORBIDDEN", "缺少附件下载权限");
        }
        if (FileObject.PRIVATE.equals(object.getVisibility())) {
            throw ApiException.forbidden("FORBIDDEN", "超出附件数据范围");
        }
        if (FileObject.INTERNAL.equals(object.getVisibility())) {
            return object;
        }
        boolean global = principal.grants().stream()
                .anyMatch(grant -> grant.permission().equals("file:download") && grant.scopeType() == ScopeType.GLOBAL);
        if (!global && !hasScopedGrant(principal)) {
            throw ApiException.forbidden("FORBIDDEN", "超出附件数据范围");
        }
        return object;
    }

    private boolean hasScopedGrant(AccountPrincipal principal) {
        return principal.grants().stream()
                .anyMatch(grant -> grant.permission().equals("file:download")
                        && (grant.scopeType() == ScopeType.DEPARTMENT || grant.scopeType() == ScopeType.TEAM));
    }

    public Resource resource(FileObject object) {
        Path path = resolve(object.getStoredName());
        if (!Files.exists(path)) {
            throw ApiException.notFound("附件文件不存在");
        }
        return new FileSystemResource(path);
    }

    /** 签名下载只信任 token；返回前仍校验归档状态。 */
    @Transactional(readOnly = true)
    public FileObject requireDownloadable(UUID id) {
        FileObject object = repository.findById(id).orElseThrow(() -> ApiException.notFound("附件不存在"));
        if (object.getArchivedAt() != null) {
            throw ApiException.notFound("附件已归档");
        }
        return object;
    }

    @Transactional
    public void archive(UUID id) {
        FileObject object = repository.findById(id).orElseThrow(() -> ApiException.notFound("附件不存在"));
        AccountPrincipal principal = currentUser.principal();
        if (!principal.personId().equals(object.getOwnerPersonId()) && !principal.hasPermission("file:download")) {
            throw ApiException.forbidden("FORBIDDEN", "无权归档该附件");
        }
        object.setArchivedAt(Instant.now());
        repository.save(object);
        auditService.record("file.archive", "file_object", id, null, null, null);
    }

    FileView view(FileObject object) {
        String token = tokenService.issue(object.getId(), currentUser.principal().personId());
        return new FileView(object.getId(), object.getOriginalName(), object.getContentType(),
                object.getSizeBytes(), object.getSha256(), object.getVisibility(),
                object.getObjectType(), object.getObjectId(), object.getCreatedAt(), DOWNLOAD_PATH + token);
    }

    private Path root() {
        return Paths.get(properties.storageDir()).toAbsolutePath().normalize();
    }

    private Path resolve(String storedName) {
        Path path = root().resolve(storedName).normalize();
        if (!path.startsWith(root())) {
            throw ApiException.badRequest("INVALID_STORAGE_NAME", "非法的存储名");
        }
        return path;
    }

    private String copyWithDigest(MultipartFile file, Path target) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = file.getInputStream(); OutputStream out = Files.newOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | java.security.NoSuchAlgorithmException ex) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // 清理失败不掩盖原始错误
            }
            throw new IllegalStateException("附件写入失败", ex);
        }
    }

    private static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return SAFE_EXTENSION.matcher(extension).matches() ? "." + extension : "";
    }

    private static String originalName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "attachment";
        }
        String trimmed = filename.replace('\\', '/');
        trimmed = trimmed.substring(trimmed.lastIndexOf('/') + 1);
        return trimmed.length() > 255 ? trimmed.substring(trimmed.length() - 255) : trimmed;
    }

    public record FileView(
            UUID id,
            String originalName,
            String contentType,
            long sizeBytes,
            String sha256,
            String visibility,
            String objectType,
            UUID objectId,
            Instant createdAt,
            String downloadUrl) {

        java.util.Map<String, Object> toAudit() {
            return java.util.Map.of(
                    "id", id,
                    "originalName", originalName,
                    "sizeBytes", sizeBytes,
                    "sha256", sha256,
                    "visibility", visibility);
        }
    }
}