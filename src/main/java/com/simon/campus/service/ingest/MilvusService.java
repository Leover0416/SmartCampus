package com.simon.campus.service.ingest;

import com.simon.campus.model.entity.ChildChunk;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.InsertParam.Field;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MilvusService {

    private final MilvusServiceClient milvusServiceClient;

    @Value("${milvus.collection-name}")
    private String collectionName;

    private static final List<String> SEARCH_OUTPUT_FIELDS =
        Arrays.asList("child_id", "parent_id", "doc_id", "doc_title", "category", "access_level", "page_start");

    public void insertBatch(List<ChildChunk> chunks, List<float[]> embeddings) {
        if (chunks.isEmpty()) return;

        List<String> childIds   = new ArrayList<>();
        List<String> parentIds  = new ArrayList<>();
        List<String> docIds     = new ArrayList<>();
        List<String> docTitles  = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        List<Integer> accessLevels = new ArrayList<>();
        List<Integer> pageStarts   = new ArrayList<>();
        List<List<Float>> vectors  = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            ChildChunk c = chunks.get(i);
            childIds.add(c.getChildId());
            parentIds.add(c.getParentId());
            docIds.add(c.getDocId());
            docTitles.add(truncate(c.getDocTitle(), 255));
            categories.add(c.getCategory() != null ? c.getCategory() : "");
            accessLevels.add(c.getAccessLevel());
            pageStarts.add(c.getPageStart() != null ? c.getPageStart() : 0);
            vectors.add(toFloatList(embeddings.get(i)));
        }

        List<Field> fields = Arrays.asList(
            new Field("child_id",     childIds),
            new Field("parent_id",    parentIds),
            new Field("doc_id",       docIds),
            new Field("doc_title",    docTitles),
            new Field("category",     categories),
            new Field("access_level", accessLevels),
            new Field("page_start",   pageStarts),
            new Field("embedding",    vectors)
        );

        InsertParam insertParam = InsertParam.newBuilder()
            .withCollectionName(collectionName)
            .withFields(fields)
            .build();

        R<MutationResult> result = milvusServiceClient.insert(insertParam);
        if (result.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus insert failed: " + result.getMessage());
        }
        log.debug("Inserted {} vectors into Milvus", chunks.size());
    }

    /**
     * Dense vector search with visibility filter.
     * 0=all, 1/2=teacher visible, 3=student visible.
     */
    public List<SearchResultsWrapper.IDScore> search(float[] queryVector, int userAccessLevel, int topK) {
        String filter = buildAccessFilter(userAccessLevel);

        SearchParam searchParam = SearchParam.newBuilder()
            .withCollectionName(collectionName)
            .withMetricType(io.milvus.param.MetricType.IP)
            .withOutFields(SEARCH_OUTPUT_FIELDS)
            .withTopK(topK)
            .withVectors(Collections.singletonList(toFloatList(queryVector)))
            .withVectorFieldName("embedding")
            .withExpr(filter)
            .withParams("{\"ef\":128}")
            .build();

        R<SearchResults> result = milvusServiceClient.search(searchParam);
        if (result.getStatus() != R.Status.Success.getCode()) {
            log.error("Milvus search failed: {}", result.getMessage());
            return Collections.emptyList();
        }

        SearchResultsWrapper wrapper = new SearchResultsWrapper(result.getData().getResults());
        return wrapper.getIDScore(0);
    }

    public void deleteByDocId(String docId) {
        DeleteParam deleteParam = DeleteParam.newBuilder()
            .withCollectionName(collectionName)
            .withExpr("doc_id == \"" + docId + "\"")
            .build();
        R<MutationResult> result = milvusServiceClient.delete(deleteParam);
        if (result.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus delete failed: " + result.getMessage());
        }
        log.info("[DELETE_FLOW] step=milvus_delete collection={} docId={}", collectionName, docId);
    }

    private String buildAccessFilter(int userAccessLevel) {
        return "access_level in " + VisibilityPolicy.visibleLevelsForViewer(userAccessLevel);
    }

    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) list.add(f);
        return list;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
