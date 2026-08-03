package com.serviceplus.form.validation.utility;


import java.util.Map;

public class ApplicationConstants {

	public static final String USER_SESSION_DETAIL_HEADER = "USER-DETAILS";
	public static final String AUTH_HEADER = "Atk";
	public static final String SP_SCHEMA_NAME = "schm_sp";
    public static final String OFFICIAL_TASK_FLAG = "O";
    public static final String APPLICATION_SUBMISSION_TASK_FLAG = "A";
    public static final Integer FALLBACK_ACTION_NO = 9;
    public static final String SERVICE_ACTIVITY_REDIS_KEY_APPENDER = "SERVICE_ACTIVITY_";
    public static final String SERVICE_WORKFLOW_REDIS_KEY_APPENDER = "SERVICE_WORKFLOW_";
    public static final String TYPE_GATEWAY = "gateway";
    public static final String TYPE_TASK = "task";
    public static final String GATEWAY_BEHAVIOUR_EXCLUSIVE_DIVERGENT = "ED";
    public static final String GATEWAY_BEHAVIOUR_EXCLUSIVE_CONVERGENT = "EC";
    public static final String GATEWAY_BEHAVIOUR_INCLUSIVE_DIVERGENT = "ID";
    public static final String GATEWAY_BEHAVIOUR_INCLUSIVE_CONVERGENT = "IC";
    public static final String GATEWAY_BEHAVIOUR_PARALLEL_DIVERGENT = "PD";
    public static final String GATEWAY_BEHAVIOUR_PARALLEL_CONVERGENT = "PC";
    final public static String HOST_HEADER = "SP-Client-Domain";

    public static final Map<Integer, String> ACTION_CODE_MAPPING = Map.of(
            24, "I",
            8,"F",
            9, "F",
            12,"F",
            20,"F",
            22,"F",
            10,"R",
            11,"D"
    );

    public static final String APPLY_METADATA_AES_KEY  = "vN7$kP2!Qx9@Lm5R";
    public static final String APPLY_METADATA_HMAC_KEY = "cR8!Ty5@Wm2#Qs9X";
    public static final String ACTIVITY_FORM_STATUS_KEY = "FS";
    public static final String ACTIVITY_ENCLOSURE_STATUS_KEY = "ES";
    public static final String APPLICATION_STATUS_DRAFT = "S";
    public static final Integer ACTION_DELIVER = 11;
    public static final Integer ACTION_REJECT = 10;
    public static final String ACTIVITY_DOCUMENT_GENERATION = "DG";

}
