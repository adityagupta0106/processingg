package com.serviceplus.form.validation.DocumentGeneration;

import com.serviceplus.form.validation.dto.DocumentGenerationDetails;
import com.serviceplus.form.validation.dto.DocumentMergeResult;
import com.serviceplus.form.validation.dto.DocumentSectionResponse;
import com.serviceplus.form.validation.dto.ResolvedDocument;
import com.serviceplus.form.validation.enums.DocumentMergeMode;
import com.serviceplus.form.validation.enums.DocumentMode;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DocumentMergeProcessor {

    public DocumentMergeResult process(DocumentGenerationDetails.DocMappingDTO mapping, List<ResolvedDocument> resolvedDocuments) {

        List<ResolvedDocument> orderedDocuments = orderDocuments(resolvedDocuments, mapping.getMergeOrder());

        if (DocumentMergeMode.MERGE_ALL.getValue().equalsIgnoreCase(mapping.getMergeMode())) {
            return new DocumentMergeResult(DocumentMergeMode.MERGE_ALL, orderedDocuments);
        }

        if (DocumentMergeMode.OVERWRITE.getValue().equalsIgnoreCase(mapping.getMergeMode())) {
            List<ResolvedDocument> firstDocument = orderedDocuments.isEmpty() ? Collections.emptyList() : List.of(orderedDocuments.getFirst());
            return new DocumentMergeResult(DocumentMergeMode.OVERWRITE, firstDocument);
        }

        return new DocumentMergeResult(DocumentMergeMode.NONE, orderedDocuments);
    }

    private List<ResolvedDocument> orderDocuments(List<ResolvedDocument> documents, List<DocumentGenerationDetails.DocMappingDTO.MergeOrderDTO> mergeOrder) {

        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }

        if (mergeOrder == null || mergeOrder.isEmpty()) {
            return documents;
        }

        Map<String, Integer> orderMap =
                mergeOrder.stream()
                        .filter(order -> order.getId() != null)
                        .collect(Collectors.toMap(
                                DocumentGenerationDetails.DocMappingDTO.MergeOrderDTO::getId,
                                order -> order.getSortOrder() == null
                                        ? Integer.MAX_VALUE
                                        : order.getSortOrder(),
                                (a, b) -> a
                        ));

        return documents.stream()
                .sorted(Comparator.comparingInt(document ->
                        orderMap.getOrDefault(
                                document.getSourceType(),
                                Integer.MAX_VALUE)))
                .toList();
    }
}