package com.serviceplus.form.validation.repository;

import com.serviceplus.form.validation.entity.CurrentProcess;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CurrentProcessRepository extends ReactiveCrudRepository<CurrentProcess, String> {
}
