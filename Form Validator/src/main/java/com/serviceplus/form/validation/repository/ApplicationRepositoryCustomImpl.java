package com.serviceplus.form.validation.repository;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.ApplicationSearchRequest;
import com.serviceplus.form.validation.dto.Applications;
import com.serviceplus.form.validation.dto.ServerSidePaginationRecord;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.List;

import static com.serviceplus.form.validation.utility.Utility.isEmpty;

@Repository
public class ApplicationRepositoryCustomImpl implements ApplicationRepositoryCustom {

    private static final Logger log = LogManager.getLogger(ApplicationRepositoryCustomImpl.class);

    private final R2dbcEntityTemplate entityTemplate;

    private static final int MAX_PAGE_SIZE = 50;

    @Autowired
    public ApplicationRepositoryCustomImpl(R2dbcEntityTemplate entityTemplate) {
        this.entityTemplate = entityTemplate;
    }

    @Override
    public Mono<ServerSidePaginationRecord<Applications>> search(ApplicationSearchRequest request, UserSessionObject user) {

        log.info(
                "Application search request state={}, serviceId={}, refNo={}, page={}, size={}, tenantId={}, userId={}",
                request.getState(),
                request.getServiceId(),
                request.getApplicationRefNo(),
                request.getPage(),
                request.getSize(),
                user.getTenantId(),
                user.getUserID()
        );

        Criteria criteria = Criteria.where("tenant_id").is(user.getTenantId()).and("beneficiary_id").is(user.getUserID());

        if (request.getState() != null) {

            switch (request.getState().toUpperCase()) {

                case "DRAFT" -> criteria = criteria.and("status").is("S");

                case "DELIVERED" -> criteria = criteria.and("status").is("D");

                case "REJECTED" -> criteria = criteria.and("status").is("R");

                case "DISPOSED" -> criteria = criteria.and("status").in("R","D");

                default -> criteria = criteria.and("status").in("F","I");

            }
        }

        if (request.getServiceId() != null) {
            criteria = criteria.and("service_id").is(request.getServiceId());
        }

        if (!isEmpty(request.getApplicationRefNo())) {
            criteria = criteria.and("reference_no").like("%" + request.getApplicationRefNo() + "%");
        }

        request.setSize(request.getSize() > MAX_PAGE_SIZE ? MAX_PAGE_SIZE : request.getSize());
        request.setPage(request.getPage() < 0 ? 0 : request.getPage());

        log.info("Executing application search page={} size={}", request.getPage(), request.getSize());

        Query query = Query.query(criteria)
                .sort(Sort.by(Sort.Direction.DESC, "apply_date"))
                .limit(request.getSize())
                .offset((long) request.getPage() * request.getSize());

        Mono<List<ApplicationDetails>> dataMono = entityTemplate.select(query, ApplicationDetails.class).collectList();

        Mono<Long> countMono = entityTemplate.count(Query.query(criteria), ApplicationDetails.class);

        return Mono.zip(dataMono, countMono).map(tuple -> {

            List<ApplicationDetails> entities = tuple.getT1();

            Long total = tuple.getT2();

            log.info("Application search completed recordsReturned={} totalRecords={}", entities.size(), total);

            ServerSidePaginationRecord<Applications> response = new ServerSidePaginationRecord<>();

            response.setData(entities.stream().map(this::convert).toList());
            response.setCurrentPage(request.getPage());
            response.setPageSize(request.getSize());
            response.setTotalRecords(total);
            response.setTotalPages((int) Math.ceil((double) total / request.getSize()));

            return response;
        })
        .onErrorResume(ex ->{
            log.error("Error while searching applications", ex);
            return Mono.error(new SPRuntimeError("Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR,""));
        });
    }

    private Applications convert(ApplicationDetails entity) {

        Applications dto = new Applications();

        dto.setApplicationId(entity.getApplicationId());
        dto.setServiceId(entity.getServiceId());
        dto.setApplicationRefNo(entity.getReferenceNo());
        dto.setStatus(entity.getStatus());
        dto.setServiceName(entity.getServiceName());
        dto.setApplyDate(entity.getApplyDate());

        return dto;
    }
}
