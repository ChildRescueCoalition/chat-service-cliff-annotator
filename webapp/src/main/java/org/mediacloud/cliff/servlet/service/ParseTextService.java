package org.mediacloud.cliff.servlet.service;

import com.crc.commons.dto.cliff.Mention;
import com.crc.commons.dto.cliff.request.BatchParseTextRequest;
import com.crc.commons.dto.cliff.response.BatchCondensedParseTextResponse;
import com.crc.commons.dto.cliff.response.BatchRawParseTextResponse;
import com.crc.commons.dto.cliff.response.ParseTextResponse;
import com.crc.commons.dto.cliff.response.RawParseTextResponse;
import com.crc.commons.dto.singlestore.SingleStoreExternalFunctionRequest;
import com.crc.commons.dto.singlestore.SingleStoreExternalFunctionResponse;
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

    public static BatchRawParseTextResponse parseTextInBatches(BatchParseTextRequest batchRequest, boolean manuallyReplaceDemonyms) {
        ExecutorService executorService = Executors.newFixedThreadPool(batchRequest.getNumOfThreads());
        logger.info("Running batch of {} elements using {} threads", batchRequest.getRequests().size(), batchRequest.getNumOfThreads());
        logger.debug("Request: {}", batchRequest);


        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Collection<CompletableFuture<RawParseTextResponse>> futuresList = batchRequest.getRequests().stream()
                .filter(parseTextRequest -> !parseTextRequest.getText().isBlank())
                .map(parseTextRequest -> CompletableFuture.supplyAsync(() -> {
                    HashMap<?, ?> result;
                    logger.debug("Running in thread {}", Thread.currentThread().getName());
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

    private static Optional<Pair<String, String>> getCityAndCountyName(Mention mention) {
        HashMap<?, ?> geonameInfo = (HashMap<?, ?>) ParseManager.getGeoNameInfo(mention.getId()).get("results");

        String city = null;
        while (geonameInfo.containsKey("parent")) {
            if (COUNTY_FEATURE_CODE.equals(geonameInfo.get("featureCode"))) {
                return Optional.of(Pair.of(city, (String) geonameInfo.get("name")));
            }

            HashMap<?, ?> geonameInfoParent = (HashMap<?, ?>) geonameInfo.get("parent");
            if (COUNTY_FEATURE_CODE.equals(geonameInfoParent.get("featureCode"))) {
               city = (String) geonameInfo.get("name");
            }
            geonameInfo = geonameInfoParent;
        }

        return Optional.empty();
    }

    private static ParseTextResponse collapseMentions(ParseTextResponse parseTextResponse) {
        Collection<Mention> withoutEmptyCountryCodes = parseTextResponse.getMentions().stream()
                .filter(m -> !m.getCountryCode().isEmpty())
                .collect(Collectors.toList());
        Map<Pair<String, String>, Long> countryStateCountMap = toCountryStateCountMap(withoutEmptyCountryCodes);
        Map<String, Long> countryCountMap = toCountryMap(withoutEmptyCountryCodes);

        // Getting most specific place grouping by country and state
        List<Mention> condensedMentions = withoutEmptyCountryCodes.stream()
                // Getting most specific place grouping by country and state
                .filter(mention -> {
                    if (COUNTRY_FEATURE_CODE.equals(mention.getFeatureCode()) && !countryCountMap.get(mention.getCountryCode()).equals(1L)) {
                        return false;
                    }

                    Pair<String, String> countryStateKey = Pair.of(mention.getCountryCode(), mention.getStateCode());
                    return !STATE_FEATURE_CODE.equals(mention.getFeatureCode()) || countryStateCountMap.get(countryStateKey).equals(1L);
                })
                // Add the city and county where suited
                .map(mention -> getCityAndCountyName(mention).map(p -> updateCountyAndCity(mention, p.getRight(), p.getLeft()))
                        .orElse(mention))
                .collect(Collectors.toList());

        return new ParseTextResponse(parseTextResponse.getExternalId(), condensedMentions);
    }

    private static Mention updateCountyAndCity(Mention mention, String county, String city) {
        return mention.copy(
                mention.getCountryCode(),
                mention.getStateCode(),
                county,
                city,
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

    public static SingleStoreExternalFunctionResponse parseTextInBatchesForSingleStore(
            SingleStoreExternalFunctionRequest request, int numOfThreads, boolean manuallyReplaceDemonyms
    ) {
        BatchCondensedParseTextResponse response = parseTextInBatchesCompressed(
                ParseTextMapper.toRegularBatchRequest(request, numOfThreads), manuallyReplaceDemonyms);

        logger.debug("Response: {}", ParseTextMapper.toSingleStoreResponse(response));
        return ParseTextMapper.toSingleStoreResponse(response);
    }
}
