package com.serviceplus.form.validation.repository;

import com.netflix.appinfo.ApplicationInfoManager;
import com.serviceplus.form.validation.entity.ApplicationDocumentLogEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
public interface ApplicationDocumentLogRepository extends ReactiveCrudRepository<ApplicationDocumentLogEntity, String> {

    Mono<ApplicationDocumentLogEntity> findByApplicationIdAndTaskIdAndReferenceId(
            String applicationId,
            String taskId,
            String referenceId);

    Flux<ApplicationDocumentLogEntity> findByApplicationIdAndTaskId(
            String applicationId,
            String taskId);

    Flux<ApplicationDocumentLogEntity> findByApplicationId(
            String applicationId);

    Mono<ApplicationDocumentLogEntity> findByUploadId(
            String uploadId);

    Mono<ApplicationDocumentLogEntity> findByIdAndApplicationIdAndTaskIdAndReferenceId(
            String id,
            String applicationId,
            String taskId,
            String referenceId);

    Mono<ApplicationDocumentLogEntity> findByApplicationIdAndTxnIdAndTaskIdAndReferenceIdAndSourceType(
            String applicationId,
            String txnId,
            String taskId,
            String referenceId,
            String sourceType
    );

    Mono<ApplicationDocumentLogEntity>  findByApplicationIdAndTxnIdAndTaskIdAndSourceTypeAndIdIn(
            String applicationId,
            String txnId,
            String taskId,
            String fileUpload,
            List<String> documentIds);

    Mono<ApplicationDocumentLogEntity> findFirstByApplicationIdAndTaskIdAndReferenceIdAndSourceTypeOrderByCreatedOnDesc(String applicationId,String taskId, String referenceId, String systemGenerated);

    Mono<ApplicationDocumentLogEntity> findFirstByApplicationIdAndProcessIdAndReferenceIdAndSourceTypeOrderByCreatedOnDesc(String applicationId, String processId, String referenceId, String systemGenerated);

    Mono<ApplicationDocumentLogEntity> findFirstByApplicationIdAndProcessIdAndReferenceIdAndSourceTypeAndStatusOrderByCreatedOnDesc(String applicationId, String processId, String referenceId, String fileUpload, String status);

    Mono<ApplicationDocumentLogEntity> findFirstByApplicationIdAndTaskIdAndReferenceIdAndSourceTypeAndStatusOrderByCreatedOnDesc(String applicationId, String taskId, String referenceId, String fileUpload, String status);
}
