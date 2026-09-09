package com.serviceplus.form.validation.service;

import com.serviceplus.form.validation.dto.Applications;
import com.serviceplus.form.validation.dto.UserSessionObject;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

public interface IApplicationManagerService {

    Mono<List<Applications>> fetch(Long userId, String status, Integer offSet, List<String> refNos,
                                   LocalDateTime from, LocalDateTime to, UserSessionObject user, Map<String, Object> params,String dateColumn);
}
