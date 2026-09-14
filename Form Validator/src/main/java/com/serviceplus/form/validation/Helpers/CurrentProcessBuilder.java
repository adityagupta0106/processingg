package com.serviceplus.form.validation.Helpers;

import static com.serviceplus.form.validation.utility.ApplicationConstants.*;
import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;
import static java.util.Objects.isNull;

import java.time.LocalDateTime;
import java.util.List;

import com.serviceplus.form.validation.dto.TaskAvailableOfficeLocation;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.enums.TaskType;
import org.springframework.stereotype.Component;

import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;
import com.serviceplus.form.validation.entity.CurrentProcess;
import com.serviceplus.form.validation.entity.ProcessingTxn;

@Component
public class CurrentProcessBuilder {

    public CurrentProcess buildBaseProcess(
            CurrentProcess currentActionProcess,
            ServiceProcessFlowDTO.Data.Nodes currentNode,
            ServiceProcessFlowDTO.Data.Nodes currentTask,
            ServiceMeta service,
            ApplicationDetails ad,
            UserSessionObject user,
            LocalDateTime now,
            List<TaskAvailableOfficeLocation> taskAvailableOfficeLocations,
            List<ServiceProcessFlowDTO.Data> wf,
            ProcessingTxn txn) {

        CurrentProcess cp = new CurrentProcess();

        cp.setPreviousProcessId(currentActionProcess.getProcessId());
        cp.setCurrentTask(currentTask.getId());
        cp.setCurrentTaskType(currentTask.getTaskType());
        cp.setCurrentTaskName(currentTask.getName());
        cp.setPreviousTask(currentNode.getId());
        cp.setPreviousTaskName(currentNode.getName());
        cp.setServiceId(service.getServiceId());
        cp.setApplicationId(ad.getApplicationId());
        cp.setTenantId(user.getTenantId());
        cp.setBaseServiceId(service.getBaseServiceId());
        cp.setFormId(currentTask.getFormId());
        cp.setIsPriority(ad.getIsPriority());

        if (TYPE_GATEWAY.equals(currentTask.getType())) {
            cp.setActionTaken("Y");
            cp.setActionOn(now);
            cp.setGateway(Boolean.TRUE);
        } else {
            cp.setInitiatedOn(now);
        }

        if(!isNull(cp.getCurrentTaskType()) && cp.getCurrentTaskType().equals(TaskType.APPLICANT_TASK.getType())){
            cp.setApplicantTask(Boolean.TRUE);
        }

        cp.setProcessId(createUniqueId());
        cp.setNewEntity(true);
        cp.setActionCode(FALLBACK_ACTION_NO);

        return cp;
    }

    public CurrentProcess buildGatewayNextProcess(
            CurrentProcess parent,
            ServiceProcessFlowDTO.Data.Nodes gatewayNode,
            ServiceProcessFlowDTO.Data.Nodes nextNode,
            ServiceMeta service,
            ApplicationDetails ad,
            UserSessionObject user,
            LocalDateTime now,
            List<TaskAvailableOfficeLocation> taskAvailableOfficeLocations,
            List<ServiceProcessFlowDTO.Data> wf,
            ProcessingTxn txn) {

        CurrentProcess cp = new CurrentProcess();

        cp.setPreviousProcessId(parent.getProcessId());
        cp.setCurrentTask(nextNode.getId());
        cp.setCurrentTaskType(nextNode.getTaskType());
        cp.setCurrentTaskName(nextNode.getName());
        cp.setPreviousTask(gatewayNode.getId());
        cp.setPreviousTaskName(gatewayNode.getName());
        cp.setServiceId(service.getServiceId());
        cp.setApplicationId(ad.getApplicationId());
        cp.setTenantId(user.getTenantId());
        cp.setBaseServiceId(service.getBaseServiceId());
        cp.setFormId(nextNode.getFormId());
        cp.setInitiatedOn(now);
        cp.setProcessId(createUniqueId());
        cp.setNewEntity(true);
        cp.setActionTaken("N");
        cp.setActionCode(FALLBACK_ACTION_NO);

        if(cp.getCurrentTaskType().equals(TaskType.APPLICANT_TASK.getType())){
            cp.setApplicantTask(Boolean.TRUE);
        }

        return cp;
    }
}