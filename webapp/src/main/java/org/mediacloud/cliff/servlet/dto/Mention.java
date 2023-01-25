package org.mediacloud.cliff.servlet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class Mention {
    private String countryCode;
    private String stateCode;
    private String county;
    private String city;
    private String place;
    private Double lat;
    private Double lon;
    private int id;

    private MentionSource mentionSource;

    private String featureCode;
    private String featureClass;


    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class MentionSource {
        private int charIndex;
        private String string;
    }
}
