package org.mediacloud.cliff.servlet.mappers;

import com.crc.commons.dto.cliff.Mention;
import com.crc.commons.dto.cliff.response.ParseTextResponse;
import com.crc.commons.dto.cliff.response.RawParseTextResponse;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class ParseTextMapper {
    public static Mention fromMap(HashMap<?, ?> mentionMap) {
        HashMap<?, ?> sourceMap = (HashMap<?, ?>) mentionMap.get("source");
        return new Mention(
                (String) mentionMap.get("countryCode"),
                (String) mentionMap.get("stateCode"),
                null,
                (String) mentionMap.get("name"),
                (Double) mentionMap.get("lat"),
                (Double) mentionMap.get("lon"),
                (int) mentionMap.get("id"),
                new Mention.MentionSource(
                        (int) sourceMap.get("charIndex"),
                        (String) sourceMap.get("string")
                ),
                (String) mentionMap.get("featureCode"),
                (String) mentionMap.get("featureClass")
        );
    }

    public static ParseTextResponse toCondensed(RawParseTextResponse rawParseTextResponse) {
        HashMap<?, ?> results = (HashMap<?, ?>) rawParseTextResponse.getResult().get("results");
        HashMap<?, ?> places = (HashMap<?, ?>) results.get("places");
        List<HashMap<?, ?>> mentions = (List<HashMap<?, ?>>) places.get("mentions");
        return new ParseTextResponse(
                rawParseTextResponse.getExternalId(),
                mentions.stream().map(ParseTextMapper::fromMap).collect(Collectors.toList())
        );
    }
}
