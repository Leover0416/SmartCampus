package com.simon.campus.controller;

import com.simon.campus.common.BizException;
import com.simon.campus.common.R;
import com.simon.campus.model.entity.KnowledgeCategory;
import com.simon.campus.model.entity.KnowledgeDoc;
import com.simon.campus.model.vo.DocVO;
import com.simon.campus.model.vo.SearchTestResultVO;
import com.simon.campus.service.ingest.DocumentPreview;
import com.simon.campus.service.ingest.KnowledgeService;
import com.simon.campus.service.ingest.VisibilityPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public record CreateCategoryRequest(String name, String code) {}

    // Only teachers and admins can upload/delete documents
    @PostMapping("/docs/upload")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<DocVO> upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam("title") String title,
        @RequestParam(value = "categoryCode", required = false) String categoryCode,
        @RequestParam(value = "accessLevel", defaultValue = "0") int accessLevel,
        Authentication auth
    ) throws Exception {
        if (file.isEmpty()) throw new BizException(400, "文件不能为空");
        if (file.getSize() > 100L * 1024 * 1024) throw new BizException(400, "文件大小不能超过100MB");
        if (!VisibilityPolicy.isValidDocumentLevel(accessLevel)) throw new BizException(400, "可见范围无效");

        KnowledgeDoc doc = knowledgeService.upload(file, title, categoryCode, accessLevel, auth.getName());
        DocVO vo = new DocVO();
        vo.setDocId(doc.getDocId());
        vo.setTitle(doc.getTitle());
        vo.setFileName(doc.getFileName());
        vo.setStatus(doc.getStatus());
        vo.setAccessLevel(doc.getAccessLevel());
        vo.setAccessLevelName(DocVO.accessLevelName(doc.getAccessLevel()));
        return R.ok(vo);
    }

    @GetMapping("/docs")
    public R<List<DocVO>> list(
        @RequestParam(required = false) String categoryCode,
        @RequestParam(required = false) String status,
        Authentication auth
    ) {
        int userAccessLevel = resolveViewerLevel(auth);
        return R.ok(knowledgeService.listDocs(categoryCode, status, userAccessLevel));
    }

    @GetMapping("/categories")
    public R<List<KnowledgeCategory>> categories() {
        return R.ok(knowledgeService.listCategories());
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<KnowledgeCategory> createCategory(@RequestBody CreateCategoryRequest request) {
        if (request == null) throw new BizException(400, "请求不能为空");
        return R.ok(knowledgeService.createCategory(request.name(), request.code()));
    }

    @DeleteMapping("/categories/{code}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> deleteCategory(@PathVariable String code) {
        knowledgeService.deleteCategory(code);
        return R.ok();
    }

    @GetMapping("/docs/{docId}/preview")
    public ResponseEntity<InputStreamResource> preview(@PathVariable String docId, Authentication auth) throws Exception {
        int userAccessLevel = resolveViewerLevel(auth);
        DocumentPreview preview = knowledgeService.previewDoc(docId, userAccessLevel);
        return ResponseEntity.ok()
            .contentType(preview.mediaType())
            .header(HttpHeaders.CONTENT_DISPOSITION, preview.contentDisposition())
            .body(new InputStreamResource(preview.stream()));
    }

    @GetMapping("/docs/{docId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable String docId, Authentication auth) throws Exception {
        int userAccessLevel = resolveViewerLevel(auth);
        DocumentPreview download = knowledgeService.downloadDoc(docId, userAccessLevel);
        return ResponseEntity.ok()
            .contentType(download.mediaType())
            .header(HttpHeaders.CONTENT_DISPOSITION, download.contentDisposition())
            .body(new InputStreamResource(download.stream()));
    }

    @GetMapping("/docs/{docId}/preview-text")
    public ResponseEntity<String> previewText(@PathVariable String docId, Authentication auth) throws Exception {
        int userAccessLevel = resolveViewerLevel(auth);
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .body(knowledgeService.previewTextDoc(docId, userAccessLevel));
    }

    @DeleteMapping("/docs/{docId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> delete(@PathVariable String docId) {
        knowledgeService.deleteDoc(docId);
        return R.ok();
    }

    @PostMapping("/docs/{docId}/reindex")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<DocVO> reindex(@PathVariable String docId) throws Exception {
        KnowledgeDoc doc = knowledgeService.reindexDoc(docId);
        DocVO vo = new DocVO();
        vo.setDocId(doc.getDocId());
        vo.setTitle(doc.getTitle());
        vo.setFileName(doc.getFileName());
        vo.setStatus(doc.getStatus());
        vo.setParentChunkCount(doc.getParentChunkCount());
        vo.setChildChunkCount(doc.getChildChunkCount());
        vo.setAccessLevel(doc.getAccessLevel());
        vo.setAccessLevelName(DocVO.accessLevelName(doc.getAccessLevel()));
        return R.ok(vo);
    }

    @GetMapping("/docs/search-test")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<SearchTestResultVO> searchTest(
        @RequestParam String query,
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) int topK,
        Authentication auth
    ) throws Exception {
        int userAccessLevel = resolveViewerLevel(auth);
        return R.ok(knowledgeService.searchTest(query, userAccessLevel, topK));
    }

    private int resolveViewerLevel(Authentication auth) {
        if (auth == null) return VisibilityPolicy.STUDENT;
        boolean teacher = auth.getAuthorities().stream()
            .map(a -> a.getAuthority())
            .anyMatch(r -> r.equals("ROLE_TEACHER") || r.equals("ROLE_ADMIN"));
        return teacher ? VisibilityPolicy.TEACHER : VisibilityPolicy.STUDENT;
    }
}
