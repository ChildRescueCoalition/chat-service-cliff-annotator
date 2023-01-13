package org.mediacloud.cliff.servlet.service;

import org.apache.commons.lang.time.StopWatch;
import org.apache.commons.lang3.tuple.Pair;
import org.mediacloud.cliff.ParseManager;
import org.mediacloud.cliff.servlet.dto.Mention;
import org.mediacloud.cliff.servlet.dto.request.BatchParseTextRequest;
import org.mediacloud.cliff.servlet.dto.response.BatchCondensedParseTextResponse;
import org.mediacloud.cliff.servlet.dto.response.BatchRawParseTextResponse;
import org.mediacloud.cliff.servlet.dto.response.ParseTextResponse;
import org.mediacloud.cliff.servlet.dto.response.RawParseTextResponse;
import org.mediacloud.cliff.servlet.mappers.ParseTextMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ParseTextService {
    private static final Logger logger = LoggerFactory.getLogger(ParseTextService.class);

    public static final String COUNTY_FEATURE_CODE = "ADM2";
    public static final String STATE_FEATURE_CODE = "ADM1";
    public static final String COUNTRY_FEATURE_CODE = "PCLI";
    public static final String POPULATED_AREAS_CLASS = "P";

    public static BatchRawParseTextResponse parseTextInBatches(BatchParseTextRequest batchRequest, boolean manuallyReplaceDemonyms) {
        ExecutorService executorService = Executors.newFixedThreadPool(batchRequest.getNumOfThreads());
        logger.info("Running batch of {} elements using {} threads", batchRequest.getRequests().size(), batchRequest.getNumOfThreads());


        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Collection<CompletableFuture<RawParseTextResponse>> futuresList = batchRequest.getRequests().stream()
                .map(parseTextRequest -> CompletableFuture.supplyAsync(() -> {
                    HashMap<?, ?> result;
//                    logger.info("Running in thread {}", Thread.currentThread().getName());
                    try {
                        result = ParseManager.parseFromText(parseTextRequest.getText(), manuallyReplaceDemonyms, parseTextRequest.getLanguage());
                    } catch (Exception e) {   // try to give the user something useful
                        logger.warn("Error parsing text of external id {}: {}",
                                parseTextRequest.getExternalId(), parseTextRequest.getText(), e);
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        e.printStackTrace(pw);
                        result = ParseManager.getErrorText(sw.toString());
                    }

                    return RawParseTextResponse.builder()
                            .externalId(parseTextRequest.getExternalId())
                            .result(result)
                            .build();
                }, executorService))
                .collect(Collectors.toList());

        CompletableFuture<?>[] futuresArray = futuresList.toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futuresArray).join();
        stopWatch.stop();

        Collection<RawParseTextResponse> responses = futuresList.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
        Long milliseconds = responses.stream()
                .map(a -> (Long) a.getResult().get("milliseconds"))
                .map(a -> a == null ? 0 : a)
                .reduce(Long::sum)
                .orElse(0L);
        executorService.shutdown();
        return BatchRawParseTextResponse.builder()
                .results(responses)
                .actualMilliseconds(stopWatch.getTime())
                .sumOfMilliseconds(milliseconds)
                .build();
    }

    private static Map<Pair<String, String>, Long> toCountryStateCountMap(List<Mention> mentions) {
        return mentions.stream()
                .map(m -> Pair.of(m.getCountryCode(), m.getStateCode()))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private static Map<String, Long> toCountryMap(List<Mention> mentions) {
        return mentions.stream()
                .map(Mention::getCountryCode)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private static Optional<String> getCountyName(Mention mention) {
        HashMap<?, ?> geonameInfo = (HashMap<?, ?>) ParseManager.getGeoNameInfo(mention.getId()).get("results");

        while (geonameInfo.containsKey("parent")) {
            if (COUNTY_FEATURE_CODE.equals(geonameInfo.get("featureCode"))) {
                return Optional.of((String) geonameInfo.get("name"));
            }
            geonameInfo = (HashMap<?, ?>) geonameInfo.get("parent");
        }
        
        return Optional.empty();
    }

    private static ParseTextResponse collapseMentions(ParseTextResponse parseTextResponse) {
        Map<Pair<String, String>, Long> countryStateCountMap = toCountryStateCountMap(parseTextResponse.getMentions());
        Map<String, Long> countryCountMap = toCountryMap(parseTextResponse.getMentions());

        // Getting most specific place grouping by country and state
        List<Mention> condensedMentions = parseTextResponse.getMentions().stream()
                // Getting most specific place grouping by country and state
                .filter(mention -> {
                    if (COUNTRY_FEATURE_CODE.equals(mention.getFeatureCode()) && !countryCountMap.get(mention.getCountryCode()).equals(1L)) {
                        return false;
                    }

                    Pair<String, String> countryStateKey = Pair.of(mention.getCountryCode(), mention.getStateCode());
                    return !STATE_FEATURE_CODE.equals(mention.getFeatureCode()) || countryStateCountMap.get(countryStateKey).equals(1L);
                })
                // counties need to have the "county" field populated
                .peek(mention -> {
                    if (COUNTY_FEATURE_CODE.equals(mention.getFeatureCode())) {
                        mention.setCounty(mention.getPlace());
                    }
                })
                // "places" need the county field populated.
                .peek(mention -> {
                    if (!POPULATED_AREAS_CLASS.equals(mention.getFeatureClass())) {
                        getCountyName(mention).ifPresent(mention::setCounty);
                    }
                })
                .collect(Collectors.toList());

        // Filling the missing spots for non-PPL places.

        parseTextResponse.setMentions(condensedMentions);

        return parseTextResponse;
    }

    public static BatchCondensedParseTextResponse parseTextInBatchesCompressed(
            BatchParseTextRequest batchParseTextRequest, boolean manuallyReplaceDemonyms) {
        BatchRawParseTextResponse response = parseTextInBatches(batchParseTextRequest, manuallyReplaceDemonyms);

        Collection<ParseTextResponse> results = response.getResults().stream()
                .map(ParseTextMapper::toCondensed)
                .map(ParseTextService::collapseMentions)
                .filter(parseTextResponse -> !parseTextResponse.getMentions().isEmpty())
                .collect(Collectors.toList());

        BatchCondensedParseTextResponse condensedResponse = new BatchCondensedParseTextResponse();
        condensedResponse.setResults(results);
        condensedResponse.setActualMilliseconds(null);
        condensedResponse.setSumOfMilliseconds(null);

        return condensedResponse;
    }

}
