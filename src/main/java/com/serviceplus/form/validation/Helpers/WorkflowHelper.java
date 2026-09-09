package com.serviceplus.form.validation.Helpers;

import static com.serviceplus.form.validation.utility.ApplicationConstants.GATEWAY_BEHAVIOUR_EXCLUSIVE_CONVERGENT;
import static com.serviceplus.form.validation.utility.ApplicationConstants.GATEWAY_BEHAVIOUR_EXCLUSIVE_DIVERGENT;
import static com.serviceplus.form.validation.utility.ApplicationConstants.GATEWAY_BEHAVIOUR_INCLUSIVE_CONVERGENT;
import static com.serviceplus.form.validation.utility.ApplicationConstants.GATEWAY_BEHAVIOUR_INCLUSIVE_DIVERGENT;
import static com.serviceplus.form.validation.utility.ApplicationConstants.GATEWAY_BEHAVIOUR_PARALLEL_CONVERGENT;
import static com.serviceplus.form.validation.utility.ApplicationConstants.GATEWAY_BEHAVIOUR_PARALLEL_DIVERGENT;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.serviceplus.form.validation.dto.ServiceProcessFlowDTO;

@Component
public class WorkflowHelper {

    public ServiceProcessFlowDTO.Data fetchNode(List<ServiceProcessFlowDTO.Data> workflow, String taskId) {

        return workflow.stream()
                .filter(d -> d.getNode() != null)
                .filter(d -> taskId.equals(d.getNode().getId()))
                .findFirst()
                .orElse(null);
    }

    public boolean isDivergentGateway(String behaviour) {

        return GATEWAY_BEHAVIOUR_EXCLUSIVE_DIVERGENT.equals(behaviour)
                || GATEWAY_BEHAVIOUR_INCLUSIVE_DIVERGENT.equals(behaviour)
                || GATEWAY_BEHAVIOUR_PARALLEL_DIVERGENT.equals(behaviour);
    }

    public boolean isConvergentGateway(String behaviour) {

        return GATEWAY_BEHAVIOUR_EXCLUSIVE_CONVERGENT.equals(behaviour)
                || GATEWAY_BEHAVIOUR_INCLUSIVE_CONVERGENT.equals(behaviour)
                || GATEWAY_BEHAVIOUR_PARALLEL_CONVERGENT.equals(behaviour);
    }

    public List<String> extractNextNodeIds(List<ServiceProcessFlowDTO.Data.MappedTask> mappedTasks) {

        return mappedTasks.stream()
                .map(m -> m.getNode().getId())
                .filter(Objects::nonNull)
                .toList();
    }
}
