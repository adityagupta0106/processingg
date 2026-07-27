package com.serviceplus.form.validation.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.serviceplus.form.validation.Helpers.CurrentProcessBuilder;
import com.serviceplus.form.validation.dto.InboxKafka;
import com.serviceplus.form.validation.dto.ServiceJSONDTO;
import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;
import com.serviceplus.form.validation.dto.TaskAvailableOfficeLocation;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.entity.ProcessingTxn;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import static com.serviceplus.form.validation.utility.ApplicationConstants.TYPE_GATEWAY;
@Service
public class WorkflowActionExecutorImpl implements WorkflowActionExecutor {

	
	
	private final CurrentProcessBuilder currentProcessBuilder;
	private final GatewayService gatewayService;
	private final ApplicationGenerationService applicationGenerationService;
	
	public WorkflowActionExecutorImpl(TaskAssignmentService taskAssignmentService,
			CurrentProcessBuilder currentProcessBuilder, GatewayService gatewayService,
			ApplicationGenerationService applicationGenerationService) {
		this.currentProcessBuilder = currentProcessBuilder;
		this.gatewayService = gatewayService;
		this.applicationGenerationService = applicationGenerationService;
	}

	@Override
	public Mono<InboxKafka> execute(
	        ApplicationDetails application,
	        CurrentProcess currentProcess,
	        String action,
	        Map<String, Map<String, List<String>>> taskLocationUserHolderMap,
	        List<String> nextNodeList,
	        ServiceJSONDTO serviceJson,
	        ProcessingTxn txn,
	        UserSessionObject user,
	        ServiceMeta serviceMeta)  {

	    currentProcess.setActionTaken("Y");
	    currentProcess.setActionOn(LocalDateTime.now());
	    currentProcess.setUserId(0l);
	    currentProcess.setDataId(null);
	    currentProcess.setFormId(null);

	    List<ServiceProcessFlowDTO.Data> wf = serviceJson.getProcessFlowMap().getData();

	    ServiceProcessFlowDTO.Data currentData = serviceJson.getProcessFlowMap().getData().stream()
                .filter(data -> data.getNode().getId().equals(currentProcess.getCurrentTask()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "Workflow node not found : " + currentProcess.getCurrentTask()));

	    ServiceProcessFlowDTO.Data.Nodes currentNode = currentData.getNode();

	    LocalDateTime now = LocalDateTime.now();

	    List<TaskAvailableOfficeLocation> officeLocations = new ArrayList<>();
	    List<CurrentProcess> processList = new ArrayList<>();
	    Map<String, Date> timerDueDate=new HashMap();
	    return Flux.fromIterable(currentData.getMappedTasks())
	            .filter(mappedTask -> nextNodeList.contains(mappedTask.getNode().getId()))
	            .concatMap(mappedTask -> {
	                ServiceProcessFlowDTO.Data.Nodes next = mappedTask.getNode();

	                CurrentProcess baseProcess =
	                        currentProcessBuilder.buildBaseProcess(
	                                currentProcess,
	                                currentNode,
	                                next,
	                                serviceMeta,
	                                application,
	                                user,
	                                now,
	                                officeLocations,
	                                wf,
	                                txn);

	                if (TYPE_GATEWAY.equals(next.getType())) {

	                    return gatewayService.processGateway(
	                            currentNode,
	                            next,
	                            wf,
	                            serviceMeta,
	                            application,
	                            txn,
	                            user,
	                            now,
	                            currentProcess,
	                            baseProcess,
	                            officeLocations,
	                            taskLocationUserHolderMap,
	                            null,timerDueDate);
	                }

	                return Mono.just(baseProcess);
	            })

	            .collectList()

	            .flatMap(nextProcesses -> {

	                nextProcesses.add(currentProcess);

	                InboxKafka inboxKafka = new InboxKafka();

	                inboxKafka.setProcessList(nextProcesses);
	                inboxKafka.setOfficeDetails(officeLocations);
	                inboxKafka.setServiceName(serviceMeta.getServiceName());
	                inboxKafka.setAppliedBy(application.getBeneficiaryId());
	                inboxKafka.setBeneficiaryName(application.getBeneficiaryName());
	                inboxKafka.setApplyDate(application.getApplyDate());
	                inboxKafka.setLoggedInUserId(user.getUserID());
	                inboxKafka.setLoggedInUserLocation(user.getLocationId());

	                return applicationGenerationService.persistWorkflow(application, inboxKafka, txn, user)
	                        .doOnSuccess(v ->
	                        applicationGenerationService.sendToInboxService(inboxKafka, application, serviceMeta))
	                        .thenReturn(inboxKafka);
	            });
	}

}
