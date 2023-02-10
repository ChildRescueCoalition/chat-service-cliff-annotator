package org.mediacloud.cliff.servlet.mappers;

import com.crc.commons.dto.cliff.Mention;
import com.crc.commons.dto.cliff.request.BatchParseTextRequest;
import com.crc.commons.dto.cliff.request.ParseTextRequest;
import com.crc.commons.dto.cliff.response.BatchCondensedParseTextResponse;
import com.crc.commons.dto.cliff.response.ParseTextResponse;
import com.crc.commons.dto.cliff.response.RawParseTextResponse;
import com.crc.commons.dto.singlestore.SingleStoreExternalFunctionRequest;
import com.crc.commons.dto.singlestore.SingleStoreExternalFunctionResponse;
import org.mediacloud.cliff.extractor.EntityExtractor;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ParseTextMapper {
    private static final int REQUEST_ROW_ID_IDX = 0;
    private static final int REQUEST_MESSAGE_TEXT_IDX = 1;
    private static final int REQUEST_LANGUAGE_TEXT_IDX = 2;


    public static Mention fromMap(HashMap<?, ?> mentionMap) {
        HashMap<?, ?> sourceMap = (HashMap<?, ?>) mentionMap.get("source");
        return new Mention(
                (String) mentionMap.get("countryCode"),
                (String) mentionMap.get("stateCode"),
                null,
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

    private static String getLanguage(List<Object> row) {
        if (row.size() > REQUEST_LANGUAGE_TEXT_IDX) {
            return (String) row.get(REQUEST_LANGUAGE_TEXT_IDX);
        }
        return EntityExtractor.ENGLISH;
    }


    public static BatchParseTextRequest toRegularBatchRequest(SingleStoreExternalFunctionRequest singleStoreRequest, int numOfThreads) {
        Collection<ParseTextRequest> requests = singleStoreRequest.getData().stream()
                .map(row ->
                        new ParseTextRequest(Integer.toString(((Double) row.get(REQUEST_ROW_ID_IDX)).intValue()), (String) row.get(REQUEST_MESSAGE_TEXT_IDX), getLanguage(row)))
                .collect(Collectors.toList());
        return new BatchParseTextRequest(requests, numOfThreads);
    }

    private static Stream<List<Object>> toRow(ParseTextResponse response) {
        return response.getMentions().stream()
                // TODO: Add city when implemented
                .map(row -> Stream.of(
                        Integer.parseInt(response.getExternalId()),
                        row.getCountryCode(),
                        row.getStateCode(),
                        row.getCounty(),
                        row.getCity(),
                        row.getPlace(),
                        row.getLat(),
                        row.getLon(),
                        row.getFeatureCode(),
                        row.getFeatureClass(),
                        row.getMentionSource().getString(),
                        row.getMentionSource().getCharIndex(),
                        row.getId()
                ).collect(Collectors.toList()));
    }

    public static SingleStoreExternalFunctionResponse toSingleStoreResponse(BatchCondensedParseTextResponse response) {
        return new SingleStoreExternalFunctionResponse(response.getResults().stream()
                .flatMap(ParseTextMapper::toRow)
                .collect(Collectors.toList()));
    }
}
