package com.simon.campus.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.*;
import io.milvus.param.collection.*;
import io.milvus.param.index.CreateIndexParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
@Slf4j
public class MilvusConfig {

    @Value("${milvus.host}")
    private String host;

    @Value("${milvus.port}")
    private int port;

    @Value("${milvus.collection-name}")
    private String collectionName;

    private static final int DIMENSION = 1024;

    @Bean
    public MilvusServiceClient milvusServiceClient() {
        ConnectParam connectParam = ConnectParam.newBuilder()
            .withHost(host)
            .withPort(port)
            .build();
        MilvusServiceClient client = new MilvusServiceClient(connectParam);
        try {
            initCollection(client);
        } catch (Exception e) {
            log.warn("Milvus collection init failed (is Milvus running?): {}", e.getMessage());
        }
        return client;
    }

    private void initCollection(MilvusServiceClient client) {
        R<Boolean> hasResp = client.hasCollection(
            HasCollectionParam.newBuilder().withCollectionName(collectionName).build()
        );
        if (Boolean.TRUE.equals(hasResp.getData())) {
            log.info("Milvus collection '{}' already exists", collectionName);
            return;
        }

        List<FieldType> fields = Arrays.asList(
            FieldType.newBuilder()
                .withName("child_id")
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build(),
            FieldType.newBuilder().withName("parent_id").withDataType(DataType.VarChar).withMaxLength(64).build(),
            FieldType.newBuilder().withName("doc_id").withDataType(DataType.VarChar).withMaxLength(36).build(),
            FieldType.newBuilder().withName("doc_title").withDataType(DataType.VarChar).withMaxLength(256).build(),
            FieldType.newBuilder().withName("category").withDataType(DataType.VarChar).withMaxLength(64).build(),
            // 0=全部可见, 1/2=教师可见（兼容旧数据）, 3=学生可见
            FieldType.newBuilder().withName("access_level").withDataType(DataType.Int32).build(),
            FieldType.newBuilder().withName("page_start").withDataType(DataType.Int32).build(),
            FieldType.newBuilder()
                .withName("embedding")
                .withDataType(DataType.FloatVector)
                .withDimension(DIMENSION)
                .build()
        );

        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
            .withCollectionName(collectionName)
            .withDescription("SmartCampus child chunk vectors")
            .withFieldTypes(fields)
            .build();
        client.createCollection(createParam);

        // Create HNSW index on embedding field
        CreateIndexParam indexParam = CreateIndexParam.newBuilder()
            .withCollectionName(collectionName)
            .withFieldName("embedding")
            .withIndexType(IndexType.HNSW)
            .withMetricType(MetricType.IP)
            .withExtraParam("{\"M\":16,\"efConstruction\":256}")
            .build();
        client.createIndex(indexParam);

        // Load collection into memory
        client.loadCollection(
            LoadCollectionParam.newBuilder().withCollectionName(collectionName).build()
        );

        log.info("Milvus collection '{}' created and loaded", collectionName);
    }
}
