package cn.edu.scuec.icpc.lab.file;

import cn.edu.scuec.icpc.lab.security.PublicEndpoint;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;
    private final FileTokenService tokenService;

    public FileController(FileService fileService, FileTokenService tokenService) {
        this.fileService = fileService;
        this.tokenService = tokenService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('file:upload')")
    public FileService.FileView upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) UUID objectId) {
        return fileService.store(file, visibility, objectType, objectId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('file:download')")
    public FileService.FileView metadata(@PathVariable UUID id) {
        return fileService.view(id);
    }

    /**
     * 签名下载：token 由已授权的元数据接口签发，5 分钟有效，绑定用户与文件（TECH-13）。
     */
    @GetMapping("/download")
    @PublicEndpoint
    public ResponseEntity<Resource> download(@RequestParam("token") String token) {
        UUID fileId = tokenService.verify(token);
        FileObject object = fileService.requireDownloadable(fileId);
        Resource resource = fileService.resource(object);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(object.getOriginalName(), StandardCharsets.UTF_8)
                .build();
        MediaType mediaType = object.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(object.getContentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(resource);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('file:upload')")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        fileService.archive(id);
        return ResponseEntity.noContent().build();
    }
}