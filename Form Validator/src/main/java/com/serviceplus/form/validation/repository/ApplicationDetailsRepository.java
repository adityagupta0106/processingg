package com.serviceplus.form.validation.repository;

import com.serviceplus.form.validation.entity.ApplicationDetails;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationDetailsRepository extends ReactiveCrudRepository<ApplicationDetails, String> {
}
