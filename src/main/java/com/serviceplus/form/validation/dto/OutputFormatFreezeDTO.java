package com.serviceplus.form.validation.dto;

import java.util.List;

public class OutputFormatFreezeDTO {

    private Long id;

    private Integer layout;

    private String pageSize;

    private List<String> usedAttr;

    private String templateName;

    private String documentType;

    private String templateBodySrc;

    private String waterMark;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Integer getLayout() {
		return layout;
	}

	public void setLayout(Integer layout) {
		this.layout = layout;
	}

	public String getPageSize() {
		return pageSize;
	}

	public void setPageSize(String pageSize) {
		this.pageSize = pageSize;
	}

	public List<String> getUsedAttr() {
		return usedAttr;
	}

	public void setUsedAttr(List<String> usedAttr) {
		this.usedAttr = usedAttr;
	}

	public String getTemplateName() {
		return templateName;
	}

	public void setTemplateName(String templateName) {
		this.templateName = templateName;
	}

	public String getDocumentType() {
		return documentType;
	}

	public void setDocumentType(String documentType) {
		this.documentType = documentType;
	}

	public String getTemplateBodySrc() {
		return templateBodySrc;
	}

	public void setTemplateBodySrc(String templateBodySrc) {
		this.templateBodySrc = templateBodySrc;
	}

	public String getWaterMark() {
		return waterMark;
	}

	public void setWaterMark(String waterMark) {
		this.waterMark = waterMark;
	}
}
