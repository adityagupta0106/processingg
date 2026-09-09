package com.serviceplus.form.validation.dto;

import java.util.List;


public class WebServiceDetails {

    private WsCall wsCall;

    private List<Entities> entities;

    private Definition definition;

    private LabelValue wsSelected;

    private Boolean unsubscribe;

    private List<LabelValue> applicationStatus;
    
    private FormDetail formDetail;
    	    
    public WsCall getWsCall() {
		return wsCall;
	}
	public void setWsCall(WsCall wsCall) {
		this.wsCall = wsCall;
	}
	public List<Entities> getEntities() {
		return entities;
	}
	public void setEntities(List<Entities> entities) {
		this.entities = entities;
	}
	public Definition getDefinition() {
		return definition;
	}
	public void setDefinition(Definition definition) {
		this.definition = definition;
	}
	public LabelValue getWsSelected() {
		return wsSelected;
	}
	public void setWsSelected(LabelValue wsSelected) {
		this.wsSelected = wsSelected;
	}
	public Boolean getUnsubscribe() {
		return unsubscribe;
	}
	public void setUnsubscribe(Boolean unsubscribe) {
		this.unsubscribe = unsubscribe;
	}
	public List<LabelValue> getApplicationStatus() {
		return applicationStatus;
	}
	public void setApplicationStatus(List<LabelValue> applicationStatus) {
		this.applicationStatus = applicationStatus;
	}
	
	public FormDetail getFormDetail() {
		return formDetail;
	}
	public void setFormDetail(FormDetail formDetail) {
		this.formDetail = formDetail;
	}

	public static class FormDetail {
		private String formId;
		private String holderId;
		private Boolean isSubscribed;
		private String subscriptionMode;
		private String parentForm;
		private String templateName;
		private Boolean defaultFormEncryptionRequired;
		private Boolean encryptDataInTransit;
		private Boolean encryptDataAtRest;
		private Boolean standalone;

		public String getFormId() {
			return formId;
		}

		public void setFormId(String formId) {
			this.formId = formId;
		}

		public String getHolderId() {
			return holderId;
		}

		public void setHolderId(String holderId) {
			this.holderId = holderId;
		}

		public Boolean getIsSubscribed() {
			return isSubscribed;
		}

		public void setIsSubscribed(Boolean isSubscribed) {
			this.isSubscribed = isSubscribed;
		}

		public String getSubscriptionMode() {
			return subscriptionMode;
		}

		public void setSubscriptionMode(String subscriptionMode) {
			this.subscriptionMode = subscriptionMode;
		}

		public String getParentForm() {
			return parentForm;
		}

		public void setParentForm(String parentForm) {
			this.parentForm = parentForm;
		}

		public String getTemplateName() {
			return templateName;
		}

		public void setTemplateName(String templateName) {
			this.templateName = templateName;
		}

		public Boolean getDefaultFormEncryptionRequired() {
			return defaultFormEncryptionRequired;
		}

		public void setDefaultFormEncryptionRequired(Boolean defaultFormEncryptionRequired) {
			this.defaultFormEncryptionRequired = defaultFormEncryptionRequired;
		}

		public Boolean getEncryptDataInTransit() {
			return encryptDataInTransit;
		}

		public void setEncryptDataInTransit(Boolean encryptDataInTransit) {
			this.encryptDataInTransit = encryptDataInTransit;
		}

		public Boolean getEncryptDataAtRest() {
			return encryptDataAtRest;
		}

		public void setEncryptDataAtRest(Boolean encryptDataAtRest) {
			this.encryptDataAtRest = encryptDataAtRest;
		}

		public Boolean getStandalone() {
			return standalone;
		}

		public void setStandalone(Boolean standalone) {
			this.standalone = standalone;
		}
	}
	public static class WsCall {

        private String wsCallInterval;

        private String wsCallFrequency;

        private LabelValue wsCallIntervalUnit;

		public String getWsCallInterval() {
			return wsCallInterval;
		}

		public void setWsCallInterval(String wsCallInterval) {
			this.wsCallInterval = wsCallInterval;
		}

		public String getWsCallFrequency() {
			return wsCallFrequency;
		}

		public void setWsCallFrequency(String wsCallFrequency) {
			this.wsCallFrequency = wsCallFrequency;
		}

		public LabelValue getWsCallIntervalUnit() {
			return wsCallIntervalUnit;
		}

