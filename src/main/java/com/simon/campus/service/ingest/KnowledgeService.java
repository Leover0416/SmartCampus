package com.simon.campus.service.ingest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.simon.campus.common.BizException;
import com.simon.campus.mapper.ChildChunkMapper;
import com.simon.campus.mapper.KnowledgeCategoryMapper;
import com.simon.campus.mapper.KnowledgeDocMapper;
import com.simon.campus.mapper.ParentChunkMapper;
import com.simon.campus.model.entity.ChildChunk;
import com.simon.campus.model.entity.KnowledgeCategory;
import com.simon.campus.model.entity.KnowledgeDoc;
import com.simon.campus.model.entity.ParentChunk;
import com.simon.campus.model.vo.DocVO;
import com.simon.campus.model.vo.SearchTestResultVO;
import com.simon.campus.model.vo.SearchTestResultVO.HitItem;
import io.milvus.response.SearchResultsWrapper.IDScore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeService {

    private final KnowledgeDocMapper docMapper;
    private final KnowledgeCategoryMapper categoryMapper;
    private final ParentChunkMapper  parentMapper;
    private final ChildChunkMapper   childMapper;
    private final MinioService       minioService;
    private final EmbeddingService   embeddingService;
    private final BM25Indexer        bm25Indexer;
    private final MilvusService      milvusService;
    private final IngestAsyncService ingestAsyncService;
    private final DocumentParser     documentParser;

    private static final int MAX_PREVIEW_TEXT_LENGTH = 100_000;

    public List<KnowledgeCategory> listCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<KnowledgeCategory>()
            .eq(KnowledgeCategory::getStatus, 1)
            .orderByAsc(KnowledgeCategory::getSortOrder)
            .orderByAsc(KnowledgeCategory::getId));
    }

    public KnowledgeCategory createCategory(String name, String code) {
        String cleanName = name == null ? "" : name.trim();
        String cleanCode = code == null ? "" : code.trim();
        if (cleanName.isBlank()) throw new BizException(400, "分类名称不能为空");
        if (cleanName.length() > 100) throw new BizException(400, "分类名称不能超过100个字符");
        if (!cleanCode.matches("^[a-z][a-z0-9_]{1,49}$")) {
            throw new BizException(400, "分类编码只能使用小写字母、数字和下划线，且需以字母开头");
        }

        KnowledgeCategory existing = categoryMapper.selectOne(new LambdaQueryWrapper<KnowledgeCategory>()
            .eq(KnowledgeCategory::getCode, cleanCode)
            .last("LIMIT 1"));
        if (existing != null) throw new BizException(400, "分类编码已存在");

        int nextSortOrder = listCategories().stream()
            .map(KnowledgeCategory::getSortOrder)
            .filter(Objects::nonNull)
            .max(Integer::compareTo)
            .map(v -> v + 1)
            .orElse(100);

        KnowledgeCategory category = new KnowledgeCategory();
        category.setName(cleanName);
        category.setCode(cleanCode);
        category.setStatus(1);
        category.setSortOrder(nextSortOrder);
        categoryMapper.insert(category);
        return category;
    }

    public void deleteCategory(String code) {
        String cleanCode = code == null ? "" : code.trim();
        if (cleanCode.isBlank()) throw new BizException(400, "分类编码不能为空");

        KnowledgeCategory existing = categoryMapper.selectOne(new LambdaQueryWrapper<KnowledgeCategory>()
            .eq(KnowledgeCategory::getCode, cleanCode)
            .eq(KnowledgeCategory::getStatus, 1)
            .last("LIMIT 1"));
        if (existing == null) throw new BizException(404, "知识分类不存在");

        Long docCount = docMapper.selectCount(new LambdaQueryWrapper<KnowledgeDoc>()
            .eq(KnowledgeDoc::getCategoryCode, cleanCode));
        if (docCount != null && docCount > 0) {
            throw new BizException(400, "该分类下还有文档，请先删除文档或调整文档分类");
        }

        int rows = categoryMapper.update(null, new LambdaUpdateWrapper<KnowledgeCategory>()
            .eq(KnowledgeCategory::getCode, cleanCode)
            .set(KnowledgeCategory::getStatus, 0));
        log.info("[DELETE_FLOW] knowledge_category={} step=soft_delete rows={}", cleanCode, rows);
        if (rows <= 0) throw new BizException(500, "知识分类删除失败");
    }

    // ── Upload & trigger async ingest ────────────────────────────────────────

    public KnowledgeDoc upload(MultipartFile file, String title, String categoryCode,
                               int accessLevel, String createdBy) throws Exception {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setDocId(UUID.randomUUID().toString());
        doc.setTitle(title);
        doc.setFileName(file.getOriginalFilename());
        String contentType = file.getContentType();
        if (contentType != null && contentType.length() > 255) {
            contentType = contentType.substring(0, 255);
        }
        doc.setFileType(contentType);
        doc.setFileSize(file.getSize());
        doc.setCategoryCode(categoryCode);
        doc.setAccessLevel(accessLevel);
        doc.setStatus("PROCESSING");
        doc.setCreatedBy(createdBy);
        docMapper.insert(doc);

        // Upload raw file to MinIO
        String minioKey = minioService.upload(file, doc.getDocId());
        doc.setMinioKey(minioKey);
        docMapper.updateById(doc);

        // Trigger async pipeline via separate bean (avoids Spring AOP self-call issue)
        byte[] bytes = file.getBytes();
        ingestAsyncService.ingest(doc.getDocId(), bytes, file.getContentType());

        return doc;
    }

    // ── List / Delete ─────────────────────────────────────────────────────────

    public List<DocVO> listDocs(String categoryCode, String status, int userAccessLevel) {
        LambdaQueryWrapper<KnowledgeDoc> wrapper = new LambdaQueryWrapper<KnowledgeDoc>()
            .in(KnowledgeDoc::getAccessLevel, VisibilityPolicy.visibleLevelsForViewer(userAccessLevel))
            .eq(categoryCode != null && !categoryCode.isBlank(), KnowledgeDoc::getCategoryCode, categoryCode)
            .eq(status != null && !status.isBlank(), KnowledgeDoc::getStatus, status)
            .orderByDesc(KnowledgeDoc::getCreatedAt);

        return docMapper.selectList(wrapper).stream()
            .map(this::toDocVO)
            .collect(Collectors.toList());
    }

    public void deleteDoc(String docId) {
        KnowledgeDoc doc = docMapper.selectById(docId);
        if (doc == null) throw new BizException(404, "文档不存在");

        log.info("[DELETE_FLOW] doc={} step=start title={} minioKey={}",
            docId, doc.getTitle(), doc.getMinioKey());
        clearIndexes(docId);
        if (doc.getMinioKey() != null && !doc.getMinioKey().isBlank()) {
            try {
                minioService.deleteStrict(doc.getMinioKey());
            } catch (Exception e) {
                throw new BizException(500, "原始文件删除失败：" + e.getMessage());
            }
        }
        int deletedDocs = docMapper.deleteById(docId);
        log.info("[DELETE_FLOW] doc={} step=mysql_doc_delete rows={}", docId, deletedDocs);
    }

    public KnowledgeDoc reindexDoc(String docId) throws Exception {
        KnowledgeDoc doc = docMapper.selectById(docId);
        if (doc == null) throw new BizException(404, "文档不存在");
        if (doc.getMinioKey() == null || doc.getMinioKey().isBlank()) {
            throw new BizException(404, "原始文件不存在，无法重新索引");
        }

        byte[] bytes;
        try (var inputStream = minioService.download(doc.getMinioKey())) {
            bytes = inputStream.readAllBytes();
        }

        clearIndexes(docId);
        docMapper.updateProcessResult(docId, "PROCESSING", 0, 0, null);
        ingestAsyncService.ingest(docId, bytes, doc.getFileType());

        doc.setStatus("PROCESSING");
        doc.setParentChunkCount(0);
        doc.setChildChunkCount(0);
        doc.setErrorMsg(null);
        return doc;
    }

    public DocumentPreview previewDoc(String docId, int userAccessLevel) throws Exception {
        KnowledgeDoc doc = docMapper.selectById(docId);
        if (doc == null) throw new BizException(404, "文档不存在");
        if (!VisibilityPolicy.canView(doc.getAccessLevel(), userAccessLevel)) {
            throw new BizException(403, "无权预览该文档");
        }
        if (doc.getMinioKey() == null || doc.getMinioKey().isBlank()) {
            throw new BizException(404, "原始文件不存在");
        }
        return DocumentPreview.of(
            doc.getMinioKey(),
            doc.getFileName(),
            doc.getFileType(),
            minioService.download(doc.getMinioKey())
        );
    }

    public DocumentPreview downloadDoc(String docId, int userAccessLevel) throws Exception {
        KnowledgeDoc doc = docMapper.selectById(docId);
        if (doc == null) throw new BizException(404, "文档不存在");
        if (!VisibilityPolicy.canView(doc.getAccessLevel(), userAccessLevel)) {
            throw new BizException(403, "无权下载该文档");
        }
        if (doc.getMinioKey() == null || doc.getMinioKey().isBlank()) {
            throw new BizException(404, "原始文件不存在");
        }
        return DocumentPreview.download(
            doc.getMinioKey(),
            doc.getFileName(),
            doc.getFileType(),
            minioService.download(doc.getMinioKey())
        );
    }

    public String previewTextDoc(String docId, int userAccessLevel) throws Exception {
        KnowledgeDoc doc = docMapper.selectById(docId);
        if (doc == null) throw new BizException(404, "文档不存在");
        if (!VisibilityPolicy.canView(doc.getAccessLevel(), userAccessLevel)) {
            throw new BizException(403, "无权预览该文档");
        }
        if (doc.getMinioKey() == null || doc.getMinioKey().isBlank()) {
            throw new BizException(404, "原始文件不存在");
        }

        try (var inputStream = minioService.download(doc.getMinioKey())) {
            String text = documentParser.parse(inputStream, doc.getFileType()).stream()
                .map(section -> section.heading() + "\n" + section.content())
                .filter(sectionText -> !sectionText.isBlank())
                .collect(Collectors.joining("\n\n"));
            if (text.length() > MAX_PREVIEW_TEXT_LENGTH) {
                return text.substring(0, MAX_PREVIEW_TEXT_LENGTH) + "\n\n[预览内容已截断]";
            }
            return text;
        }
    }

    // ── Search Test ──────────────────────────────────────────────────────────

    public SearchTestResultVO searchTest(String query, int userAccessLevel, int topK) throws Exception {
        // Dense retrieval
        float[] queryVec = embeddingService.embedOne(query);
        List<IDScore> denseHits = milvusService.search(queryVec, userAccessLevel, topK);

        // BM25 retrieval
        List<Map.Entry<String, Double>> bm25Hits = bm25Indexer.search(query, userAccessLevel, topK);

        // Merge results (simple union, prefer dense order)
        List<HitItem> hits = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (IDScore hit : denseHits) {
            String childId = hit.getStrID();
            if (seen.add(childId)) {
                HitItem item = buildHitItem(childId, hit.getScore(), "dense", userAccessLevel);
                if (item != null) hits.add(item);
            }
        }
        for (Map.Entry<String, Double> entry : bm25Hits) {
            String childId = entry.getKey();
            if (seen.add(childId)) {
                HitItem item = buildHitItem(childId, entry.getValue(), "bm25", userAccessLevel);
                if (item != null) hits.add(item);
            }
        }

        return SearchTestResultVO.builder()
            .query(query)
            .totalHits(hits.size())
            .hits(hits.stream().limit(topK).toList())
            .build();
    }

    private HitItem buildHitItem(String childId, double score, String source, int userAccessLevel) {
        ChildChunk child = childMapper.selectById(childId);
        if (child == null) {
            return null;
        }
        if (!VisibilityPolicy.canView(child.getAccessLevel(), userAccessLevel)) return null;
        return HitItem.builder()
            .childId(childId)
            .parentId(child.getParentId())
            .docTitle(child.getDocTitle())
            .headingPath(child.getHeadingPath())
            .content(child.getContent())
            .score(score)
            .pageStart(child.getPageStart())
            .source(source)
            .build();
    }

    private void clearIndexes(String docId) {
        milvusService.deleteByDocId(docId);
        bm25Indexer.deleteByDocId(docId);
        log.info("[DELETE_FLOW] doc={} step=bm25_delete", docId);
        int childRows = childMapper.delete(new LambdaQueryWrapper<ChildChunk>().eq(ChildChunk::getDocId, docId));
        int parentRows = parentMapper.delete(new LambdaQueryWrapper<ParentChunk>().eq(ParentChunk::getDocId, docId));
        log.info("[DELETE_FLOW] doc={} step=mysql_chunk_delete parentRows={} childRows={}",
            docId, parentRows, childRows);
    }

    private DocVO toDocVO(KnowledgeDoc doc) {
        DocVO vo = new DocVO();
        vo.setDocId(doc.getDocId());
        vo.setTitle(doc.getTitle());
        vo.setFileName(doc.getFileName());
        vo.setFileType(doc.getFileType());
        vo.setFileSize(doc.getFileSize());
        vo.setCategoryCode(doc.getCategoryCode());
        vo.setStatus(doc.getStatus());
        vo.setAccessLevel(doc.getAccessLevel());
        vo.setAccessLevelName(DocVO.accessLevelName(doc.getAccessLevel()));
        vo.setParentChunkCount(doc.getParentChunkCount());
        vo.setChildChunkCount(doc.getChildChunkCount());
        vo.setErrorMsg(doc.getErrorMsg());
        vo.setCreatedBy(doc.getCreatedBy());
        vo.setCreatedAt(doc.getCreatedAt());
        vo.setUpdatedAt(doc.getUpdatedAt());
        return vo;
    }
}
