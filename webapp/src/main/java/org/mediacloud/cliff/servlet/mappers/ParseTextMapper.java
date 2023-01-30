package org.mediacloud.cliff.servlet.mappers;

import org.mediacloud.cliff.servlet.dto.Mention;
import org.mediacloud.cliff.servlet.dto.response.ParseTextResponse;
import org.mediacloud.cliff.servlet.dto.response.RawParseTextResponse;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class ParseTextMapper {
    public static Mention fromMap(HashMap<?, ?> mentionMap) {
        HashMap<?, ?> sourceMap = (HashMap<?, ?>) mentionMap.get("source");
        return Mention.builder()
                .featureCode((String) mentionMap.get("featureCode"))
                .featureClass((String) mentionMap.get("featureClass"))
                .lon((Double) mentionMap.get("lon"))
                .lat((Double) mentionMap.get("lat"))
                .countryCode((String) mentionMap.get("countryCode"))
                .place((String) mentionMap.get("name"))
                .mentionSource(Mention.MentionSource.builder()
                        .charIndex((int) sourceMap.get("charIndex"))
                        .string((String) sourceMap.get("string"))
                        .build())
                .stateCode((String) mentionMap.get("stateCode"))
                .id((int) mentionMap.get("id"))
                .build();
    }

    public static ParseTextResponse toCondensed(RawParseTextResponse rawParseTextResponse) {
        HashMap<?, ?> results = (HashMap<?, ?>) rawParseTextResponse.getResult().get("results");
        HashMap<?, ?> places = (HashMap<?, ?>) results.get("places");
        List<HashMap<?, ?>> mentions = (List<HashMap<?, ?>>) places.get("mentions");
        return ParseTextResponse.builder()
                .externalId(rawParseTextResponse.getExternalId())
                .mentions(mentions.stream().map(ParseTextMapper::fromMap).collect(Collectors.toList()))
                .build();
    }
}
