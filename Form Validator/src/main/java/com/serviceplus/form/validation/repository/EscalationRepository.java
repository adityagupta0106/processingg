package com.serviceplus.form.validation.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import com.serviceplus.form.validation.entity.WorkflowEscalation;

@Repository
public interface EscalationRepository extends ReactiveCrudRepository<WorkflowEscalation, Long> {

	List<WorkflowEscalation> findByStatus(String status);

	@Query("""
		    SELECT e
		    FROM WorkflowEscalation e
		    WHERE e.status = :status
		      AND e.executeOn <= :currentDate
		    ORDER BY e.executeOn ASC
		    """)
		List<WorkflowEscalation> findPendingEscalations(
		        @Param("status") String status,
		        @Param("currentDate") Date currentDate);

}
