-- DROP TABLE IF EXISTS schm_sp.application_details;

CREATE TABLE IF NOT EXISTS schm_sp.application_details
(
    application_id character varying COLLATE pg_catalog."default" NOT NULL,
    draft_reference_no character varying COLLATE pg_catalog."default" NOT NULL,
    reference_no character varying COLLATE pg_catalog."default",
    service_id integer NOT NULL,
    apply_date timestamp with time zone,
    beneficiary_name character varying COLLATE pg_catalog."default",
    due_date timestamp with time zone,
    status character varying COLLATE pg_catalog."default" NOT NULL,
    applied_location_id integer,
    applied_location_name character varying COLLATE pg_catalog."default",
    grievance_application_id character varying COLLATE pg_catalog."default",
    beneficiary_id bigint,
    tenant_id character varying COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT application_details_pkey PRIMARY KEY (application_id)
)


-- DROP TABLE IF EXISTS schm_sp.application_flow_status;

CREATE TABLE IF NOT EXISTS schm_sp.application_flow_status
(
    id character varying COLLATE pg_catalog."default" NOT NULL,
    application_id character varying COLLATE pg_catalog."default" NOT NULL,
    activity_type character varying COLLATE pg_catalog."default" NOT NULL,
    txn_id character varying COLLATE pg_catalog."default" NOT NULL,
    completed integer,
    form_id character varying COLLATE pg_catalog."default" NOT NULL,
    data_id character varying COLLATE pg_catalog."default",
    service_id integer,
    task_id character varying COLLATE pg_catalog."default",
    last_update timestamp with time zone,
    tenant_id character varying COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT application_flow_status_pkey PRIMARY KEY (id),
    CONSTRAINT application_flow_status_ukey UNIQUE (application_id, task_id, activity_type, completed, txn_id)
)


-- DROP TABLE IF EXISTS schm_sp.current_process;

CREATE TABLE IF NOT EXISTS schm_sp.current_process
(
    process_id character varying COLLATE pg_catalog."default" NOT NULL,
    application_id character varying COLLATE pg_catalog."default" NOT NULL,
    service_id integer NOT NULL,
    current_task character varying COLLATE pg_catalog."default" NOT NULL,
    current_task_name character varying COLLATE pg_catalog."default" NOT NULL,
    previous_task character varying COLLATE pg_catalog."default" NOT NULL,
    previous_task_name character varying COLLATE pg_catalog."default" NOT NULL,
    previous_process_id character varying COLLATE pg_catalog."default" NOT NULL,
    data_id character varying COLLATE pg_catalog."default",
    is_parallel character varying COLLATE pg_catalog."default",
    action_code integer,
    action_taken character varying COLLATE pg_catalog."default",
    action_on timestamp with time zone,
    initiated_on timestamp with time zone,
    base_service_id integer NOT NULL,
    user_id bigint,
    user_ip character varying COLLATE pg_catalog."default",
    tenant_id character varying COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT current_process_pkey PRIMARY KEY (process_id)
)


-- DROP TABLE IF EXISTS schm_sp.processing_transactions;

CREATE TABLE IF NOT EXISTS schm_sp.processing_transactions
(
    txn_id character varying COLLATE pg_catalog."default" NOT NULL,
    form_id character varying COLLATE pg_catalog."default" NOT NULL,
    service_id integer NOT NULL,
    task_id character varying COLLATE pg_catalog."default" NOT NULL,
    application_id character varying COLLATE pg_catalog."default",
    user_id bigint,
    user_ip character varying COLLATE pg_catalog."default",
    start_time timestamp with time zone,
    end_time timestamp with time zone,
    activity_type character varying COLLATE pg_catalog."default",
    tenant_id character varying COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT processing_transactions_pkey PRIMARY KEY (txn_id)
)


alter table schm_sp.application_flow_status add column service_id integer;
alter table schm_sp.application_flow_status add column task_id character varying;
alter table schm_sp.current_process add column base_service_id integer;
alter table schm_sp.application_details add column service_name character varying;
ALTER TABLE IF EXISTS schm_sp.current_process ADD COLUMN current_task_type integer;

CREATE TABLE IF NOT EXISTS schm_sp.timer_task_execution
(
    id bigserial,
    application_id character varying COLLATE pg_catalog."default",
    current_process_id character varying COLLATE pg_catalog."default",
    service_id integer,
    base_service_id integer,
    task_id character varying COLLATE pg_catalog."default",
    due_date timestamp with time zone,
    status character varying COLLATE pg_catalog."default",
    action_taken character varying COLLATE pg_catalog."default",
    created_on timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    executed_on timestamp with time zone,
    CONSTRAINT timer_task_execution_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS schm_sp.workflow_escalation
(
    id bigint NOT NULL DEFAULT nextval('schm_sp.workflow_escalation_id_seq'::regclass),
    application_id character varying COLLATE pg_catalog."default",
    service_id integer,
    current_process_id character varying COLLATE pg_catalog."default",
    task_id character varying COLLATE pg_catalog."default",
    execute_on timestamp with time zone,
    status character varying COLLATE pg_catalog."default",
    retry_count integer,
    action character varying COLLATE pg_catalog."default",
    mvel_expression character varying COLLATE pg_catalog."default",
    escalation_json character varying COLLATE pg_catalog."default",
    created_on timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    modified_on timestamp with time zone,
    CONSTRAINT workflow_escalation_pkey PRIMARY KEY (id)
);
CREATE TABLE IF NOT EXISTS schm_sp.workflow_webservice_execution
(
    execution_id bigserial,
    application_id character varying COLLATE pg_catalog."default",
    service_id integer,
    base_service_id integer,
    current_process_id character varying COLLATE pg_catalog."default",
    current_task_id character varying COLLATE pg_catalog."default",
    api_id character varying COLLATE pg_catalog."default",
    status character varying COLLATE pg_catalog."default",
    attempt_count integer,
    max_attempt integer,
    retry_interval integer,
    retry_interval_unit character varying COLLATE pg_catalog."default",
    next_retry_time timestamp with time zone,
    api_response character varying COLLATE pg_catalog."default",
    normalized_response character varying COLLATE pg_catalog."default",
    validation_token character varying COLLATE pg_catalog."default",
    form_data_id character varying COLLATE pg_catalog."default",
    error_message character varying COLLATE pg_catalog."default",
    created_on timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    modified_on timestamp with time zone,
    CONSTRAINT workflow_webservice_execution_pkey PRIMARY KEY (execution_id)
);