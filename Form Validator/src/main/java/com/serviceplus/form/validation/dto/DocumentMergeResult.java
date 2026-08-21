package com.serviceplus.form.validation.dto;

import com.serviceplus.form.validation.enums.DocumentMergeMode;

import java.util.List;

public record DocumentMergeResult(DocumentMergeMode mode, List<ResolvedDocument> documents) {

}
