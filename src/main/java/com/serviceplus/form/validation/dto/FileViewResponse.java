package com.serviceplus.form.validation.dto;

import java.util.List;
import java.util.Map;

public class FileViewResponse {

    private Map<String, Data> results;

    public Map<String, Data> getResults() {
        return results;
    }

    public void setResults(Map<String, Data> results) {
        this.results = results;
    }

    public static class Data {
        private List<TypedSignedUrl> data;

        public List<TypedSignedUrl> getData() {
            return data;
        }

        public void setData(List<TypedSignedUrl> data) {
            this.data = data;
        }

        public static class TypedSignedUrl {

            private String type;
            private SignedUrlResponse data;

            public static class SignedUrlResponse {

                private String url;
                private Long expiryInSecond;
                private String uploadId;

                public SignedUrlResponse(String url, long expiry,String uploadId) {
                    this.url = url;
                    this.expiryInSecond = expiry;
                    this.uploadId = uploadId;
                }

                public String getUrl() {
                    return url;
                }

                public void setUrl(String url) {
                    this.url = url;
                }

                public Long getExpiryInSecond() {
                    return expiryInSecond;
                }

                public void setExpiryInSecond(Long expiryInSecond) {
                    this.expiryInSecond = expiryInSecond;
                }

                public String getUploadId() {
                    return uploadId;
                }

                public void setUploadId(String uploadId) {
                    this.uploadId = uploadId;
                }
            }

            public TypedSignedUrl(String type, SignedUrlResponse data) {
                this.type = type;
                this.data = data;
            }

            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type;
            }

            public SignedUrlResponse getData() {
                return data;
            }

            public void setData(SignedUrlResponse data) {
                this.data = data;
            }
        }

    }


}
