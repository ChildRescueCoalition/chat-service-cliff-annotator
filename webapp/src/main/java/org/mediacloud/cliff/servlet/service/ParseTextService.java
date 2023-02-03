package org.mediacloud.cliff.servlet.service;

import com.crc.commons.dto.cliff.Mention;
import com.crc.commons.dto.cliff.request.BatchParseTextRequest;
import com.crc.commons.dto.cliff.response.BatchCondensedParseTextResponse;
import com.crc.commons.dto.cliff.response.BatchRawParseTextResponse;
import com.crc.commons.dto.cliff.response.ParseTextResponse;
import com.crc.commons.dto.cliff.response.RawParseTextResponse;
import org.apache.commons.lang.time.StopWatch;
import org.apache.commons.lang3.tuple.Pair;
import org.mediacloud.cliff.ParseManager;
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

                    return new RawParseTextResponse(parseTextRequest.getExternalId(), result);
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
        return new BatchRawParseTextResponse(responses, stopWatch.getTime(), milliseconds);
    }

    private static Map<Pair<String, String>, Long> toCountryStateCountMap(Collection<Mention> mentions) {
        return mentions.stream()
                .map(m -> Pair.of(m.getCountryCode(), m.getStateCode()))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private static Map<String, Long> toCountryMap(Collection<Mention> mentions) {
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
                .map(mention -> {
                    if (COUNTY_FEATURE_CODE.equals(mention.getFeatureCode())) {
                        return updateCounty(mention, mention.getPlace());
                    }
                    return mention;
                })
                // "places" need the county field populated.
                .map(mention -> {
                    if (!POPULATED_AREAS_CLASS.equals(mention.getFeatureClass())) {
                        Optional<Mention> optMention = getCountyName(mention).map(county -> updateCounty(mention, county));
                        if (optMention.isPresent()) {
                            return optMention.get();
                        }
                    }
                    return mention;
                })
                .collect(Collectors.toList());

        return new ParseTextResponse(parseTextResponse.getExternalId(), condensedMentions);
    }

    private static Mention updateCounty(Mention mention, String county) {
        return mention.copy(
                mention.getCountryCode(),
                mention.getStateCode(),
                county,
                mention.getPlace(),
                mention.getLat(),
                mention.getLon(),
                mention.getId(),
                mention.getMentionSource(),
                mention.getFeatureCode(),
                mention.getFeatureClass()
        );
    }

    public static BatchCondensedParseTextResponse parseTextInBatchesCompressed(
            BatchParseTextRequest batchParseTextRequest, boolean manuallyReplaceDemonyms) {
        BatchRawParseTextResponse response = parseTextInBatches(batchParseTextRequest, manuallyReplaceDemonyms);

        Collection<ParseTextResponse> results = response.getResults().stream()
                .map(ParseTextMapper::toCondensed)
                .map(ParseTextService::collapseMentions)
                .filter(parseTextResponse -> !parseTextResponse.getMentions().isEmpty())
                .collect(Collectors.toList());

        return new BatchCondensedParseTextResponse(results, null, null);
    }

}
