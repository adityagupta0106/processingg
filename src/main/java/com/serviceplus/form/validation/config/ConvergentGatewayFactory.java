package com.serviceplus.form.validation.config;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.serviceplus.form.validation.handlers.ConvergentGatewayHandler;

@Component
public class ConvergentGatewayFactory {

    private final Map<String, ConvergentGatewayHandler> handlers;

    public ConvergentGatewayFactory(
            List<ConvergentGatewayHandler> handlerList) {

        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(
                        this::getGatewayType,
                        Function.identity()
                ));
    }

    private String getGatewayType(ConvergentGatewayHandler handler) {
        if (handler instanceof ExclusiveConvergentGatewayHandler) {
            return "EC";
        }

        if (handler instanceof InclusiveConvergentGatewayHandler) {
            return "IC";
        }

        if (handler instanceof ParallelConvergentGatewayHandler) {
            return "PC";
        }

        throw new IllegalArgumentException(
                "Unsupported convergent gateway handler: "
                        + handler.getClass().getName());
    }

    public ConvergentGatewayHandler getHandler(String gatewayType) {

        ConvergentGatewayHandler handler = handlers.get(gatewayType.toUpperCase());

        if (handler == null) {
            throw new IllegalArgumentException("Unsupported convergent gateway type: "+ gatewayType);
        }

        return handler;
    }
}
