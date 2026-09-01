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

-------------------NEXT-------------------

CREATE TABLE schm_sp.application_document_log
(
    id                  character varying PRIMARY KEY,
    application_id      character varying NOT NULL,
    txn_id              character varying NOT NULL,
	process_id              character varying,
    service_id          integer NOT NULL,
    task_id             character varying NOT NULL,
    reference_id        character varying NOT NULL,
    document_name       character varying,
    source_type         character varying,
    upload_id           character varying,
    status              character varying,
    created_by          bigint,
    created_on          timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE schm_sp.application_document_merge
(
    id                      character varying PRIMARY KEY,
    application_id          character varying NOT NULL,
    txn_id              character varying NOT NULL,
	process_id              character varying,
    task_id                 character varying NOT NULL,
    reference_id            character varying NOT NULL,
    merged_upload_id        character varying,
    merged_preview_url      character varying,
    signed_upload_id        character varying,
    status                  character varying,
    created_by              bigint,
    created_on              timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              integer,
    updated_on              timestamp with time zone
);


CREATE TABLE schm_sp.application_document_submission
(
    id                  character varying PRIMARY KEY,
    txn_id              character varying NOT NULL,
	process_id              character varying,
    application_id      character varying NOT NULL,
    service_id          integer NOT NULL,
    task_id             character varying NOT NULL,
    reference_id        character varying NOT NULL,
    document_name       character varying,
    upload_id           character varying,
    signed_upload_id    character varying,
    merged             boolean DEFAULT false,
    status              character varying,
    created_by          bigint,
    created_on          timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);


alter table schm_sp.application_document_log add column tenant_id character varying;
alter table schm_sp.application_document_merge add column tenant_id character varying;
alter table schm_sp.application_document_submission add column tenant_id character varying;

    
ALTER TABLE IF EXISTS schm_sp.timer_task_execution   ALTER COLUMN id type character varying ;
ALTER TABLE IF EXISTS schm_sp.workflow_escalation   ALTER COLUMN id type character varying ;
ALTER TABLE IF EXISTS schm_sp.workflow_webservice_execution   ALTER COLUMN execution_id type character varying ;

ALTER TABLE IF EXISTS schm_sp.application_details ADD COLUMN is_priority boolean  NOT NULL DEFAULT false;


----------------------------- AUA------------------

-- Table: schm_sp.aua_providers

-- DROP TABLE IF EXISTS schm_sp.aua_providers;

CREATE TABLE IF NOT EXISTS schm_sp.aua_providers
(
    id bigserial,
    provider_name character varying COLLATE pg_catalog."default" NOT NULL,
    description character varying COLLATE pg_catalog."default",
    active boolean NOT NULL DEFAULT true,
    cr_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    up_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id character varying COLLATE pg_catalog."default",
    CONSTRAINT aua_providers_pkey PRIMARY KEY (id),
    CONSTRAINT uk_aua_providers_name UNIQUE (provider_name)
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS schm_sp.aua_providers
    OWNER to postgres;



-- Table: schm_sp.aua_api_definitions

-- DROP TABLE IF EXISTS schm_sp.aua_api_definitions;

CREATE TABLE IF NOT EXISTS schm_sp.aua_api_definitions
(
    id bigserial,
    provider_id bigint NOT NULL,
    api_code character varying COLLATE pg_catalog."default" NOT NULL,
    api_name character varying COLLATE pg_catalog."default" NOT NULL,
    operation_type character varying COLLATE pg_catalog."default" NOT NULL,
    api_version character varying COLLATE pg_catalog."default",
    protocol character varying COLLATE pg_catalog."default" NOT NULL DEFAULT 'HTTPS'::character varying,
    http_method character varying COLLATE pg_catalog."default" NOT NULL DEFAULT 'POST'::character varying,
    endpoint character varying COLLATE pg_catalog."default",
    description character varying COLLATE pg_catalog."default",
    active boolean NOT NULL DEFAULT true,
    cr_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    up_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id character varying COLLATE pg_catalog."default",
    xml_namespace character varying COLLATE pg_catalog."default",
    xml_namespace_version character varying COLLATE pg_catalog."default",
    CONSTRAINT aua_api_definitions_pkey PRIMARY KEY (id),
    CONSTRAINT uk_aua_api_definition UNIQUE (provider_id, api_code, api_version),
    CONSTRAINT fk_aua_api_def_provider FOREIGN KEY (provider_id)
        REFERENCES schm_sp.aua_providers (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS schm_sp.aua_api_definitions
    OWNER to postgres;



-- Table: schm_sp.aua_api_messages

-- DROP TABLE IF EXISTS schm_sp.aua_api_messages;

CREATE TABLE IF NOT EXISTS schm_sp.aua_api_messages
(
    id bigserial,
    api_definition_id bigint NOT NULL,
    message_type character varying COLLATE pg_catalog."default" NOT NULL,
    root_element character varying COLLATE pg_catalog."default" NOT NULL,
    xsd_content text COLLATE pg_catalog."default",
    xsd_version character varying COLLATE pg_catalog."default",
    xsd_hash character varying COLLATE pg_catalog."default",
    description character varying COLLATE pg_catalog."default",
    active boolean NOT NULL DEFAULT true,
    cr_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    up_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id character varying COLLATE pg_catalog."default",
    CONSTRAINT aua_api_messages_pkey PRIMARY KEY (id),
    CONSTRAINT uk_aua_api_message UNIQUE (api_definition_id, message_type),
    CONSTRAINT fk_aua_api_message_definition FOREIGN KEY (api_definition_id)
        REFERENCES schm_sp.aua_api_definitions (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS schm_sp.aua_api_messages
    OWNER to postgres;


-- Table: schm_sp.aua_api_fields

-- DROP TABLE IF EXISTS schm_sp.aua_api_fields;

CREATE TABLE IF NOT EXISTS schm_sp.aua_api_fields
(
    id bigserial,
    message_id bigint NOT NULL,
    field_code character varying COLLATE pg_catalog."default" NOT NULL,
    field_name character varying COLLATE pg_catalog."default" NOT NULL,
    xpath character varying COLLATE pg_catalog."default" NOT NULL,
    data_type character varying COLLATE pg_catalog."default" NOT NULL,
    field_type character varying COLLATE pg_catalog."default" NOT NULL,
    required boolean NOT NULL DEFAULT false,
    multiple boolean NOT NULL DEFAULT false,
    sensitive boolean NOT NULL DEFAULT false,
    default_value character varying COLLATE pg_catalog."default",
    allowed_values jsonb,
    description character varying COLLATE pg_catalog."default",
    display_order integer,
    active boolean NOT NULL DEFAULT true,
    cr_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    up_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id character varying COLLATE pg_catalog."default",
    displayable boolean NOT NULL DEFAULT true,
    generation_type character varying COLLATE pg_catalog."default",
    CONSTRAINT aua_api_fields_pkey PRIMARY KEY (id),
    CONSTRAINT uk_aua_api_field UNIQUE (message_id, field_code),
    CONSTRAINT fk_aua_api_field_message FOREIGN KEY (message_id)
        REFERENCES schm_sp.aua_api_messages (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS schm_sp.aua_api_fields
    OWNER to postgres;



-- Table: schm_sp.aua_api_field_mappings

-- DROP TABLE IF EXISTS schm_sp.aua_api_field_mappings;

CREATE TABLE IF NOT EXISTS schm_sp.aua_api_field_mappings
(
    id bigserial,
    api_definition_id bigint NOT NULL,
    api_field_id bigint NOT NULL,
    source_type character varying COLLATE pg_catalog."default" NOT NULL,
    source_path character varying COLLATE pg_catalog."default",
    transformation character varying COLLATE pg_catalog."default",
    default_value character varying COLLATE pg_catalog."default",
    cr_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    up_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id character varying COLLATE pg_catalog."default",
    response_attribute_type character varying COLLATE pg_catalog."default",
    desired_response character varying COLLATE pg_catalog."default",
    service_id bigint,
    cr_by bigint,
    provider_id bigint,
    CONSTRAINT aua_api_field_mappings_pkey PRIMARY KEY (id),
    CONSTRAINT uk_aua_api_mapping UNIQUE (api_definition_id, api_field_id, service_id),
    CONSTRAINT fk_aua_mapping_definition FOREIGN KEY (api_definition_id)
        REFERENCES schm_sp.aua_api_definitions (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT fk_aua_mapping_field FOREIGN KEY (api_field_id)
        REFERENCES schm_sp.aua_api_fields (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT fk_aua_mapping_service FOREIGN KEY (service_id)
        REFERENCES schm_sp.service_definition (service_id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
)

TABLESPACE pg_default;

ALTER TABLE IF EXISTS schm_sp.aua_api_field_mappings
    OWNER to postgres;