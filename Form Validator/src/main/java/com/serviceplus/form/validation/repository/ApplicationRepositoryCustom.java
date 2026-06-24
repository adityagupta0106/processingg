package com.serviceplus.form.validation.repository;

import com.serviceplus.form.validation.dto.ApplicationSearchRequest;
import com.serviceplus.form.validation.dto.Applications;
import com.serviceplus.form.validation.dto.ServerSidePaginationRecord;
import com.serviceplus.form.validation.dto.UserSessionObject;
import reactor.core.publisher.Mono;

public interface ApplicationRepositoryCustom {

    Mono<ServerSidePaginationRecord<Applications>> search(ApplicationSearchRequest request, UserSessionObject user);
}
