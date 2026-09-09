package com.serviceplus.form.validation.service;

import com.serviceplus.form.validation.dto.ServiceMeta;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.List;

import static com.serviceplus.form.validation.utility.SnowflakeIdGenerator.createUniqueId;
import static com.serviceplus.form.validation.utility.Utility.getClientIpAddr;

@Service
public class TempTransactionLogService {

    @Autowired
    private RedisService redisService;

    public Mono<TempTransactionLogs> mergeTransactionLog(ServiceMeta services, UserSessionObject user, ServerHttpRequest request){
        TempTransactionLogs logs = new TempTransactionLogs();
        logs.setService(services);
        logs.setTxnId(createUniqueId());
        logs.setUserIp(getClientIpAddr(request));
        logs.setStartTime(new Date());
        logs.setUserId(user.getUserID());

        return redisService.add(logs,logs.getTxnId(),true,120).flatMap(flag -> {
            if(flag){
                return Mono.just(logs);
            }
            else {
                return Mono.empty();
            }
        });
    }

    public Mono<TempTransactionLogs> fetch(String txnId){
        Mono<Object> fetch = redisService.fetch(txnId, TempTransactionLogs.class);
        return fetch.flatMap(data -> {
                 TempTransactionLogs log = (TempTransactionLogs)  data;
                 return Mono.just(log);
        });

    }
}
