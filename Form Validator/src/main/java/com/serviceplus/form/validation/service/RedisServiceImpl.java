package com.serviceplus.form.validation.service;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.entity.TempTransactionLogs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static com.serviceplus.form.validation.utility.Utility.entityToString;
import static com.serviceplus.form.validation.utility.Utility.stringToEntity;

@Service
@Primary
public class RedisServiceImpl implements  RedisService{

    @Autowired
    private ReactiveRedisTemplate<String, String> template;

    @Value("${new.application.form.transaction.redis.timeout.minutes}")
    private Long NEW_APPLICATION_FORM_TRANSACTION_REDIS_TIMEOUT;

    @Override
    public Mono<Object> fetch(String value,Class<?> obj) {

        try {
            Mono<String> stringMono = template.opsForValue().get(value);

            //Dump data
            //RedisConnection connection = template.getConnectionFactory().getConnection();
            //connection.save();
            //Dump data */

            //RedisServerCommands serverCommands = connection.serverCommands();
            //serverCommands.save();
            return stringMono.flatMap(data -> {
                Object s = stringToEntity(data, obj);
                return Mono.just(s);
            });

        } catch (Exception e) {
            e.printStackTrace();
            return Mono.error(new SPRuntimeError("REDIS _ERR", HttpStatus.INTERNAL_SERVER_ERROR));
        }

    }

    @Override
    public Mono<Boolean> add(Object ent, String key,boolean durationRequired) {
        String finalJson;
        try {
            finalJson = entityToString(ent);
            if(durationRequired){
                return template.opsForValue().set(key, finalJson, Duration.ofMinutes(NEW_APPLICATION_FORM_TRANSACTION_REDIS_TIMEOUT));
            }
            else{
                return template.opsForValue().set(key, finalJson);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return Mono.just(false);
        }
    }

    @Override
    public Mono<Void> remove(String key) {
        return template.opsForValue()
                .getAndDelete(key)
                .then()
                .doOnError(e -> e.printStackTrace());
    }


}

