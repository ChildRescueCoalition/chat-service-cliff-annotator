package org.mediacloud.cliff.servlet;

import com.google.gson.Gson;
import org.apache.commons.lang.time.StopWatch;
import org.mediacloud.cliff.ParseManager;
import org.mediacloud.cliff.servlet.dto.request.BatchParseTextRequest;
import org.mediacloud.cliff.servlet.dto.response.BatchParseTextResponse;
import org.mediacloud.cliff.servlet.dto.response.ParseTextResponse;
import org.mediacloud.cliff.servlet.utils.RequestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Wrapsthe CLAVIN geoparser behind some ports so we can integrate it into other workflows.
 *
 * @author rahulb
 */
public class BatchParseTextServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(BatchParseTextServlet.class);

    private static Gson gson = new Gson();

    public BatchParseTextServlet() {
    }


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean manuallyReplaceDemonyms = RequestUtils.getReplaceAllDemonyms(request);

        response.setContentType("application/json");

        BatchParseTextRequest batchRequest = gson.fromJson(request.getReader(), BatchParseTextRequest.class);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<BatchParseTextRequest>> violations = validator.validate(batchRequest);
            if (!violations.isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, violations.toString());
                response.getWriter().write(violations.toString());
                return;
            }
        }

        ExecutorService executorService = Executors.newFixedThreadPool(batchRequest.getNumOfThreads());
        logger.info("Running batch of {} elements using {} threads", batchRequest.getRequests().size(), batchRequest.getNumOfThreads());


        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Collection<CompletableFuture<ParseTextResponse>> futuresList = batchRequest.getRequests().stream()
                .map(parseTextRequest -> CompletableFuture.supplyAsync(() -> {
                    HashMap result;
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

                    return ParseTextResponse.builder()
                            .externalId(parseTextRequest.getExternalId())
                            .result(result)
                            .build();
                }, executorService))
                .collect(Collectors.toList());

        CompletableFuture[] futuresArray = futuresList.toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futuresArray).join();
        stopWatch.stop();

        Collection<ParseTextResponse> responses = futuresList.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
        Long milliseconds = responses.stream()
                .map(a -> (Long) a.getResult().get("milliseconds"))
                .map(a -> a == null ? 0 : a)
                .reduce(Long::sum)
                .orElse(0L);
        executorService.shutdown();
        response.getWriter().write(gson.toJson(BatchParseTextResponse.builder()
                .results(responses)
                .actualMilliseconds(stopWatch.getTime())
                .sumOfMilliseconds(milliseconds)));
    }
}