		public void setWsCallIntervalUnit(LabelValue wsCallIntervalUnit) {
			this.wsCallIntervalUnit = wsCallIntervalUnit;
		}
        
    }
    public static class Definition {

        private String id;

        private String templateName;

        private String description;

        private String endpoint;

        private String method;

        private String callType;

        private Boolean storeResponse;

        private Boolean serverValidation;

        private Body body;

        private List<Header> headers;

        private List<Param> params;

        private Authorization authorization;

        private List<DataMapping> dataMapping;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getTemplateName() {
			return templateName;
		}

		public void setTemplateName(String templateName) {
			this.templateName = templateName;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		public String getEndpoint() {
			return endpoint;
		}

		public void setEndpoint(String endpoint) {
			this.endpoint = endpoint;
		}

		public String getMethod() {
			return method;
		}

		public void setMethod(String method) {
			this.method = method;
		}

		public String getCallType() {
			return callType;
		}

		public void setCallType(String callType) {
			this.callType = callType;
		}

		public Boolean getStoreResponse() {
			return storeResponse;
		}

		public void setStoreResponse(Boolean storeResponse) {
			this.storeResponse = storeResponse;
		}

		public Boolean getServerValidation() {
			return serverValidation;
		}

		public void setServerValidation(Boolean serverValidation) {
			this.serverValidation = serverValidation;
		}

		public Body getBody() {
			return body;
		}

		public void setBody(Body body) {
			this.body = body;
		}

		public List<Header> getHeaders() {
			return headers;
		}

		public void setHeaders(List<Header> headers) {
			this.headers = headers;
		}

		public List<Param> getParams() {
			return params;
		}

		public void setParams(List<Param> params) {
			this.params = params;
		}

		public Authorization getAuthorization() {
			return authorization;
		}

		public void setAuthorization(Authorization authorization) {
			this.authorization = authorization;
		}

		public List<DataMapping> getDataMapping() {
			return dataMapping;
		}

		public void setDataMapping(List<DataMapping> dataMapping) {
			this.dataMapping = dataMapping;
		}
        
    }
    
    public static class Header {
        private String label;
        private String value;
        private String fieldValue;
		public String getLabel() {
			return label;
		}
		public void setLabel(String label) {
			this.label = label;
		}
		public String getValue() {
			return value;
		}
		public void setValue(String value) {
			this.value = value;
		}
		public String getFieldValue() {
			return fieldValue;
		}
		public void setFieldValue(String fieldValue) {
			this.fieldValue = fieldValue;
		}
    }
    public static class Param {
        private String label;
        private String value;
        private String fieldValue;
		public String getLabel() {
			return label;
		}
		public void setLabel(String label) {
			this.label = label;
		}
		public String getValue() {
			return value;
		}
		public void setValue(String value) {
			this.value = value;
		}
		public String getFieldValue() {
			return fieldValue;
		}
		public void setFieldValue(String fieldValue) {
			this.fieldValue = fieldValue;
		}
    }
    
    public static class Body {

        private String type;
        private String raw;
        private String rawFormat;

        private List<FormData> formData;
        private List<UrlEncoded> urlEncoded;
        private List<FormData> rawJson;
		public String getType() {
			return type;
		}
		public void setType(String type) {
			this.type = type;
		}
		public String getRaw() {
			return raw;
		}
		public void setRaw(String raw) {
			this.raw = raw;
		}
		public String getRawFormat() {
			return rawFormat;
		}
		public void setRawFormat(String rawFormat) {
			this.rawFormat = rawFormat;
		}
		public List<FormData> getFormData() {
			return formData;
		}
		public void setFormData(List<FormData> formData) {
			this.formData = formData;
		}
		public List<UrlEncoded> getUrlEncoded() {
			return urlEncoded;
		}
		public void setUrlEncoded(List<UrlEncoded> urlEncoded) {
			this.urlEncoded = urlEncoded;
		}
		public List<FormData> getRawJson() {
			return rawJson;
		}
		public void setRawJson(List<FormData> rawJson) {
			this.rawJson = rawJson;
		}
    }

    public static class FormData {
        private String label;
        private String value;
        private String fieldValue;
		public String getLabel() {
			return label;
		}
		public void setLabel(String label) {
			this.label = label;
		}
		public String getValue() {
			return value;
		}
		public void setValue(String value) {
			this.value = value;
		}
		public String getFieldValue() {
			return fieldValue;
		}
		public void setFieldValue(String fieldValue) {
			this.fieldValue = fieldValue;
		}
    }

