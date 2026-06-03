package com.simon.campus.service.ingest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.simon.campus.mapper.ChildChunkMapper;
import com.simon.campus.mapper.KnowledgeDocMapper;
import com.simon.campus.mapper.ParentChunkMapper;
import com.simon.campus.model.entity.ChildChunk;
import com.simon.campus.model.entity.KnowledgeDoc;
import com.simon.campus.model.entity.ParentChunk;
import com.simon.campus.service.ingest.DocumentParser.ParsedSection;
import com.simon.campus.service.ingest.ParentChildChunkSplitter.SplitResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestAsyncService {

    private final KnowledgeDocMapper docMapper;
    private final ParentChunkMapper  parentMapper;
    private final ChildChunkMapper   childMapper;
    private final DocumentParser     documentParser;
    private final ParentChildChunkSplitter splitter;
    private final EmbeddingService   embeddingService;
    private final BM25Indexer        bm25Indexer;
    private final MilvusService      milvusService;

    private static final int MILVUS_BATCH = 20;

    @Async("ingestExecutor")
    public void ingest(String docId, byte[] fileBytes, String contentType) {
        try {
            log.info("Ingest started for doc {}", docId);
            KnowledgeDoc doc = docMapper.selectById(docId);
            log.info("[INGEST_FLOW] doc={} step=start title={} contentType={} bytes={}",
                docId, doc != null ? doc.getTitle() : "", contentType, fileBytes != null ? fileBytes.length : 0);

            List<ParsedSection> sections;
            try (ByteArrayInputStream is = new ByteArrayInputStream(fileBytes)) {
                sections = documentParser.parseForIngest(is, contentType);
            }
            log.info("[INGEST_FLOW] doc={} step=parse sections={}", docId, sections.size());
            if (sections.isEmpty()) {
                throw new IllegalStateException("文档未解析出可入库内容，请确认文件是否包含可识别文本或已配置视觉解析能力");
            }

            SplitResult split = splitter.split(docId, doc.getTitle(), doc.getAccessLevel(), sections);
            List<ParentChunk> parents  = split.parents();
            List<ChildChunk>  children = split.children();
            log.info("[INGEST_FLOW] doc={} step=split parents={} children={}",
                docId, parents.size(), children.size());

            children.forEach(c -> c.setCategory(doc.getCategoryCode()));
            parents.forEach(p -> p.setCategory(doc.getCategoryCode()));

            if (!parents.isEmpty()) parentMapper.insertBatch(parents);
            if (!children.isEmpty()) childMapper.insertBatch(children);
            log.info("[INGEST_FLOW] doc={} step=mysql_insert parents={} children={}",
                docId, parents.size(), children.size());

            List<String> childIds   = children.stream().map(ChildChunk::getChildId).toList();
            List<String> childTexts = children.stream().map(ChildChunk::getContent).toList();
            bm25Indexer.indexChildren(docId, childIds, childTexts);
            log.info("[INGEST_FLOW] doc={} step=bm25_index children={}", docId, childIds.size());

            for (int i = 0; i < children.size(); i += MILVUS_BATCH) {
                List<ChildChunk> batch = children.subList(i, Math.min(i + MILVUS_BATCH, children.size()));
                List<String> texts = batch.stream().map(ChildChunk::getContent).toList();
                List<float[]> embeddings = embeddingService.embedBatch(texts);
                milvusService.insertBatch(batch, embeddings);
                log.info("[INGEST_FLOW] doc={} step=milvus_insert batchStart={} batchSize={}",
                    docId, i, batch.size());
            }

            docMapper.updateProcessResult(docId, "READY", parents.size(), children.size(), null);
            log.info("Ingest completed for doc {} — {} parents, {} children", docId, parents.size(), children.size());

        } catch (Throwable e) {
            if (e instanceof VirtualMachineError virtualMachineError) {
                throw virtualMachineError;
            }
            if (e instanceof ThreadDeath threadDeath) {
                throw threadDeath;
            }
            log.error("Ingest failed for doc {}: {}", docId, e.getMessage(), e);
            try {
                docMapper.updateProcessResult(docId, "FAILED", 0, 0, IngestErrorMessage.from(e));
            } catch (Exception updateError) {
                log.error("Failed to mark ingest doc {} as FAILED: {}", docId, updateError.getMessage(), updateError);
            }
        }
    }
}
