package com.serviceplus.form.validation.service;

import com.serviceplus.form.validation.ExceptionHandler.SPRuntimeError;
import com.serviceplus.form.validation.dto.Applications;
import com.serviceplus.form.validation.dto.UserSessionObject;
import com.serviceplus.form.validation.entity.ApplicationDetails;
import com.serviceplus.form.validation.entity.FilterValue;
import com.serviceplus.form.validation.repository.ApplicationDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.serviceplus.form.validation.utility.ApplicationConstants.APPLICATION_STATUS_DRAFT;

@Service
public class ApplicationManagerService implements IApplicationManagerService{

    @Autowired
    private TransactionalDBExecutor dbExecutor;

    private final static Integer QUERY_LIMIT = 50;

    @Override
    @SuppressWarnings("unchecked")
    public Mono<List<Applications>> fetch(Integer userId, String status, Integer offSet, List<String> refNos,
                                          LocalDateTime from, LocalDateTime to, UserSessionObject user, Map<String, Object> params,String dateColumn) {


        Mono<?> appList = dbExecutor.fetchSingleEntity(user.getTenantId(), offSet, QUERY_LIMIT, from, to, params
                , dateColumn, ApplicationDetails.class
                , Sort.Direction.DESC, "apply_date");

        return appList
                .flatMap(t -> {
                    List<ApplicationDetails> list = (List<ApplicationDetails>) t;

                    if(list.isEmpty()){
                        return Mono.error(new SPRuntimeError("No record found or invalid request",HttpStatus.BAD_REQUEST,null));
                    }

                    List<Applications> finalList = new ArrayList<>();
                    DateTimeFormatter formatter =  DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a");
                    list.forEach(item -> {
                            Applications app = new Applications();
                            app.setApplicationId(item.getApplicationId());
                            app.setServiceId(item.getServiceId());
                            app.setApplyDate(item.getApplyDate().format(formatter));
                            if(APPLICATION_STATUS_DRAFT.equals(status)) {
                                app.setDraftRefNo(item.getDraftReferenceNo());
                            }
                            else{
                                app.setStatus(item.getStatus());
                                app.setApplicationRefNo(item.getReferenceNo());
                            }
                            finalList.add(app);
                    });

                    return Mono.just(finalList);
                });
    }
}