    public static class UrlEncoded {
        private String label;
        private String value;
        private String fieldValue;
		public String getLabel() {
			return label;
		}
		public void setLabel(String label) {
			this.label = label;
		}
		public String getValue() {
			return value;
		}
		public void setValue(String value) {
			this.value = value;
		}
		public String getFieldValue() {
			return fieldValue;
		}
		public void setFieldValue(String fieldValue) {
			this.fieldValue = fieldValue;
		}
    }
    
    public static class Authorization {

        private String type;

        private ApiKey apiKey;
        private Bearer bearer;
        private Basic basic;
		public String getType() {
			return type;
		}
		public void setType(String type) {
			this.type = type;
		}
		public ApiKey getApiKey() {
			return apiKey;
		}
		public void setApiKey(ApiKey apiKey) {
			this.apiKey = apiKey;
		}
		public Bearer getBearer() {
			return bearer;
		}
		public void setBearer(Bearer bearer) {
			this.bearer = bearer;
		}
		public Basic getBasic() {
			return basic;
		}
		public void setBasic(Basic basic) {
			this.basic = basic;
		}
        
    }

    
    public static class ApiKey {
        private String key;
        private String value;
        private String addTo;
		public String getKey() {
			return key;
		}
		public void setKey(String key) {
			this.key = key;
		}
		public String getValue() {
			return value;
		}
		public void setValue(String value) {
			this.value = value;
		}
		public String getAddTo() {
			return addTo;
		}
		public void setAddTo(String addTo) {
			this.addTo = addTo;
		}
        
    }

    
    public static class Bearer {
        private String token;
		public String getToken() {
			return token;
		}
		public void setToken(String token) {
			this.token = token;
		}
    }
    public static class Basic {
        private String username;
        private String password;
		public String getUsername() {
			return username;
		}
		public void setUsername(String username) {
			this.username = username;
		}
		public String getPassword() {
			return password;
		}
		public void setPassword(String password) {
			this.password = password;
		}
        
    }
    public static class DataMapping {

        private String targetType;
        private String label;
        private String dataType;
        private String explicitKeys;
        private String valueFormat;
        private String labelName;
        private String mapWith;
		public String getTargetType() {
			return targetType;
		}
		public void setTargetType(String targetType) {
			this.targetType = targetType;
		}
		public String getLabel() {
			return label;
		}
		public void setLabel(String label) {
			this.label = label;
		}
		public String getDataType() {
			return dataType;
		}
		public void setDataType(String dataType) {
			this.dataType = dataType;
		}
		public String getExplicitKeys() {
			return explicitKeys;
		}
		public void setExplicitKeys(String explicitKeys) {
			this.explicitKeys = explicitKeys;
		}
		public String getValueFormat() {
			return valueFormat;
		}
		public void setValueFormat(String valueFormat) {
			this.valueFormat = valueFormat;
		}
		public String getLabelName() {
			return labelName;
		}
		public void setLabelName(String labelName) {
			this.labelName = labelName;
		}
		public String getMapWith() {
			return mapWith;
		}
		public void setMapWith(String mapWith) {
			this.mapWith = mapWith;
		}
    }
    
    public static class Entities {
		private String type;
		private LabelValue entity;
		private LabelValue category;
		private LabelValue clcDetail;
		private LabelValue entityLevel;
		private List<Designation> designations;
		
		public String getType() {
			return type;
		}
		public void setType(String type) {
			this.type = type;
		}
		public LabelValue getEntity() {
			return entity;
		}
		public void setEntity(LabelValue entity) {
			this.entity = entity;
		}
		public LabelValue getClcDetail() {
			return clcDetail;
		}
		public void setClcDetail(LabelValue clcDetail) {
			this.clcDetail = clcDetail;
		}
		public LabelValue getEntityLevel() {
			return entityLevel;
		}
		public void setEntityLevel(LabelValue entityLevel) {
			this.entityLevel = entityLevel;
		}
		public LabelValue getCategory() {
			return category;
		}
		public void setCategory(LabelValue category) {
			this.category = category;
		}
		public List<Designation> getDesignations() {
			return designations;
		}
		public void setDesignations(List<Designation> designations) {
			this.designations = designations;
		}
		
	}

	public static class Designation {
		private LabelValue designation;

		public LabelValue getDesignation() {
			return designation;
		}

		public void setDesignation(LabelValue designation) {
			this.designation = designation;
		}
	}
   
}
