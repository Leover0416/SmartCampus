package com.simon.campus.service.ingest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.simon.campus.mapper.ChildChunkMapper;
import com.simon.campus.mapper.KnowledgeDocMapper;
import com.simon.campus.mapper.ParentChunkMapper;
import com.simon.campus.model.entity.KnowledgeDoc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("KnowledgeService - 重新索引")
class KnowledgeServiceReindexTest {

    @Test
    @DisplayName("清理旧索引后从 MinIO 原文件重新触发入库")
    void reindexClearsIndexesAndTriggersIngest() throws Exception {
        KnowledgeDocMapper docMapper = mock(KnowledgeDocMapper.class);
        ParentChunkMapper parentMapper = mock(ParentChunkMapper.class);
        ChildChunkMapper childMapper = mock(ChildChunkMapper.class);
        MinioService minioService = mock(MinioService.class);
        BM25Indexer bm25Indexer = mock(BM25Indexer.class);
        MilvusService milvusService = mock(MilvusService.class);
        IngestAsyncService ingestAsyncService = mock(IngestAsyncService.class);

        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setDocId("doc-001");
        doc.setTitle("图片 PDF");
        doc.setFileType("application/pdf");
        doc.setMinioKey("docs/doc-001.pdf");

        when(docMapper.selectById("doc-001")).thenReturn(doc);
        when(minioService.download("docs/doc-001.pdf"))
            .thenReturn(new ByteArrayInputStream("pdf-bytes".getBytes()));

        KnowledgeService service = new KnowledgeService(
            docMapper,
            null,
            parentMapper,
            childMapper,
            minioService,
            null,
            bm25Indexer,
            milvusService,
            ingestAsyncService,
            null
        );

        KnowledgeDoc result = service.reindexDoc("doc-001");

        assertThat(result.getStatus()).isEqualTo("PROCESSING");
        assertThat(result.getChildChunkCount()).isZero();
        verify(milvusService).deleteByDocId("doc-001");
        verify(bm25Indexer).deleteByDocId("doc-001");
        verify(childMapper).delete(any(LambdaQueryWrapper.class));
        verify(parentMapper).delete(any(LambdaQueryWrapper.class));
        verify(docMapper).updateProcessResult("doc-001", "PROCESSING", 0, 0, null);
        verify(ingestAsyncService).ingest(eq("doc-001"), aryEq("pdf-bytes".getBytes()), eq("application/pdf"));
    }

    @Test
    @DisplayName("删除文档时同步清理 MySQL、Milvus、BM25 和原始文件")
    void deleteRemovesIndexesAndOriginalFile() throws Exception {
        KnowledgeDocMapper docMapper = mock(KnowledgeDocMapper.class);
        ParentChunkMapper parentMapper = mock(ParentChunkMapper.class);
        ChildChunkMapper childMapper = mock(ChildChunkMapper.class);
        MinioService minioService = mock(MinioService.class);
        BM25Indexer bm25Indexer = mock(BM25Indexer.class);
        MilvusService milvusService = mock(MilvusService.class);

        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setDocId("doc-002");
        doc.setTitle("待删除 PDF");
        doc.setMinioKey("docs/doc-002.pdf");
        when(docMapper.selectById("doc-002")).thenReturn(doc);
        when(parentMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(2);
        when(childMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(7);
        when(docMapper.deleteById("doc-002")).thenReturn(1);

        KnowledgeService service = new KnowledgeService(
            docMapper,
            null,
            parentMapper,
            childMapper,
            minioService,
            null,
            bm25Indexer,
            milvusService,
            null,
            null
        );

        service.deleteDoc("doc-002");

        verify(milvusService).deleteByDocId("doc-002");
        verify(bm25Indexer).deleteByDocId("doc-002");
        verify(childMapper).delete(any(LambdaQueryWrapper.class));
        verify(parentMapper).delete(any(LambdaQueryWrapper.class));
        verify(minioService).deleteStrict("docs/doc-002.pdf");
        verify(docMapper).deleteById("doc-002");
    }
}
