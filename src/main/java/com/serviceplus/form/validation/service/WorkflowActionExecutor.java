package com.serviceplus.form.validation.service;

import java.util.List;
import java.util.Map;

import com.serviceplus.form.validation.dto.InboxKafka;
import com.serviceplus.form.validation.dto.ServiceJSONDTO;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO.TaskRelationDTO;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.entity.ProcessingTxn;

import reactor.core.publisher.Mono;

public interface WorkflowActionExecutor {

    Mono<InboxKafka> execute(
            ApplicationDetails application,
            CurrentProcess currentProcess,
            String action,
            Map<String, Map<String, List<String>>> holderMap,
            List<String> nextNodeList,
            ServiceJSONDTO serviceJson,
            ProcessingTxn txn,
            UserSessionObject user,
            ServiceMeta serviceMeta,
            Map<String,TaskRelationDTO> taskRelationMap);
}
