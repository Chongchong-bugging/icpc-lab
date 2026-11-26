package cn.edu.scuec.icpc.lab.importing;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 导入框架接口（WP-P0-13）。两阶段：预检不写业务表；确认单事务落库。
 */
@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('import:write')")
    public ImportService.BatchView preview(@RequestParam("kind") String kind,
                                           @RequestParam("file") MultipartFile file) {
        return importService.preview(kind, file);
    }

    @PostMapping("/{id}/apply")
    @PreAuthorize("hasAuthority('import:write')")
    public ImportService.BatchView apply(@PathVariable UUID id,
                                         @RequestParam("file") MultipartFile file) {
        return importService.apply(id, file);
    }

    @GetMapping(value = "/{id}/errors.csv", produces = "text/csv")
    @PreAuthorize("hasAuthority('import:write')")
    public ResponseEntity<String> errorsCsv(@PathVariable UUID id) {
        String csv = importService.errorsCsv(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("import-errors.csv", StandardCharsets.UTF_8)
                                .build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }
}